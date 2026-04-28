package com.example.handheldapp.data.api

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.example.handheldapp.data.local.AppDatabase
import com.example.handheldapp.data.local.dao.CachedBranchDao
import com.example.handheldapp.data.local.dao.CachedCompanyDao
import com.example.handheldapp.data.local.dao.CachedDeliveryOrderDao
import com.example.handheldapp.data.local.dao.CachedProductDao
import com.example.handheldapp.data.local.dao.CachedUserDao
import com.example.handheldapp.data.local.dao.PendingScanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module untuk Database dependencies
 * Menyediakan Room Database dan DAO untuk offline storage
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Untuk development, ganti dengan proper migration di production
            .build()
    }

    @Provides
    fun providePendingScanDao(database: AppDatabase): PendingScanDao {
        return database.pendingScanDao()
    }

    @Provides
    fun provideCachedProductDao(database: AppDatabase): CachedProductDao {
        return database.cachedProductDao()
    }

    @Provides
    fun provideCachedCompanyDao(database: AppDatabase): CachedCompanyDao {
        return database.cachedCompanyDao()
    }

    @Provides
    fun provideCachedBranchDao(database: AppDatabase): CachedBranchDao {
        return database.cachedBranchDao()
    }

    @Provides
    fun provideCachedUserDao(database: AppDatabase): CachedUserDao {
        return database.cachedUserDao()
    }

    @Provides
    fun provideCachedDeliveryOrderDao(database: AppDatabase): CachedDeliveryOrderDao {
        return database.cachedDeliveryOrderDao()
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}