package com.example.internet_strentgh_poc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import io.flutter.plugin.common.EventChannel

/**
 * Streams live signal updates to Dart, and also serves the one-shot
 * MethodChannel read ([readCurrentSignal]) so both code paths share exactly
 * the same "which transport is active, and what's its signal" logic.
 *
 * @param hasLocationPermission callback into MainActivity's live permission
 *   check -- WiFi RSSI is only meaningful once ACCESS_FINE_LOCATION is
 *   granted at runtime (Android 8+), not merely declared in the manifest.
 */
class SignalStreamHandler(
    private val context: Context,
    private val hasLocationPermission: () -> Boolean
) : EventChannel.StreamHandler {

    private var sink: EventChannel.EventSink? = null

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentType: String = "none"
    private var lastCellularLevel: Int = 0
    private var isListening = false

    // Modern cellular listener (API 31+). Boxed as Any so referencing this
    // class doesn't affect verification on older OS versions.
    private var telephonyCallback: Any? = null

    // Legacy cellular listener, used below API 31.
    @Suppress("DEPRECATION")
    private val phoneStateListener = object : PhoneStateListener() {
        @Suppress("DEPRECATION")
        override fun onSignalStrengthsChanged(signal: SignalStrength) {
            lastCellularLevel = signal.level
            if (currentType == "cellular") {
                sendSignalUpdate("cellular", signal.level)
            }
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (currentType == "wifi") {
                updateWifiSignal()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val newType = transportFor(capabilities)
            if (newType != currentType) {
                currentType = newType
                updateCurrentSignal()
            }
        }

        override fun onLost(network: Network) {
            updateCurrentSignal()
        }
    }

    private fun transportFor(capabilities: NetworkCapabilities?): String = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
        else -> "none"
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        Log.d("SignalStrengthPOC", "onListen starting")
        sink = events
        isListening = true

        startCellularListening()

        val filter = IntentFilter(WifiManager.RSSI_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(wifiReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(wifiReceiver, filter)
        }

        val request = NetworkRequest.Builder().build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        updateCurrentSignal()
    }

    override fun onCancel(arguments: Any?) {
        Log.d("SignalStrengthPOC", "onCancel")
        isListening = false
        stopCellularListening()
        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (e: Exception) {
            Log.w("SignalStrengthPOC", "wifiReceiver already unregistered: ${e.message}")
        }
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w("SignalStrengthPOC", "networkCallback already unregistered: ${e.message}")
        }
        sink = null
    }

    /** Called by MainActivity once the user answers the runtime permission prompt. */
    fun onPermissionResult(granted: Boolean) {
        if (granted && isListening && currentType == "wifi") {
            updateWifiSignal()
        }
    }

    @Suppress("DEPRECATION")
    private fun startCellularListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signal: SignalStrength) {
                    lastCellularLevel = signal.level
                    if (currentType == "cellular") {
                        sendSignalUpdate("cellular", signal.level)
                    }
                }
            }
            telephonyCallback = callback
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        } else {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopCellularListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
            telephonyCallback = null
        } else {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
    }

    private fun updateCurrentSignal() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        currentType = transportFor(capabilities)

        when (currentType) {
            "wifi" -> updateWifiSignal()
            "cellular" -> sendSignalUpdate("cellular", currentCellularLevel())
            else -> sendSignalUpdate("none", 0)
        }
    }

    /**
     * Best-known cellular level right now. Reads the OS's already-cached
     * SignalStrength instead of hardcoding 0 and waiting for the next
     * callback -- avoids a visible flash to "no signal" when switching onto
     * cellular.
     */
    private fun currentCellularLevel(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                telephonyManager.signalStrength?.level?.let { return it }
            } catch (e: SecurityException) {
                Log.w("SignalStrengthPOC", "signalStrength unavailable: ${e.message}")
            }
        }
        return lastCellularLevel
    }

    private fun updateWifiSignal() {
        sendSignalUpdate("wifi", wifiLevel(), wifiRssiOrNull())
    }

    /**
     * Without a granted (not just declared) location permission, Android
     * returns a locked/dummy RSSI on API 27+. Report null rather than a
     * misleading number.
     */
    private fun wifiRssiOrNull(): Int? {
        if (!hasLocationPermission()) return null
        return wifiManager.connectionInfo.rssi
    }

    private fun wifiLevel(): Int {
        val rssi = wifiRssiOrNull() ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.calculateSignalLevel(rssi)
        } else {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, 5)
        }
    }

    /**
     * One-shot read used by the MethodChannel. Shares the exact same
     * active-transport detection as the live stream, instead of always
     * reading WiFi regardless of what's actually connected.
     */
    fun readCurrentSignal(): Map<String, Any?> {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val type = transportFor(capabilities)
        return when (type) {
            "wifi" -> mapOf("type" to "wifi", "level" to wifiLevel(), "rssi" to wifiRssiOrNull())
            "cellular" -> mapOf("type" to "cellular", "level" to currentCellularLevel(), "rssi" to null)
            else -> mapOf("type" to "none", "level" to 0, "rssi" to null)
        }
    }

    private fun sendSignalUpdate(type: String, level: Int, rssi: Int? = null) {
        val data = mutableMapOf<String, Any?>()
        data["type"] = type
        data["level"] = level
        data["rssi"] = rssi

        handler.post {
            Log.d("SignalStrengthPOC", "Sinking: $data")
            sink?.success(data)
        }
    }
}
