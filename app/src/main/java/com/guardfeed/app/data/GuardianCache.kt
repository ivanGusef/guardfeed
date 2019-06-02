package com.guardfeed.app.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.Completable
import io.reactivex.Maybe
import java.io.File
import java.io.FileFilter
import java.io.IOException

class GuardianCache(
    private val gson: Gson,
    private val cacheDir: File
) {

    companion object {
        val TAG: String = GuardianCache::class.java.simpleName

        val PAGE_FILE_NAME = Regex("[0-9]+\\.json")
    }

    fun putMeta(meta: GuardianMeta): Completable {
        return Completable.fromAction {
            writeMetaToFile(meta)
        }
    }

    fun getMeta(): Maybe<GuardianMeta> {
        return Maybe.fromCallable {
            readMetaFromFile()
        }
    }

    @Synchronized
    private fun writeMetaToFile(meta: GuardianMeta) {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.w(TAG, "Could not create cache directory ${cacheDir.path}")

            return
        }

        try {
            File(cacheDir, "meta.json").writeText(gson.toJson(meta))
        } catch (e: IOException) {
            Log.w(TAG, "Could not write to file meta.json", e)
        }
    }

    @Synchronized
    private fun readMetaFromFile(): GuardianMeta? {
        val file = File(cacheDir, "meta.json")
        if (!file.exists()) {

            return null
        }

        return try {
            gson.fromJson(file.reader(), GuardianMeta::class.java)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read file meta.json", e)
            return null
        }
    }

    fun putPage(page: Int, items: List<GuardianItem>): Completable {
        return Completable.fromAction {
            writePageToFile(page, items)
        }
    }

    fun getPages(): Maybe<List<GuardianItem>> {
        return Maybe.fromCallable {
            readPages()
        }
    }

    @Synchronized
    private fun writePageToFile(page: Int, items: List<GuardianItem>) {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.w(TAG, "Could not create cache directory ${cacheDir.path}")

            return
        }

        try {
            File(cacheDir, "$page.json").writeText(gson.toJson(items))
        } catch (e: IOException) {
            Log.w(TAG, "Could not write to file $page.json", e)
        }
    }

    @Synchronized
    private fun readPages(): List<GuardianItem>? {
        if (!cacheDir.exists()) {
            Log.w(TAG, "Could not read pages: cache dir ${cacheDir.path} does not exist")

            return null
        }

        try {
            val pageFiles = cacheDir.listFiles(FileFilter { it.name.matches(PAGE_FILE_NAME) })
            pageFiles.sortBy { it.name }

            return pageFiles.flatMapTo(mutableListOf<GuardianItem>()) { file ->
                readPageFromFile(file) ?: listOf()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read files from ${cacheDir.path}", e)

            return null
        }
    }

    @Synchronized
    private fun readPageFromFile(file: File): List<GuardianItem>? {
        if (!file.exists()) {
            Log.w(TAG, "Could not read file ${file.name}: does not exist")

            return null
        }

        return try {
            gson.fromJson(file.reader(), object : TypeToken<List<GuardianItem>>() {}.type)
        } catch (e: IOException) {
            Log.w(TAG, "Could not read file ${file.name}", e)

            return null
        }
    }

    fun clear(): Completable {
        return Completable.fromAction {
            cacheDir.listFiles().forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Could not delete ${file.name}")
                }
            }
        }
    }
}