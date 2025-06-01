package com.example.antapp // Adjust if your package name is different

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

// Import your app's R file if needed for R.mipmap.ic_launcher, e.g.:
// import com.example.antapp.R

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View

    private val NOTIFICATION_CHANNEL_ID = "ant_crawler_service_channel"
    private val NOTIFICATION_ID = 1337 // Arbitrary non-zero ID for the notification

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "onCreate called")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(200, 200) // Example size
            setBackgroundColor(android.graphics.Color.BLACK)   // Example color
        }

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            200, // Width
            200, // Height
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,  // <<< FLAG_SHOW_WHEN_LOCKED ADDED HERE
            PixelFormat.TRANSLUCENT
        )

        // Using the gravity and coordinates from your original working file
        params.gravity = Gravity.CENTER or Gravity.TOP
        params.x = 0
        params.y = 100

        try {
            if (!overlayView.isAttachedToWindow) {
                 windowManager.addView(overlayView, params)
                 Log.d("OverlayService", "Overlay view added successfully.")
            } else {
                Log.d("OverlayService", "Overlay view was already attached. Not adding again.")
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error adding overlay view in onCreate: ${e.message}", e)
            stopSelf() // Stop the service if the view can't be added
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannelName = "Ant Crawler Service"
            val serviceChannelDescription = "Channel for Ant Crawler background service"
            val importance = NotificationManager.IMPORTANCE_LOW // Use LOW to be less intrusive
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, serviceChannelName, importance).apply {
                description = serviceChannelDescription
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d("OverlayService", "Notification channel created: $NOTIFICATION_CHANNEL_ID")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OverlayService", "onStartCommand called")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        // IMPORTANT: Ensure R.mipmap.ic_launcher is a valid icon in your project.
        // If not, replace with android.R.drawable.ic_dialog_info or a custom small icon.
        val notificationIcon = R.mipmap.ic_launcher // <<< VERIFY THIS ICON IS VALID

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ant Crawler Active")
            .setContentText("The ant is crawling on your screen.")
            .setSmallIcon(notificationIcon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Consistent with channel importance
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d("OverlayService", "Service successfully started in foreground.")
        } catch (e: Exception) {
            Log.e("OverlayService", "Error calling startForeground: ${e.message}", e)
            // If startForeground fails, the service might be stopped by the system or crash.
            // Consider specific error handling or calling stopSelf().
        }
        
        return START_STICKY // Or START_NOT_STICKY if you don't want it to auto-restart
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("OverlayService", "onDestroy called")
        try {
            if (::windowManager.isInitialized && ::overlayView.isInitialized) {
                if (overlayView.isAttachedToWindow) {
                    windowManager.removeView(overlayView)
                    Log.d("OverlayService", "Overlay view removed successfully.")
                } else {
                    Log.d("OverlayService", "Overlay view was not attached to window. No need to remove.")
                }
            } else {
                Log.d("OverlayService", "WindowManager or OverlayView not initialized in onDestroy.")
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error removing overlay view in onDestroy: ${e.message}", e)
        }
        // stopForeground(true) // true to remove the notification when service is destroyed
        Log.d("OverlayService", "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We are not using binding
    }
}