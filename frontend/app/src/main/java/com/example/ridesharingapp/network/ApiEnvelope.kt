package com.example.ridesharingapp.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ApiEnvelope<T>(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String = "",
    @SerializedName("data") val data: T? = null,
    @SerializedName("meta") val meta: JsonElement? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
)

data class PaginationMeta(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("limit") val limit: Int = 10,
)

data class NestErrorBody(
    @SerializedName("statusCode") val statusCode: Int? = null,
    @SerializedName("message") val message: JsonElement? = null,
    @SerializedName("path") val path: String? = null,
)
