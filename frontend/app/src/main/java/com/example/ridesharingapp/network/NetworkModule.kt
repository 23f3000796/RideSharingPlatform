package com.example.ridesharingapp.network

import com.example.ridesharingapp.BuildConfig
import com.example.ridesharingapp.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private fun String.withTrailingSlash(): String = if (endsWith("/")) this else "$this/"

private class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/register")) {
            return chain.proceed(chain.request())
        }
        val token = SessionManager.accessToken()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

private class UnauthorizedInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            val path = chain.request().url.encodedPath
            if (!path.contains("auth/login") && !path.contains("auth/register") && SessionManager.isLoggedIn()) {
                SessionManager.clear()
                com.example.ridesharingapp.AuthEvents.emitUnauthorized()
            }
        }
        return response
    }
}

object NetworkModule {

    val api: RideSharingApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(UnauthorizedInterceptor())
            .apply { if (BuildConfig.DEBUG) addInterceptor(logging) }
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.withTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RideSharingApiService::class.java)
    }
}
