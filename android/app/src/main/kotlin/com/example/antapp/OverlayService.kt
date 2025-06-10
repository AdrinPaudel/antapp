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

    // Settings
    private var currentAntSize: Int = 50
    private var baseAntSpeedPixelsPerSecond: Float = 50.0f

    // Movement State
    private val movementHandler = Handler(Looper.getMainLooper())
    private var preciseX: Float = 0f
    private var preciseY: Float = 0f
    private var vectorX: Float = 0f
    private var vectorY: Float = -1f
    private val random = Random()
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val updateIntervalMillis: Long = 16

    // Behavior Parameters
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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val display = windowManager.defaultDisplay
        val sizePoint = Point()
        display.getRealSize(sizePoint)
        screenWidth = sizePoint.x
        screenHeight = sizePoint.y
    }

    private fun setupViewAndParams() {
        if (antBitmap == null) {
            stopSelf()
            return
        }
        
        val initialX = screenWidth / 2 - currentAntSize / 2
        val initialY = screenHeight / 2 - currentAntSize / 2

        if (overlayView == null) {
            preciseX = initialX.toFloat()
            preciseY = initialY.toFloat()
            
            overlayView = ImageView(this).apply {
                setImageBitmap(antBitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            overlayView!!.addOnAttachStateChangeListener(viewAttachListener)
        }
        
        if (wmParams == null) {
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            wmParams = WindowManager.LayoutParams(
                currentAntSize,
                currentAntSize,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = preciseX.roundToInt()
                y = preciseY.roundToInt()
            }
        }

        var paramsChanged = false
        if (wmParams!!.width != currentAntSize) { wmParams!!.width = currentAntSize; paramsChanged = true }
        if (wmParams!!.height != currentAntSize) { wmParams!!.height = currentAntSize; paramsChanged = true }

        if (overlayView!!.parent == null) {
             try {
                windowManager.addView(overlayView, wmParams)
             } catch (e: Exception) { stopSelf() }
        } else if (paramsChanged) {
            try {
                windowManager.updateViewLayout(overlayView, wmParams)
            } catch (e: Exception) { /* silent fail */ }
        }
    }
    
    // *** MODIFIED: Added a check for speed at the beginning of the animation loop ***
    private val movementRunnable = object : Runnable {
        override fun run() {
            if (overlayView == null || !overlayView!!.isAttachedToWindow || wmParams == null) {
                movementHandler.removeCallbacks(this)
                return
            }
            
            // If the speed is 0, skip all movement and rotation logic.
            // The ant will stay perfectly still.
            if (baseAntSpeedPixelsPerSecond <= 0f) {
                // We still need to re-post the handler so that if the user increases
                // the speed later, the animation can resume.
                movementHandler.postDelayed(this, updateIntervalMillis)
                return
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

            // This "wander" logic is what makes the ant look around. It is now
            // automatically skipped when speed is 0 because of the check above.
            if (random.nextFloat() < wanderFrequency) {
                val currentAngle = atan2(vectorY, vectorX)
                val angleChange = (random.nextFloat() * 2f - 1f) * wanderStrength
                vectorX = cos(currentAngle + angleChange)
                vectorY = sin(currentAngle + angleChange)
            }

            val pixelsToMoveThisFrame = effectiveAntSpeedPixelsPerSecond * (updateIntervalMillis / 1000.0f)
            preciseX += vectorX * pixelsToMoveThisFrame
            preciseY += vectorY * pixelsToMoveThisFrame

            var bounced = false
            if (preciseX <= 0f) { preciseX = 0f; vectorX *= -1; bounced = true }
            else if (preciseX >= screenWidth - currentAntSize) { preciseX = (screenWidth - currentAntSize).toFloat(); vectorX *= -1; bounced = true }
            if (preciseY <= 0f) { preciseY = 0f; vectorY *= -1; bounced = true }
            else if (preciseY >= screenHeight - currentAntSize) { preciseY = (screenHeight - currentAntSize).toFloat(); vectorY *= -1; bounced = true }

            if (bounced) {
                val randomAngleOffset = (random.nextFloat() - 0.5f) * 1.5f
                val currentAngle = atan2(vectorY, vectorX)
                vectorX = cos(currentAngle + randomAngleOffset)
                vectorY = sin(currentAngle + randomAngleOffset)
            }
            
            val angleDegrees = Math.toDegrees(atan2(vectorY, vectorX).toDouble()).toFloat() + 90f
            overlayView?.rotation = angleDegrees

            wmParams!!.x = preciseX.roundToInt()
            wmParams!!.y = preciseY.roundToInt()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isUpdateRequest = intent?.getBooleanExtra("is_update_extra", false) ?: false

        if (!isUpdateRequest) {
            val imageBytes = intent?.getByteArrayExtra("ant_image_extra")
            if (imageBytes != null) {
                antBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } else {
                stopSelf(); return START_NOT_STICKY
            }
        }

        currentAntSize = intent?.getFloatExtra("ant_size_extra", currentAntSize.toFloat())?.toInt() ?: currentAntSize
        baseAntSpeedPixelsPerSecond = intent?.getFloatExtra("ant_speed_extra", baseAntSpeedPixelsPerSecond) ?: baseAntSpeedPixelsPerSecond

        setupViewAndParams()
        
        if (!serviceHasBeenStartedInForeground) {
             val notificationIntent = Intent(this, MainActivity::class.java)
             val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE } else { PendingIntent.FLAG_UPDATE_CURRENT }
             val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)
             val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Ant Crawler Active").setContentText("The ant is crawling on your screen.")
                .setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW).build()
            try { startForeground(NOTIFICATION_ID, notification); serviceHasBeenStartedInForeground = true }
            catch (e: Exception) { /* silent fail */ }
        }
        return START_STICKY
    }

    private fun startMovementLogic() {
        movementHandler.removeCallbacks(movementRunnable)
        if (overlayView != null && overlayView!!.isAttachedToWindow) {
            movementHandler.post(movementRunnable)
        }
    }
    
    private val viewAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            wmParams?.let { preciseX = it.x.toFloat(); preciseY = it.y.toFloat() }
            startMovementLogic()
        }
        override fun onViewDetachedFromWindow(v: View) {
            movementHandler.removeCallbacks(movementRunnable)
        }
    }

    private fun removeViewIfNeeded() {
        movementHandler.removeCallbacks(movementRunnable)
        if (overlayView != null && overlayView!!.isAttachedToWindow) {
            try {
                windowManager.removeView(overlayView)
                Log.d("AntCrawler", "Overlay view removed successfully.")
            } catch (e: Exception) {
                Log.e("AntCrawler", "Error removing overlay view: ${e.message}")
            }
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AntCrawler", "OverlayService is being destroyed.")
        removeViewIfNeeded()
        antBitmap = null
        wmParams = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Ant Crawler Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
