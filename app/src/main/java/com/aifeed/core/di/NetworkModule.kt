package com.aifeed.core.di

import com.aifeed.BuildConfig
import com.aifeed.core.network.api.GeminiApiService
import com.aifeed.core.network.api.NewsApiService
import com.aifeed.core.network.api.SupabaseApiService
import com.aifeed.core.network.api.XApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SUPABASE_BASE_URL = "supabase_url"
    private const val NEWS_API_BASE_URL = "news_api_url"

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named(SUPABASE_BASE_URL)
    fun provideSupabaseBaseUrl(): String {
        val url = BuildConfig.SUPABASE_URL
        return if (url.isNotEmpty()) "$url/rest/v1/" else "https://placeholder.supabase.co/rest/v1/"
    }

    @Provides
    @Singleton
    @Named(NEWS_API_BASE_URL)
    fun provideNewsApiBaseUrl(): String {
        return "https://newsapi.org/v2/"
    }

    @Provides
    @Singleton
    @Named("SupabaseRetrofit")
    fun provideSupabaseRetrofit(
        okHttpClient: OkHttpClient,
        @Named(SUPABASE_BASE_URL) baseUrl: String
    ): Retrofit {
        val supabaseApiKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(supabaseApiKey.isNotEmpty()) {
            "SUPABASE_ANON_KEY is missing. Set it in local.properties and rebuild."
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val urlWithApiKey = original.url.newBuilder()
                        // Keep apikey in query as fallback in case intermediaries strip headers.
                        .addQueryParameter("apikey", supabaseApiKey)
                        .build()
                    val request = original.newBuilder()
                        .url(urlWithApiKey)
                        .header("apikey", supabaseApiKey)
                        .header("Authorization", "Bearer $supabaseApiKey")
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=representation")
                        .build()
                    chain.proceed(request)
                }
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("NewsApiRetrofit")
    fun provideNewsApiRetrofit(
        okHttpClient: OkHttpClient,
        @Named(NEWS_API_BASE_URL) baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("X-Api-Key", BuildConfig.NEWS_API_KEY)
                        .build()
                    chain.proceed(request)
                }
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSupabaseApiService(
        @Named("SupabaseRetrofit") retrofit: Retrofit
    ): SupabaseApiService {
        return retrofit.create(SupabaseApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNewsApiService(
        @Named("NewsApiRetrofit") retrofit: Retrofit
    ): NewsApiService {
        return retrofit.create(NewsApiService::class.java)
    }

    @Provides
    @Singleton
    @Named("XApiRetrofit")
    fun provideXApiRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.twitter.com/")
            .client(okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("Authorization", "Bearer ${BuildConfig.X_BEARER_TOKEN}")
                        .build()
                    chain.proceed(request)
                }
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideXApiService(
        @Named("XApiRetrofit") retrofit: Retrofit
    ): XApiService {
        return retrofit.create(XApiService::class.java)
    }

    @Provides
    @Singleton
    @Named("GeminiRetrofit")
    fun provideGeminiRetrofit(
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val original = chain.request()
                    val url = original.url.newBuilder()
                        .addQueryParameter("key", BuildConfig.GEMINI_API_KEY)
                        .build()
                    val request = original.newBuilder()
                        .url(url)
                        .header("Content-Type", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiApiService(
        @Named("GeminiRetrofit") retrofit: Retrofit
    ): GeminiApiService {
        return retrofit.create(GeminiApiService::class.java)
    }
}
