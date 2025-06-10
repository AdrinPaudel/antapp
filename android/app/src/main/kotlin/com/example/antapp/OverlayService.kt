package com.adrin.antapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlayView: ImageView? = null
    private var wmParams: WindowManager.LayoutParams? = null
    private var antBitmap: Bitmap? = null

    // --- Settings ---
    private var currentAntSize: Int = 25
    private var baseAntSpeedPixelsPerSecond: Float = 50.0f

    // --- Movement State ---
    private val movementHandler = Handler(Looper.getMainLooper())
    private var preciseX: Float = 0f
    private var preciseY: Float = 0f
    private var vectorX: Float = 0f
    private var vectorY: Float = -1f
    private val random = Random()
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val updateIntervalMillis: Long = 16

    // --- Behavior Parameters ---
    private var wanderStrength: Float = 0.5f
    private var wanderFrequency: Float = 0.1f
    private var effectiveAntSpeedPixelsPerSecond: Float = baseAntSpeedPixelsPerSecond
    private var currentSpeedMultiplier: Float = 1.0f
    private var speedVariationUpdateCounter: Int = 0
    private var isCurrentlyPaused: Boolean = false
    private var pauseEndTimeMillis: Long = 0L
    private val pauseChancePerFrame: Float = 0.005f
    private val minPauseDurationMillis: Long = 500
    private val maxPauseDurationMillis: Long = 3000

    private val NOTIFICATION_CHANNEL_ID = "ant_crawler_service_channel"
    private val NOTIFICATION_ID = 1337
    private var serviceHasBeenStartedInForeground = false

    override fun onCreate() {
        super.onCreate()
        Log.d("OverlayService", "[DEBUG] onCreate: Service is creating.")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val display = windowManager.defaultDisplay
        val sizePoint = Point()
        display.getRealSize(sizePoint)
        screenWidth = sizePoint.x
        screenHeight = sizePoint.y
        Log.d("OverlayService", "[DEBUG] onCreate: Screen dimensions: $screenWidth x $screenHeight")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("OverlayService", "[DEBUG] onStartCommand: Service received a command.")
        val isUpdateRequest = intent?.getBooleanExtra("is_update_extra", false) ?: false

        if (!isUpdateRequest) {
            Log.d("OverlayService", "[DEBUG] onStartCommand: This is a FRESH START command.")
            val imageBytes = intent?.getByteArrayExtra("ant_image_extra")
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                Log.d("OverlayService", "[DEBUG] onStartCommand: Received image byte array with length: ${imageBytes.size}.")
                try {
                    antBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (antBitmap != null) {
                         Log.d("OverlayService", "[DEBUG] onStartCommand: Ant bitmap successfully decoded.")
                    } else {
                        Log.e("OverlayService", "[DEBUG] FATAL: BitmapFactory.decodeByteArray returned null!")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                } catch (e: Exception) {
                    Log.e("OverlayService", "[DEBUG] FATAL: Exception while decoding bitmap: ${e.message}", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
            } else {
                Log.e("OverlayService", "[DEBUG] FATAL: Start command received without valid image bytes. Stopping.")
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            Log.d("OverlayService", "[DEBUG] onStartCommand: This is an UPDATE command.")
        }

        currentAntSize = intent?.getFloatExtra("ant_size_extra", currentAntSize.toFloat())?.toInt() ?: currentAntSize
        baseAntSpeedPixelsPerSecond = intent?.getFloatExtra("ant_speed_extra", baseAntSpeedPixelsPerSecond) ?: baseAntSpeedPixelsPerSecond
        Log.d("OverlayService", "[DEBUG] onStartCommand: Settings updated -> Size: $currentAntSize, Speed: $baseAntSpeedPixelsPerSecond")

        setupViewAndParams()

        if (!serviceHasBeenStartedInForeground) {
             val notificationIntent = Intent(this, MainActivity::class.java)
             val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE } else { PendingIntent.FLAG_UPDATE_CURRENT }
             val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)
             val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Ant Crawler Active").setContentText("The ant is crawling on your screen.")
                .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW).build()
            try {
                startForeground(NOTIFICATION_ID, notification)
                serviceHasBeenStartedInForeground = true
                Log.d("OverlayService", "[DEBUG] onStartCommand: Service started in foreground.")
            } catch (e: Exception) {
                Log.e("OverlayService", "[DEBUG] FATAL: Error calling startForeground: ${e.message}", e)
            }
        }
        return START_STICKY
    }

    private fun setupViewAndParams() {
        Log.d("OverlayService", "[DEBUG] setupViewAndParams: Starting view setup.")
        if (antBitmap == null) {
            Log.e("OverlayService", "[DEBUG] FATAL: setupViewAndParams called but antBitmap is null.")
            stopSelf()
            return
        }

        if (overlayView == null) {
            preciseX = (screenWidth / 2 - currentAntSize / 2).toFloat()
            preciseY = 100f
            
            overlayView = ImageView(this).apply {
                setImageBitmap(antBitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            overlayView!!.addOnAttachStateChangeListener(viewAttachListener)
            Log.d("OverlayService", "[DEBUG] setupViewAndParams: New ImageView created.")
        }
        
        if (wmParams == null) {
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            wmParams = WindowManager.LayoutParams().apply {
                    width = currentAntSize
                    height = currentAntSize
                    type = layoutFlag
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.TOP or Gravity.START
                    x = preciseX.roundToInt()
                    y = preciseY.roundToInt()
            }
            Log.d("OverlayService", "[DEBUG] setupViewAndParams: New WindowManager.LayoutParams created.")
        }

        var paramsChanged = false
        if (wmParams!!.width != currentAntSize) { wmParams!!.width = currentAntSize; paramsChanged = true }
        if (wmParams!!.height != currentAntSize) { wmParams!!.height = currentAntSize; paramsChanged = true }

        if (overlayView!!.parent == null) {
            Log.d("OverlayService", "[DEBUG] setupViewAndParams: View is not attached. Calling addView...")
            try {
                windowManager.addView(overlayView, wmParams)
                Log.d("OverlayService", "[DEBUG] setupViewAndParams: addView call SUCCEEDED.")
            } catch (e: Exception) {
                Log.e("OverlayService", "[DEBUG] FATAL: Exception during windowManager.addView: ${e.message}", e)
                stopSelf()
            }
        } else if (paramsChanged) {
            Log.d("OverlayService", "[DEBUG] setupViewAndParams: View is attached and params changed. Calling updateViewLayout...")
            try {
                windowManager.updateViewLayout(overlayView, wmParams)
                Log.d("OverlayService", "[DEBUG] setupViewAndParams: updateViewLayout call SUCCEEDED.")
            } catch (e: Exception) {
                Log.e("OverlayService", "[DEBUG] FATAL: Exception during updateViewLayout: ${e.message}", e)
            }
        }
    }

    private val viewAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            Log.d("OverlayService", "[DEBUG] viewAttachListener: View has been ATTACHED to window.")
            wmParams?.let { preciseX = it.x.toFloat(); preciseY = it.y.toFloat() }
            startMovementLogic()
        }
        override fun onViewDetachedFromWindow(v: View) {
            Log.d("OverlayService", "[DEBUG] viewAttachListener: View has been DETACHED from window.")
            movementHandler.removeCallbacks(movementRunnable)
        }
    }

    private fun startMovementLogic() {
        Log.d("OverlayService", "[DEBUG] startMovementLogic: Preparing to start movement.")
        movementHandler.removeCallbacks(movementRunnable)
        if (overlayView != null && overlayView!!.isAttachedToWindow) {
            Log.d("OverlayService", "[DEBUG] startMovementLogic: Conditions met. Posting movement runnable.")
            movementHandler.post(movementRunnable)
        } else {
            Log.e("OverlayService", "[DEBUG] startMovementLogic: FAILED. View is null or not attached.")
        }
    }
    
    // --- Unchanged Methods Below (movementRunnable, onDestroy, etc.) ---

    private val movementRunnable = object : Runnable {
        override fun run() {
            if (overlayView == null || !overlayView!!.isAttachedToWindow || wmParams == null) {
                movementHandler.removeCallbacks(this); return
            }
            if (isCurrentlyPaused) {
                if (System.currentTimeMillis() >= pauseEndTimeMillis) { isCurrentlyPaused = false }
                else { movementHandler.postDelayed(this, updateIntervalMillis); return }
            }
            if (speedVariationUpdateCounter <= 0) {
                currentSpeedMultiplier = 0.7f + random.nextFloat() * 0.6f
                effectiveAntSpeedPixelsPerSecond = baseAntSpeedPixelsPerSecond * currentSpeedMultiplier
                speedVariationUpdateCounter = random.nextInt(90) + 30
            } else { speedVariationUpdateCounter-- }

            if (random.nextFloat() < wanderFrequency) {
                val currentAngle = atan2(vectorY, vectorX)
                val angleChange = (random.nextFloat() * 2f - 1f) * wanderStrength
                vectorX = cos(currentAngle + angleChange); vectorY = sin(currentAngle + angleChange)
            }
            val pixelsToMoveThisFrame = effectiveAntSpeedPixelsPerSecond * (updateIntervalMillis / 1000.0f)
            preciseX += vectorX * pixelsToMoveThisFrame; preciseY += vectorY * pixelsToMoveThisFrame

            var bounced = false
            if (preciseX <= 0f) { preciseX = 0f; vectorX *= -1; bounced = true }
            else if (preciseX >= screenWidth - currentAntSize) { preciseX = (screenWidth - currentAntSize).toFloat(); vectorX *= -1; bounced = true }
            if (preciseY <= 0f) { preciseY = 0f; vectorY *= -1; bounced = true }
            else if (preciseY >= screenHeight - currentAntSize) { preciseY = (screenHeight - currentAntSize).toFloat(); vectorY *= -1; bounced = true }
            if (bounced) {
                val randomAngleOffset = (random.nextFloat() - 0.5f) * 1.5f
                val currentAngle = atan2(vectorY, vectorX)
                vectorX = cos(currentAngle + randomAngleOffset); vectorY = sin(currentAngle + randomAngleOffset)
            }
            val angleDegrees = Math.toDegrees(atan2(vectorY, vectorX).toDouble()).toFloat() + 90f
            overlayView?.rotation = angleDegrees
            wmParams!!.x = preciseX.roundToInt(); wmParams!!.y = preciseY.roundToInt()
            try {
                if (overlayView!!.isAttachedToWindow) { windowManager.updateViewLayout(overlayView, wmParams) }
            } catch (e: Exception) { movementHandler.removeCallbacks(this); return }
            
            if (!isCurrentlyPaused && random.nextFloat() < pauseChancePerFrame) {
                isCurrentlyPaused = true
                val pauseDuration = minPauseDurationMillis + random.nextInt((maxPauseDurationMillis - minPauseDurationMillis).toInt() + 1)
                pauseEndTimeMillis = System.currentTimeMillis() + pauseDuration
            }
            movementHandler.postDelayed(this, updateIntervalMillis)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("OverlayService", "[DEBUG] onDestroy: Service is being destroyed.")
        removeViewIfNeeded()
        antBitmap?.recycle()
        antBitmap = null
        wmParams = null
    }

    private fun removeViewIfNeeded() {
        movementHandler.removeCallbacks(movementRunnable)
        val viewToRemove = overlayView
        if (viewToRemove != null && viewToRemove.isAttachedToWindow) {
            try {
                viewToRemove.removeOnAttachStateChangeListener(viewAttachListener)
                windowManager.removeView(viewToRemove)
                Log.d("OverlayService", "[DEBUG] onDestroy: View successfully removed.")
            } catch (e: Exception) { Log.e("OverlayService", "[DEBUG] onDestroy: Exception removing view: ${e.message}") }
        }
        if (overlayView == viewToRemove) { overlayView = null }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Ant Crawler Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
