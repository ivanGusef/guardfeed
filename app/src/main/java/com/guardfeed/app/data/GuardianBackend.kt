package com.guardfeed.app.data

import com.google.gson.Gson
import io.reactivex.Single
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class GuardianBackend(private val gson: Gson, private val apiKey: String) {

    companion object {
        const val SCHEME = "https"
        const val HOST = "content.guardianapis.com"
        const val PATH = "search"

        const val PARAM_QUERY = "q"
        const val PARAM_APIKEY = "api-key"
        const val PARAM_PAGE = "page"
        const val PARAM_PAGE_SIZE = "page-size"
        const val PARAM_SHOW_FIELDS = "show-fields"
    }

    private val client = OkHttpClient()

    fun load(page: Int, pageSize: Int): Single<GuardianResponse> {
        return Single.fromCallable {
            val url = HttpUrl.Builder().scheme(SCHEME).host(HOST).addPathSegment(PATH)
                    .addQueryParameter(PARAM_QUERY, "football")
                    .addQueryParameter(PARAM_APIKEY, apiKey)
                    .addQueryParameter(PARAM_PAGE, page.toString())
                    .addQueryParameter(PARAM_PAGE_SIZE, pageSize.toString())
                    .addQueryParameter(PARAM_SHOW_FIELDS, "trailText,thumbnail")

            val request = Request.Builder().url(url.toString()).get().build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw RuntimeException("Request failed: ${response.code()}/${response.message()}")
            }

            val body = response.body() ?: throw RuntimeException("Response body is null")
            val bodyString = body.string()

            gson.fromJson(bodyString, GuardianResponse.Response::class.java)?.response
                ?: throw RuntimeException("Unable to parse body")
        }
    }
}