package com.example.handheldapp

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.handheldapp.data.local.SettingsManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Application class untuk Warehouse Handheld App
 * Digunakan untuk initialize Hilt dependency injection
 *
 * Implements Configuration.Provider untuk custom WorkManager dengan HiltWorkerFactory
 */
@HiltAndroidApp
class WarehouseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()

        // Apply saved theme SYNCHRONOUSLY on app startup
        // This must happen before any activity is created
        applyThemeFromSettings()
    }

    /**
     * Apply theme from saved settings SYNCHRONOUSLY
     * This ensures theme is applied before any activity is created
     */
    private fun applyThemeFromSettings() {
        try {
            // Use runBlocking to ensure theme is set before activities start
            val themeMode = runBlocking {
                try {
                    // Create a local SettingsManager since @Inject might not be ready
                    val localSettingsManager = SettingsManager(applicationContext)
                    localSettingsManager.getThemeMode().first()
                } catch (e: Exception) {
                    "system"
                }
            }

            val nightMode = when (themeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            // Default to system theme if error
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /**
     * Provide custom WorkManager configuration with HiltWorkerFactory
     * Ini diperlukan agar WorkManager bisa instantiate @HiltWorker classes
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}
