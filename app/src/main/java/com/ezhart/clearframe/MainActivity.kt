package com.ezhart.clearframe

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ezhart.clearframe.data.loadConfig
import com.ezhart.clearframe.sync.SyncService
import com.ezhart.clearframe.ui.screens.AppViewModel
import com.ezhart.clearframe.ui.screens.HomeScreen
import com.ezhart.clearframe.ui.theme.ClearFrameTheme
import com.kitesystems.nix.frame.MotionDetectedEvent
import com.kitesystems.nix.frame.MotionSensor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.Calendar
import java.util.TimeZone

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var mService: SyncService
    private var isServiceBound: Boolean = false
    private val motionSensor: MotionSensor = MotionSensor()
    var lastActivity: Long = epochSeconds()
    var isAwake = false
    var appViewModel: AppViewModel? = null

    var sleepCheckJob: Job? = null

    var sleepTimeoutSeconds = 15 * 60
    var sleepHourStart = 23
    var wakeHourStart = 6
    var motionSensorActive = true

    val sleepCheckInterval: Long = 1000 * 60

    private val remoteKeys: List<Int> = listOf(131, 132, 19, 21, 22, 20, 4, 134, 23, 24, 25)

    data class RemoteKeyPressEvent(val keyCode: Int)

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d(TAG, "onServiceConnected...")
            val binder = service as SyncService.SyncServiceBinder
            mService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MotionSensor.initialize(this)

        val config = loadConfig(this)
        sleepTimeoutSeconds = config.sleepTimeoutMinutes * 60
        sleepHourStart = config.sleepHourStart
        wakeHourStart = config.wakeHourStart
        motionSensorActive = config.motionSensorEnabled

        enableEdgeToEdge()

        setContent {
            ClearFrameTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val vm: AppViewModel =
                        viewModel(factory = AppViewModel.Factory)
                    appViewModel = vm

                    HomeScreen(
                        uiState = vm.uiState,
                        displayMode = vm.displayMode
                    )
                }
            }
        }

        if (isInWakeSchedule()) {
            wake()
        }

        Intent(this, SyncService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        EventBus.getDefault().register(this)

        GlobalScope.launch { motionSensor.start() }
        if (!isAwake) {
            sleepCheckJob = GlobalScope.launch { sleepCheck() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed, reloading photos")
        appViewModel?.reload()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume, waking up...")
        wake()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "KeyUp received: $keyCode")

        resetInactivityTimer()

        if (remoteKeys.contains(keyCode)) {
            EventBus.getDefault().post(RemoteKeyPressEvent(keyCode))
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    fun resetInactivityTimer() {
        lastActivity = epochSeconds()
    }

    fun epochSeconds(): Long {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)

        Log.d(TAG, "Frame thinks time is ${cal.time}")

        val epochSeconds = cal.timeInMillis / 1000
        return epochSeconds
    }

    fun isInWakeSchedule(): Boolean {
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return !(hour < wakeHourStart || hour >= sleepHourStart)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun wake() {

        if (isAwake) {
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val screenIsOn = pm.isInteractive
        if (!screenIsOn) {
            Log.d(TAG, "Screen is not active, (briefly) acquiring wake lock to wake it up")
            val wakeLockTag = packageName + "WAKELOCK"
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE, wakeLockTag
            )
            wakeLock.acquire(10 * 1000)
            wakeLock.release()
        }

        isAwake = true

        sleepCheckJob?.cancel()
        sleepCheckJob = GlobalScope.launch { sleepCheck() }
    }

    fun sleep() {
        if (!isAwake) return

        isAwake = false

        Log.d(TAG, "Device should go to sleep, disabling FLAG_KEEP_SCREEN_ON")

        Handler(Looper.getMainLooper()).post {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    suspend fun sleepCheck() {
        while (isAwake) {
            val elapsedSeconds = epochSeconds() - lastActivity

            Log.d(
                TAG,
                "Sleep check, elapsed time since last activity is $elapsedSeconds seconds, timeout is $sleepTimeoutSeconds seconds"
            )

            if (elapsedSeconds >= sleepTimeoutSeconds) {
                sleep()
            } else {
                delay(sleepCheckInterval)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public fun handleMotionDetected(event: MotionDetectedEvent) {
        if (!motionSensorActive) return
        resetInactivityTimer()
        if (isInWakeSchedule()) {
            wake()
        }
    }
}