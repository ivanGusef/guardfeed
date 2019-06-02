package com.guardfeed.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guardfeed.app.R
import com.squareup.picasso.Picasso

class NewsItemViewHolder(
        parent: ViewGroup,
        private val adapter: NewsFeedAdapter
) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.news_feed_list_item, parent, false)) {

    interface Callback {
        fun onNewsItemClick(item: NewsItem)
    }

    private val imageView = itemView.findViewById<ImageView>(R.id.image)
    private val titleView = itemView.findViewById<TextView>(R.id.title)
    private val textView = itemView.findViewById<TextView>(R.id.text)

    private val bound: NewsItem?
        get() = adapter.getItem(adapterPosition) as? NewsItem

    init {
        itemView.setOnClickListener {
            bound?.let {
                adapter.callback.onNewsItemClick(it)
            }
        }
    }

    fun bind(item: NewsItem) {
        Picasso.get().cancelRequest(imageView)
        if (item.image.isNotEmpty()) {
            Picasso.get()
                    .load(item.image)
                    .into(imageView)
        } else {
            imageView.setImageDrawable(null)
        }

        titleView.text = item.title
        textView.text = item.text
    }
}