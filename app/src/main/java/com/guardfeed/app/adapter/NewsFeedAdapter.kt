package com.guardfeed.app.adapter

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.guardfeed.app.R
import io.reactivex.schedulers.Schedulers

class NewsFeedAdapter(val callback: NewsItemViewHolder.Callback) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_CONTENT_LOADING = 1
        const val VIEW_TYPE_CONTENT_EMPTY = 2
        const val VIEW_TYPE_PAGE_LOADING = 3
        const val VIEW_TYPE_ITEM = 4
    }

    class DiffCallback(
            private val old: List<NewsFeedListItem>,
            private val new: List<NewsFeedListItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int {
            return old.size
        }

        override fun getNewListSize(): Int {
            return new.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition].id == new[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return old[oldItemPosition] == new[newItemPosition]
        }

    }

    private val handler = Handler(Looper.getMainLooper())

    private var diffRequestId = 0

    private var items = listOf<NewsFeedListItem>()

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CONTENT_LOADING -> LayoutViewHolder(R.layout.news_feed_content_loading_list_item, parent)
            VIEW_TYPE_CONTENT_EMPTY -> LayoutViewHolder(R.layout.news_feed_content_empty_list_item, parent)
            VIEW_TYPE_PAGE_LOADING -> LayoutViewHolder(R.layout.news_feed_page_loading_list_item, parent)
            VIEW_TYPE_ITEM -> NewsItemViewHolder(parent, this)
            else -> throw IllegalArgumentException("Unsupported viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder.itemViewType == VIEW_TYPE_ITEM) {
            (holder as NewsItemViewHolder).bind(items[position] as NewsItem)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            ContentLoadingListItem -> VIEW_TYPE_CONTENT_LOADING
            ContentEmptyListItem -> VIEW_TYPE_CONTENT_EMPTY
            PageLoadingListItem -> VIEW_TYPE_PAGE_LOADING
            else -> VIEW_TYPE_ITEM
        }
    }

    fun getItem(position: Int): NewsFeedListItem {
        return items[position]
    }

    fun setItems(
            newItems: List<NewsFeedListItem>,
            onSetCallback: (() -> Unit)? = null
    ) {
        val nextDiffRequest = ++diffRequestId
        val diffCallback = DiffCallback(items, newItems)

        Schedulers.computation().scheduleDirect {
            val result = DiffUtil.calculateDiff(diffCallback)
            handler.post {
                if (nextDiffRequest == diffRequestId) {
                    this.items = newItems
                    result.dispatchUpdatesTo(this)
                    onSetCallback?.invoke()
                }
            }
        }
    }
}