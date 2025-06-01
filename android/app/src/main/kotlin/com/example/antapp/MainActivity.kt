package com.example.antapp // Adjust if your package name is different

import android.app.ActivityManager // Required for checking running services
import android.content.Context // Required for getSystemService
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "overlay_permission"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Log.d("AntCrawlerNative", "Configuring Flutter Engine and Method Channel.")

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            Log.d("AntCrawlerNative", "Method call received: ${call.method}")
            when (call.method) {
                "checkOverlayPermission" -> {
                    val canDraw = Settings.canDrawOverlays(this)
                    Log.d("AntCrawlerNative", "checkOverlayPermission native call. Can draw: $canDraw")
                    result.success(canDraw)
                }
                "requestOverlayPermission" -> {
                    // ... (existing code for this method) ...
                    Log.d("AntCrawlerNative", "requestOverlayPermission native call.")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        try {
                            startActivity(intent)
                            result.success(null)
                        } catch (e: Exception) {
                            Log.e("AntCrawlerNative", "Error starting ACTION_MANAGE_OVERLAY_PERMISSION activity: ${e.message}")
                            result.error("ACTIVITY_START_FAILED", e.message, null)
                        }
                    } else {
                        Log.d("AntCrawlerNative", "Overlay permission request not applicable for this Android version or already granted implicitly.")
                        result.success(true)
                    }
                }
                "startOverlay" -> {
                    Log.d("AntCrawlerNative", "startOverlay native call.")
                    startOverlayService()
                    result.success(null)
                }
                "stopOverlay" -> {
                    Log.d("AntCrawlerNative", "stopOverlay native call.")
                    stopOverlayService()
                    result.success(null)
                }
                // ADD THIS NEW METHOD HANDLER
                "isOverlayActive" -> {
                    val isActive = isServiceRunning(OverlayService::class.java)
                    Log.d("AntCrawlerNative", "isOverlayActive check. Service running: $isActive")
                    result.success(isActive)
                }
                else -> {
                    Log.d("AntCrawlerNative", "Method ${call.method} not implemented.")
                    result.notImplemented()
                }
            }
        }
    }

    private fun startOverlayService() {
        // ... (existing code for this method) ...
        Log.d("AntCrawlerNative", "Attempting to start OverlayService.")
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d("AntCrawlerNative", "OverlayService start command issued.")
    }

    private fun stopOverlayService() {
        // ... (existing code for this method) ...
        Log.d("AntCrawlerNative", "Attempting to stop OverlayService.")
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Log.d("AntCrawlerNative", "OverlayService stop command issued.")
    }

    // ADD THIS NEW HELPER FUNCTION
    @Suppress("DEPRECATION") // getRunningServices is deprecated for 3rd party apps but okay for own app.
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true // Service is running
            }
        }
        return false // Service is not running
    }
}