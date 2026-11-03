package com.example.bleanchor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class BleAdvertiserService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private lateinit var deviceGuid: UUID

    private val PREFS_NAME = "ble_anchor_prefs"
    private val KEY_CUSTOM_NAME = "custom_bt_name"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        createNotificationChannel()
        acquireWakeLock()
        registerScreenOffReceiver()
        deviceGuid = DeviceIdManager.getDeviceGuid(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "收到指令: ${intent?.action}")
        when (intent?.action) {
            "START" -> {
                startForeground(NOTIFICATION_ID, buildNotification("正在启动..."))
                ensureBluetoothName()
                startBleAdvertising()
            }
            "STOP" -> {
                stopBleAdvertising()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureBluetoothName() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val savedName = prefs.getString(KEY_CUSTOM_NAME, null) ?: return
            if (savedName.isBlank()) return

            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter ?: return
            if (adapter.name != savedName) {
                val ok = adapter.setName(savedName)
                Log.d(TAG, "恢复蓝牙名称 '$savedName' 结果: $ok")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "恢复蓝牙名称权限不足", e)
        } catch (e: Exception) {
            Log.e(TAG, "恢复蓝牙名称异常", e)
        }
    }

    private fun startBleAdvertising() {
        if (isAdvertising) {
            Log.w(TAG, "已在广播中，忽略重复请求")
            return
        }

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            updateNotification("❌ 蓝牙不可用")
            Log.e(TAG, "蓝牙不可用")
            return
        }
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            updateNotification("❌ 不支持BLE")
            Log.e(TAG, "设备不支持BLE广播")
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build()

            // 主广播包：只放服务 UUID，节省空间
            val serviceUuid = ParcelUuid.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(serviceUuid)
                .build()

            // 扫描响应：携带设备名称（不占用主广播包，避免数据过大）
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)   // 扫描响应中附带当前蓝牙名称
                .build()

            Log.d(TAG, "开始广播，设备GUID: $deviceGuid，名称将通过扫描响应发送")
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.d(TAG, "广播启动成功，模式: ${settingsInEffect.mode}")
                    updateNotification("✅ BLE锚点运行中")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "广播启动失败，错误码: $errorCode")
                    updateNotification("❌ 广播失败: $errorCode")
                }
            })
            isAdvertising = true
        } catch (e: Exception) {
            Log.e(TAG, "广播异常", e)
            updateNotification("❌ 广播异常: ${e.message}")
        }
    }

    private fun stopBleAdvertising() {
        try {
            advertiser?.stopAdvertising(object : AdvertiseCallback() {})
            Log.d(TAG, "广播已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止广播异常", e)
        }
        isAdvertising = false
    }

    private fun registerScreenOffReceiver() {
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.w(TAG, "屏幕关闭，强制重启广播")
                        stopBleAdvertising()
                        startBleAdvertising()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "屏幕点亮")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenOffReceiver, filter)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BLE-Anchor::WakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE锚点",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE锚点")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        Log.d(TAG, "服务销毁")
        stopBleAdvertising()
        wakeLock?.let { if (it.isHeld) it.release() }
        screenOffReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BleService"
        private const val CHANNEL_ID = "ble_anchor"
        private const val NOTIFICATION_ID = 1
    }
}
