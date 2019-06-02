package com.guardfeed.app.data

import com.google.gson.annotations.SerializedName

data class GuardianItem(
        @SerializedName("id") val id: String = "",
        @SerializedName("webTitle") val headline: String = "",
        @SerializedName("fields") private val fields: Fields? = null
) {
    data class Fields(
            @SerializedName("trailText") val trailText: String = "",
            @SerializedName("thumbnail") val thumbnail: String = ""
    ) {
        /** for Gson */
        @Suppress("unused")
        constructor() : this("")
    }

    val trailText: String
        get() = fields?.trailText ?: ""

    val thumbnail: String
        get() = fields?.thumbnail ?: ""

    /** for Gson */
    @Suppress("unused")
    constructor() : this("")
}