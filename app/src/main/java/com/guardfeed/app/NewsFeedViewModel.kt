package com.guardfeed.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.guardfeed.app.adapter.*
import com.guardfeed.app.data.GuardianItem
import com.guardfeed.app.data.GuardianMeta
import com.guardfeed.app.data.GuardianRepository
import com.squareup.picasso.Picasso
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

class NewsFeedViewModel(app: Application) : AndroidViewModel(app), NewsItemViewHolder.Callback {

    companion object {
        private val TAG = NewsFeedViewModel::class.java.simpleName

        const val PAGE_SIZE = 5

        private val INITIAL_STATE = State(
                meta = GuardianMeta(0, 0, 0),
                items = emptyList(),
                pagesLoaded = 0,
                canLoadMore = true,
                listItems = listOf(ContentLoadingListItem)
        )
    }

    data class State(
            val meta: GuardianMeta,
            val items: List<GuardianItem>,
            val pagesLoaded: Int,
            val canLoadMore: Boolean,
            val listItems: List<NewsFeedListItem>,
            val scrollToPosition: Int = -1,
            val scrollToRequestId: UUID? = null
    )

    class InfiniteRepeat : Function<Flowable<Throwable>, Flowable<Long>> {
        override fun apply(errors: Flowable<Throwable>): Flowable<Long> {
            return errors.zipWith(Flowable.range(1, Int.MAX_VALUE), BiFunction { _: Throwable, time: Int ->
                var delay = time.toLong()
                if (time > 3) {
                    delay = 3
                }
                Flowable.timer(delay, TimeUnit.SECONDS)
            }).flatMap { it }
        }
    }

    private val newsFeedStateData = MutableLiveData<State>()
    val newsFeedState: LiveData<State> get() = newsFeedStateData

    private var pageLoading = true

    private val loadPageProcessor = PublishProcessor.create<Int>()
    private val scrollToProcessor = PublishProcessor.create<String>()

    private val repository = GuardianRepository(getApplication(), pageSize = PAGE_SIZE)

    private val disposables = CompositeDisposable()

    fun init(newsId: String?) {
        if (disposables.size() > 0) {
            if (newsId != null) {
                scrollToProcessor.onNext(newsId)
            }

            return
        }

        val initialStream = Single.zip(
                repository.getMeta(),
                repository.getItems(),
                BiFunction { meta: GuardianMeta, items: List<GuardianItem> ->
                    { state: State ->
                        onInitialMutate(state, newsId, meta, items)
                    }
                })
                .retryWhen(InfiniteRepeat())
                .toFlowable()

        val loadPageStream = loadPageProcessor.observeOn(Schedulers.io())
                .flatMapSingle { page ->
                    repository.getItems(page)
                            .retryWhen(InfiniteRepeat())
                }
                .map { loadedItems ->
                    { state: State ->
                        onLoadPageMutate(state, loadedItems)
                    }
                }

        val scrollToStream = scrollToProcessor.observeOn(Schedulers.io())
                .map { newsIdToScroll ->
                    { state: State ->
                        onScrollMutate(state, newsIdToScroll)
                    }
                }

        Flowable.concat(initialStream, Flowable.merge(loadPageStream, scrollToStream))
                .scan(INITIAL_STATE) { state, mutator -> mutator(state) }
                .distinctUntilChanged { old, new -> old === new }
                .subscribeOn(Schedulers.io())
                .observeOn(MainScheduler)
                .subscribe({ state ->
                    newsFeedStateData.value = state
                    if (state !== INITIAL_STATE) {
                        pageLoading = false
                    }
                }, { error ->
                    Log.e(TAG, "Error during observing state", error)
                })
                .also {
                    disposables.add(it)
                }
    }

    override fun onNewsItemClick(item: NewsItem) {
        loadLargeIcon(item.image)
                .switchIfEmpty(getFallbackLargeIcon())
                .map { bmp ->
                    NotificationCompat.Builder(getApplication(), "default")
                            .setContentTitle(item.title)
                            .setContentText(item.text)
                            .setLargeIcon(bmp)
                            .setSmallIcon(R.drawable.ic_notification_small)
                            .setContentIntent(NewsFeedActivity.getLaunchingIntent(getApplication(), item.newsId))
                            .setAutoCancel(true)
                            .build()
                }
                .subscribeOn(Schedulers.io())
                .subscribe({ notification ->
                    NotificationManagerCompat.from(getApplication()).notify(1, notification)
                }, { error ->
                    Log.e(TAG, "Error during building notification", error)
                })
    }

