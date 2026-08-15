package com.example.internet_strentgh_poc

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.fluttercon/signal"
    private val EVENTS = "com.fluttercon/signal_stream"
    private val LOCATION_PERMISSION_REQUEST_CODE = 8451

    private var signalStreamHandler: SignalStreamHandler? = null

    // Holds a MethodChannel result while we wait on the runtime permission
    // dialog, so we can answer it once the user responds instead of
    // returning a stale/dummy reading immediately.
    private var pendingMethodResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        Log.i("SignalStrengthPOC", "MainActivity: configureFlutterEngine")

        // NOTE: declaring ACCESS_FINE_LOCATION in AndroidManifest.xml is not
        // enough on its own. On Android 8+, WifiManager silently returns a
        // locked/dummy RSSI unless the permission is actually GRANTED at
        // runtime. We request it explicitly below.
        signalStreamHandler = SignalStreamHandler(applicationContext) { hasLocationPermission() }
        EventChannel(engine.dartExecutor.binaryMessenger, EVENTS)
            .setStreamHandler(signalStreamHandler)

        MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSignalStrength" -> {
                        if (hasLocationPermission()) {
                            result.success(signalStreamHandler?.readCurrentSignal())
                        } else {
                            // Defer: answer this call once the permission
                            // dialog resolves instead of returning a
                            // misleading reading right now.
                            pendingMethodResult = result
                            requestLocationPermission()
                        }
                    }
                    "hasLocationPermission" -> result.success(hasLocationPermission())
                    "requestLocationPermission" -> {
                        if (!hasLocationPermission()) {
                            requestLocationPermission()
                        }
                        result.success(hasLocationPermission())
                    }
                    else -> result.notImplemented()
                }
            }

        if (!hasLocationPermission()) {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.i("SignalStrengthPOC", "Location permission granted=$granted")

            // Let the live stream re-read WiFi now that permission state changed.
            signalStreamHandler?.onPermissionResult(granted)

            // Answer any one-shot call that was waiting on this dialog.
            pendingMethodResult?.let { result ->
                result.success(signalStreamHandler?.readCurrentSignal())
                pendingMethodResult = null
            }
        }
    }
}
