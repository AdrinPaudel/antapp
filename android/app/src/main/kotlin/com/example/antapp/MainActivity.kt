package com.adrin.antapp

import android.app.ActivityManager
import android.content.Context
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
            when (call.method) {
                "checkOverlayPermission" -> {
                    val canDraw = Settings.canDrawOverlays(this)
                    result.success(canDraw)
                }
                "requestOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        try {
                            startActivity(intent)
                            result.success(null)
                        } catch (e: Exception) {
                            result.error("ACTIVITY_START_FAILED", e.message, null)
                        }
                    } else {
                        result.success(true)
                    }
                }
                "startOverlay" -> {
                    val args = call.arguments as? Map<String, Any>
                    val antSize = args?.get("ant_size") as? Double
                    val antSpeed = args?.get("ant_speed") as? Double
                    val antImage = args?.get("ant_image") as? ByteArray

                    startOverlayService(
                        size = antSize?.toFloat(),
                        speed = antSpeed?.toFloat(),
                        image = antImage,
                        isUpdate = false
                    )
                    result.success(null)
                }
                "stopOverlay" -> {
                    // *** ADDED log to confirm this is called ***
                    Log.d("AntCrawlerNative", "stopOverlay command received. Stopping service.")
                    stopOverlayService()
                    result.success(null)
                }
                "isOverlayActive" -> {
                    val isActive = isServiceRunning(OverlayService::class.java)
                    result.success(isActive)
                }
                "updateAntSettings" -> {
                    if (!isServiceRunning(OverlayService::class.java)) {
                        result.success(null)
                        return@setMethodCallHandler
                    }
                    val args = call.arguments as? Map<String, Any>
                    val size = (args?.get("size") as? Double)?.toFloat()
                    val speed = (args?.get("speed") as? Double)?.toFloat()

                    startOverlayService(
                        size = size,
                        speed = speed,
                        image = null,
                        isUpdate = true
                    )
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun startOverlayService(
        size: Float? = null,
        speed: Float? = null,
        image: ByteArray? = null,
        isUpdate: Boolean = false
    ) {
        val intent = Intent(this, OverlayService::class.java)
        size?.let { intent.putExtra("ant_size_extra", it) }
        speed?.let { intent.putExtra("ant_speed_extra", it) }
        image?.let { intent.putExtra("ant_image_extra", it) }
        intent.putExtra("is_update_extra", isUpdate)

        if (!isUpdate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) { return true }
        }
        return false
    }
}
