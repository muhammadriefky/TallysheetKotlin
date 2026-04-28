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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager untuk memantau status koneksi jaringan
 * Digunakan untuk offline mode dan sync mechanism
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkStateManager"
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(checkCurrentConnectivity())
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _networkType = MutableStateFlow(getCurrentNetworkType())
    val networkType: StateFlow<NetworkType> = _networkType.asStateFlow()

    init {
        registerNetworkCallback()
    }

    /**
     * Network type enum
     */
    enum class NetworkType {
        WIFI,
        CELLULAR,
        ETHERNET,
        NONE
    }

    /**
     * Check current connectivity status
     * UPDATED: Hapus requirement NET_CAPABILITY_VALIDATED (terlalu ketat)
     */
    fun checkCurrentConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // ✅ FIXED: Hanya perlu internet capability, tidak butuh validated
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get current network type
     */
    fun getCurrentNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
    }

    /**
     * Register network callback for real-time updates
     */
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _isConnected.value = true
                _networkType.value = getCurrentNetworkType()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                _isConnected.value = false
                _networkType.value = NetworkType.NONE
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                // ✅ FIXED: Hanya check internet capability
                _isConnected.value = hasInternet
                _networkType.value = getCurrentNetworkType()
            }
        })
    }

    /**
     * Observe connectivity as Flow
     */
    fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, callback)

        // Emit initial state
        trySend(checkCurrentConnectivity())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Check if connected to WiFi (for data-intensive operations)
     */
    fun isOnWifi(): Boolean = networkType.value == NetworkType.WIFI

    /**
     * Check if on metered connection (cellular)
     */
    fun isOnMeteredConnection(): Boolean {
        val network = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
