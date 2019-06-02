package com.guardfeed.app.data

import com.google.gson.annotations.SerializedName

data class GuardianResponse(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("pageSize") val pageSize: Int = 0,
    @SerializedName("currentPage") val currentPage: Int = 0,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("results") val results: List<GuardianItem> = listOf()
) {
    class Response(
        @SerializedName("response") val response: GuardianResponse? = null
    ) {
        /** for Gson */
        @Suppress("unused")
        constructor() : this(null)
    }

    /** for Gson */
    @Suppress("unused")
    constructor() : this(0)
}