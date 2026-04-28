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
            val request = chain.request()
            val token = runBlocking {
                sessionManager.getToken().first()
            }

            val requestBuilder = request.newBuilder()
            requestBuilder.addHeader("ngrok-skip-browser-warning", "true")
            
            // CEK: Jika ini request login, JANGAN tambahkan Authorization header
            val isLoginRequest = request.url.encodedPath.endsWith("login")

            // Add authorization header if token exists and it's NOT a login request
            if (!token.isNullOrEmpty() && !isLoginRequest) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            requestBuilder.addHeader("Accept", "application/json")
            requestBuilder.addHeader("Content-Type", "application/json")

            chain.proceed(requestBuilder.build())
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
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

    @Provides
    @Singleton
    fun provideAppVersionApiService(retrofit: Retrofit): AppVersionApiService {
        return retrofit.create(AppVersionApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideProductApiService(retrofit: Retrofit): ProductApiService {
        return retrofit.create(ProductApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNotificationApiService(retrofit: Retrofit): NotificationApiService {
        return retrofit.create(NotificationApiService::class.java)
    }
}
