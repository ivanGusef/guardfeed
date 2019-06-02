package com.guardfeed.app.data

import android.content.Context
import com.google.gson.Gson
import io.reactivex.Completable
import io.reactivex.Single
import java.io.File

class GuardianRepository(context: Context, private val pageSize: Int) {

    private val gson by lazy { Gson() }

    private val backend by lazy { GuardianBackend(gson, "test") }

    private val cache by lazy { GuardianCache(gson, File(context.cacheDir, "news")) }

    fun getMeta(): Single<GuardianMeta> {
        return cache.getMeta()
                .switchIfEmpty(loadAndCachePage(page = 1).map { it.first })
    }

    fun getItems(): Single<List<GuardianItem>> {
        return cache.getPages()
                .switchIfEmpty(loadAndCachePage(page = 1).map { it.second })
    }

    fun getItems(page: Int): Single<List<GuardianItem>> {
        return loadAndCachePage(page).map { it.second }
    }

    private fun loadAndCachePage(page: Int): Single<Pair<GuardianMeta, List<GuardianItem>>> {
        return backend.load(page = page, pageSize = pageSize)
                .flatMap { response ->
                    val meta = GuardianMeta(response.pages, response.total, response.pageSize)
                    val items = response.results

                    cache.putMeta(meta)
                            .andThen(cache.putPage(page, items))
                            .andThen(Single.just(meta to items))
                }
    }

    fun clearCaches() : Completable {
        return cache.clear()
    }
}