package com.example.handheldapp.ui.base

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.example.handheldapp.data.local.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Base Activity yang menerapkan font size scaling secara global
 * Semua Activity harus extend dari BaseActivity ini
 * untuk mendapatkan efek font size yang konsisten
 *
 * Note: Tidak menggunakan @AndroidEntryPoint karena attachBaseContext
 * dipanggil sebelum Hilt injection. Child classes harus menambahkan
 * @AndroidEntryPoint sendiri.
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply font size scaling before activity is created
        val scaledContext = applyFontScale(newBase)
        super.attachBaseContext(scaledContext)
    }

    /**
     * Apply font scale based on saved settings
     */
    private fun applyFontScale(context: Context): Context {
        return try {
            // Get font size setting synchronously (needed before activity creation)
            val fontSize = runBlocking {
                try {
                    // Use application context to get SettingsManager
                    val settingsManager = SettingsManager(context.applicationContext)
                    settingsManager.getFontSize().first()
                } catch (e: Exception) {
                    "normal" // Default
                }
            }

            val scale = when (fontSize) {
                "small" -> 0.85f
                "large" -> 1.15f
                "xlarge" -> 1.30f
                else -> 1.0f // normal
            }

            val configuration = Configuration(context.resources.configuration)
            configuration.fontScale = scale

            context.createConfigurationContext(configuration)
        } catch (e: Exception) {
            context // Return original context if error
        }
    }

    /**
     * Recreate activity when font size changes
     */
    fun applyFontSizeChange() {
        recreate()
    }
}
