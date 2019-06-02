package com.guardfeed.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.forEach
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guardfeed.app.adapter.NewsFeedAdapter
import java.util.*

class NewsFeedActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_NEWS_ID = "extra.newsId"

        private const val STATE_SCROLL_TO_REQUEST_ID = "state.scrollToRequestId"

        fun getLaunchingIntent(context: Context, newsId: String): PendingIntent {
            return PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, NewsFeedActivity::class.java).putExtra(EXTRA_NEWS_ID, newsId),
                    PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    private lateinit var viewModel: NewsFeedViewModel

    private var consumedScrollToRequestId: UUID? = null

    private var density = 0f

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        consumedScrollToRequestId?.let {
            outState.putString(STATE_SCROLL_TO_REQUEST_ID, it.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            consumedScrollToRequestId = savedInstanceState.getString(STATE_SCROLL_TO_REQUEST_ID)?.let {
                UUID.fromString(it)
            }
        }

        setContentView(R.layout.news_feed_activity)

        density = resources.displayMetrics.density

        viewModel = ViewModelProviders.of(this).get(NewsFeedViewModel::class.java)

        findViewById<View>(R.id.btnClear).setOnClickListener {
            viewModel.clearCache()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.newsFeedList)
        recyclerView.doOnLayout {
            val itemHeight = resources.getDimensionPixelSize(R.dimen.news_feed_item_height)
            val parentHeight = recyclerView.height
            val padding = (parentHeight - itemHeight) / 2
            recyclerView.setPadding(0, padding, 0, padding)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            //80% - 100% scale
            recyclerView.addOnScrollListener(ItemScaleController(0.8f, 1f))
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    viewModel.onListScrolled(layoutManager.findLastVisibleItemPosition())
                }
            })

            val adapter = NewsFeedAdapter(viewModel)
            recyclerView.adapter = adapter

            viewModel.newsFeedState.observe(this, Observer<NewsFeedViewModel.State> { state ->
                if (state != null) {
                    val scrollToRequestId = state.scrollToRequestId
                    if (scrollToRequestId != null) {
                        adapter.setItems(state.listItems) {
                            val scrollToPosition = state.scrollToPosition
                            if (scrollToPosition != -1 && scrollToRequestId != consumedScrollToRequestId) {
                                recyclerView.smoothScrollToPosition(scrollToPosition)
                                consumedScrollToRequestId = state.scrollToRequestId
                            }
                        }
                    } else {
                        adapter.setItems(state.listItems)
                    }
                }
            })
        }

        onHandleIntent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        onHandleIntent()
    }

    private fun onHandleIntent() {
        val newsId = intent.run {
            if (hasExtra(EXTRA_NEWS_ID)) {
                getStringExtra(EXTRA_NEWS_ID).also {
                    removeExtra(EXTRA_NEWS_ID)
                }
            } else {
                null
            }
        }

        viewModel.init(newsId)
    }

    private fun px(dp: Float): Int {
        return (density * dp + 0.5f).toInt()
    }

    private class ItemScaleController(
            val minScale: Float,
            val maxScale: Float
    ) : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            recyclerView.forEach { child ->
                val parentHeight = recyclerView.height.toFloat()
                val parentCenter = parentHeight / 2f
                val height = child.bottom - child.top
                val center = child.top + height / 2f
                val max = parentHeight / 2f
                val diff = if (center > parentCenter) {
                    center - parentCenter
                } else {
                    parentCenter - center
                }
                val fraction = 1f - norm(0f, max, diff)

                val scale = minScale + (maxScale - minScale) * fraction
                child.scaleX = scale
                child.scaleY = scale
            }
        }

        private fun norm(min: Float, max: Float, value: Float): Float {
            return when {
                value < min -> 0f
                value > max -> 1f
                else -> value / (max - min)
            }
        }
    }
}