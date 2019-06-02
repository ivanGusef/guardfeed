package com.guardfeed.app.adapter

sealed class NewsFeedListItem {
    abstract val id: String
}

object ContentLoadingListItem : NewsFeedListItem() {
    override val id: String
        get() = "contentLoading"
}

object ContentEmptyListItem : NewsFeedListItem() {
    override val id: String
        get() = "contentEmpty"
}

object PageLoadingListItem : NewsFeedListItem() {
    override val id: String
        get() = "pageLoading"
}

data class NewsItem(
    val newsId: String,
    val image: String,
    val title: String,
    val text: CharSequence
) : NewsFeedListItem() {
    override val id: String = wrapId(newsId)

    companion object {
        fun wrapId(newsId: String) : String {
            return "item:$newsId"
        }
    }
}