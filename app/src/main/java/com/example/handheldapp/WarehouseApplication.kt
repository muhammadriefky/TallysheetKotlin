package com.example.handheldapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class untuk Warehouse Handheld App
 * Digunakan untuk initialize Hilt dependency injection
 */
@HiltAndroidApp
class WarehouseApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-level components here
    }
}
