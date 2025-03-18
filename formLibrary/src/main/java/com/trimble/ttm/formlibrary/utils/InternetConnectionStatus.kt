package com.trimble.ttm.formlibrary.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.trimble.ttm.commons.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val PING_TIMEOUT = 5000
private const val NO_CONTENT_ERR_CODE = 204
private const val EXCEPTION_RETRY_DELAY = 2_000L
private const val MAX_EXCEPTION_RETRY_COUNT = 10
private const val NO_CONTENT_REQUEST_URL = "https://clients3.google.com/generate_204"

class InternetConnectionStatus(context: Context,
    private val dispatcherProvider: DispatcherProvider) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var connectExceptionRetryCount = 0
    /**
     * Memoization variable to help exception retry recursion function.
     * If we get positive response for internet connectivity then this will be returned
     * insteadof negative values from recursion stack. This will be reset upon recursion completion
     * and on network lost callback.
     */
    private var hasActiveInternet = false
    private val tag = "InternetConnectionStatus"

    val networkStatus = callbackFlow {
        val networkStatusCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val hasInternetCapability = hasInternetCapabilities(connectivityManager, network)
                if (hasInternetCapability.not()) {
                    return
                }
                checkForInternetConnectivity(this@callbackFlow)
            }

            override fun onLost(network: Network) {
                trySend(false)
                hasActiveInternet = false
            }

            override fun onUnavailable() {
                checkForInternetConnectivity(this@callbackFlow)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                checkForInternetConnectivity(this@callbackFlow)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(request, networkStatusCallback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkStatusCallback)
        }
    }.distinctUntilChanged()

    fun checkForInternetConnectivity(producerScope: ProducerScope<Boolean>) {
        CoroutineScope(SupervisorJob()).launch(CoroutineName("$tag Check connectivity") + dispatcherProvider.io()) {
            val hasInternet = pingGoogleToGetInternetConnectivityStatus()
            producerScope.trySend(hasInternet)
            hasActiveInternet = false
        }
    }

    fun hasInternetCapabilities(connectivityManager: ConnectivityManager, network: Network): Boolean =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                connectivityManager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
            else ->
                connectivityManager.getNetworkInfo(network)?.let { networkInfo ->
                    return@let networkInfo.isConnected
                } ?: false
        }

     suspend fun pingGoogleToGetInternetConnectivityStatus(): Boolean =
        coroutineScope {
            async(dispatcherProvider.io()) {
                try {
                    val urlConnection: HttpsURLConnection = URL(NO_CONTENT_REQUEST_URL).openConnection() as HttpsURLConnection
                    urlConnection.setRequestProperty("User-Agent", "Android")
                    urlConnection.setRequestProperty("Connection", "close")
                    urlConnection.connectTimeout = PING_TIMEOUT
                    urlConnection.connect()
                    hasActiveInternet = urlConnection.responseCode == NO_CONTENT_ERR_CODE && urlConnection.contentLength == ZERO
                    hasActiveInternet
                } catch (e: Exception) {
                    delay(EXCEPTION_RETRY_DELAY)
                    if (connectExceptionRetryCount < MAX_EXCEPTION_RETRY_COUNT) {
                        connectExceptionRetryCount += 1
                        pingGoogleToGetInternetConnectivityStatus()
                    }
                    hasActiveInternet
                }
            }.await()
        }

}