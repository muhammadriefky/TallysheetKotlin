package com.example.handheldapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitor untuk status koneksi jaringan
 * Digunakan untuk menentukan mode online/offline
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Flow untuk observe status koneksi secara real-time
     * Emit true saat online, false saat offline
     */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                trySend(true)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                trySend(isCurrentlyConnected()) // Double check karena mungkin ada network lain
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                Log.d(TAG, "Network capabilities changed: internet=$hasInternet, validated=$hasValidated")
                // ✅ FIXED: Hanya perlu internet capability
                trySend(hasInternet)
            }

            override fun onUnavailable() {
                Log.d(TAG, "Network unavailable")
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(isCurrentlyConnected())

        awaitClose {
            Log.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Check status koneksi saat ini (synchronous)
     * Useful untuk quick check sebelum API call
     *
     * UPDATED: Hapus requirement NET_CAPABILITY_VALIDATED karena terlalu ketat.
     * Validasi Android bisa gagal meski server API kita bisa diakses.
     * Sekarang hanya check apakah ada koneksi internet capability.
     */
    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        // Log untuk debugging
        Log.d(TAG, "Network check: hasInternet=$hasInternet, hasValidated=$hasValidated")

        // ✅ FIXED: Hanya perlu internet capability, tidak butuh validated
        // Validated bisa false meski server API kita accessible (captive portal, DNS issue, etc)
        return hasInternet
    }

    /**
     * Get connection type info
     */
    fun getConnectionType(): ConnectionType {
        val network = connectivityManager.activeNetwork ?: return ConnectionType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.UNKNOWN
        }
    }
}

enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    UNKNOWN,
    NONE
}
