package com.example.ridesharingapp.network

import com.google.gson.Gson
import retrofit2.HttpException

private val gson = Gson()

fun <T> ApiEnvelope<T>.requireData(): T {
    if (!success || data == null) {
        throw ApiException(message.ifBlank { "Request failed" })
    }
    return data
}

fun HttpException.toApiException(): ApiException {
    val body = response()?.errorBody()?.string()
    val msg = when (code()) {
        401 -> "Your session has expired. Please sign in again."
        in 500..599 -> parseNestErrorMessage(body)?.takeUnless { it.contains("Internal server error", true) }
            ?: "Server error. Please try again in a moment."
        else -> parseNestErrorMessage(body) ?: message()
    }
    return ApiException(msg, code())
}

private fun parseNestErrorMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        val err = gson.fromJson(body, NestErrorBody::class.java)
        val m = err.message ?: return body
        when {
            m.isJsonPrimitive -> m.asJsonPrimitive.asString
            m.isJsonArray -> {
                val arr = m.asJsonArray
                arr.mapNotNull { if (it.isJsonPrimitive) it.asString else null }.joinToString(", ")
            }
            else -> body
        }
    } catch (_: Exception) {
        body
    }
}

suspend fun <T> runApi(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: HttpException) {
        Result.failure(e.toApiException())
    } catch (e: ApiException) {
        Result.failure(e)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        Result.failure(ApiException("Can't reach the server. Check your internet connection and try again."))
    } catch (e: Exception) {
        Result.failure(ApiException(e.message ?: "Network error"))
    }
