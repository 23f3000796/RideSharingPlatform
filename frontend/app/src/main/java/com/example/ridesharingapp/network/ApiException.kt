package com.example.ridesharingapp.network

class ApiException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)
