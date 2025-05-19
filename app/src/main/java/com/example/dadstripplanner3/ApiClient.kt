package com.example.dadstripplanner3 // Your package name

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Base URL for the Transport for NSW API
    // Ensure this ends with a '/'
    private const val BASE_URL = "https://api.transport.nsw.gov.au/"

    // Lazy-initialized instance of our TfNSWApiService
    val instance: TfNSWApiService by lazy {
        retrofit.create(TfNSWApiService::class.java)
    }

    // Lazy-initialized Retrofit instance
    private val retrofit: Retrofit by lazy {
        // Create a logging interceptor
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // For debugging: Log HTTP request and response data.
            // Level.BODY logs headers and body. For production, you might use Level.NONE or Level.BASIC.
            level = if (BuildConfig.DEBUG) { // Only log in debug builds
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        // Create an OkHttpClient and add the logging interceptor
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Connection timeout
            .readTimeout(30, TimeUnit.SECONDS)    // Read timeout
            .writeTimeout(30, TimeUnit.SECONDS)   // Write timeout
            .build()

        // Build the Retrofit instance
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Set the custom OkHttpClient
            .addConverterFactory(GsonConverterFactory.create()) // Use Gson for JSON parsing
            .build()
    }
}