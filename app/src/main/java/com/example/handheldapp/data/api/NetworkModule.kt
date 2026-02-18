package com.example.handheldapp.data.api

import com.example.handheldapp.BuildConfig
import com.example.handheldapp.data.local.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt Module untuk Network dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
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
    fun provideAuthInterceptor(sessionManager: SessionManager): Interceptor {
        return Interceptor { chain ->
            val token = runBlocking {
                sessionManager.getToken().first()
            }

            val request = chain.request().newBuilder()

            // Add authorization header if token exists
            if (!token.isNullOrEmpty()) {
                request.addHeader("Authorization", "Bearer $token")
            }

            request.addHeader("Accept", "application/json")
            request.addHeader("Content-Type", "application/json")

            chain.proceed(request.build())
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDeliveryOrderApiService(retrofit: Retrofit): DeliveryOrderApiService {
        return retrofit.create(DeliveryOrderApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideScanApiService(retrofit: Retrofit): ScanApiService {
        return retrofit.create(ScanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideStagingAreaApiService(retrofit: Retrofit): StagingAreaApiService {
        return retrofit.create(StagingAreaApiService::class.java)
    }
}