    override fun onCleared() {
        disposables.clear()
        super.onCleared()
    }

    fun onListScrolled(lastVisibleItemPosition: Int) {
        if (lastVisibleItemPosition == -1) {
            return
        }

        val state = newsFeedState.value ?: return

        if (!state.canLoadMore) {
            return
        }

        if (state.listItems.size - lastVisibleItemPosition > 3) {
            return
        }

        if (pageLoading) {
            return
        }

        pageLoading = true

        Log.d(TAG, "Load next: ${state.pagesLoaded + 1}")
        loadPageProcessor.onNext(state.pagesLoaded + 1)
    }

    fun clearCache() {
        repository.clearCaches()
                .subscribeOn(Schedulers.io())
                .observeOn(MainScheduler)
                .subscribe({
                    Log.d(TAG, "Cache is empty")
                    Toast.makeText(getApplication(), "Cache cleared", Toast.LENGTH_SHORT).show()
                }, { error ->
                    Log.e(TAG, "Could not clear cache", error)
                })
    }

    //State mutator functions
    private fun onInitialMutate(state: State, newsIdToScroll: String?, meta: GuardianMeta, items: List<GuardianItem>): State {
        var scrollToPosition: Int = -1
        var scrollToRequestId: UUID? = null
        val listItems = if (meta.totalItems > 0 && items.isEmpty()) {
            listOf(ContentEmptyListItem)
        } else {
            items.mapToListItems().also { list ->
                if (meta.totalItems > list.size) {
                    list.add(PageLoadingListItem)
                }

                if (newsIdToScroll != null) {
                    val wrappedId = NewsItem.wrapId(newsIdToScroll)
                    scrollToPosition = list.indexOfFirst { it.id == wrappedId }
                    scrollToRequestId = UUID.randomUUID()
                }
            }
        }

        return state.copy(
                meta = meta,
                items = items,
                pagesLoaded = 1,
                canLoadMore = meta.totalPages > 1,
                listItems = listItems,
                scrollToPosition = scrollToPosition,
                scrollToRequestId = scrollToRequestId
        )
    }

    private fun onLoadPageMutate(state: State, loadedItems: List<GuardianItem>): State {
        val pagesLoaded = state.pagesLoaded + 1
        val canLoadMore = state.meta.totalPages > pagesLoaded
        return state.copy(
                items = state.items.plus(loadedItems),
                pagesLoaded = pagesLoaded,
                canLoadMore = canLoadMore,
                listItems = state.listItems.toMutableList().apply {
                    addAll(size - 1, loadedItems.mapToListItems())
                    if (!canLoadMore) {
                        removeAt(size - 1)
                    }
                })
    }

    private fun onScrollMutate(state: State, newsIdToScroll: String): State {
        val wrappedId = NewsItem.wrapId(newsIdToScroll)
        val position = state.listItems.indexOfFirst { it.id == wrappedId }
        return if (position != -1) {
            state.copy(
                    scrollToPosition = position,
                    scrollToRequestId = UUID.randomUUID()
            )
        } else {
            state
        }
    }


    //Util functions
    private fun loadLargeIcon(url: String): Maybe<Bitmap> {
        return Maybe
                .fromCallable {
                    Picasso.get()
                            .load(url)
                            .resize(256, 256)
                            .centerCrop()
                            .get()
                }
                .onErrorComplete()
    }

    private fun getFallbackLargeIcon(): Single<Bitmap> {
        return Single.fromCallable {
            BitmapFactory.decodeResource(getApplication<Application>().resources, R.mipmap.ic_launcher_round)
        }
    }

    private fun List<GuardianItem>.mapToListItems(): MutableList<NewsFeedListItem> {
        return mutableListOf<NewsFeedListItem>().apply {
            for (item in this@mapToListItems) {
                add(
                        NewsItem(
                                newsId = item.id,
                                image = item.thumbnail,
                                title = item.headline,
                                text = HtmlCompat.fromHtml(item.trailText, HtmlCompat.FROM_HTML_MODE_COMPACT)
                        )
                )
            }
        }
    }
}