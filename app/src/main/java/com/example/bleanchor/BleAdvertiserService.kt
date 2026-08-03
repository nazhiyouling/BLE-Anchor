package com.example.bleanchor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
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
            val settingsBuilder = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)

            // 尝试设置公共地址（Android 12+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val method = AdvertiseSettings.Builder::class.java.getMethod(
                        "setOwnAddressType", Int::class.javaPrimitiveType
                    )
                    val publicAddressValue = AdvertiseSettings::class.java
                        .getDeclaredField("ADVERTISE_OWN_ADDRESS_PUBLIC").getInt(null)
                    method.invoke(settingsBuilder, publicAddressValue)
                    Log.d(TAG, "已启用公共地址广播")
                } catch (e: Exception) {
                    Log.e(TAG, "无法设置公共地址，将使用随机地址", e)
                }
            } else {
                Log.d(TAG, "Android <12，使用默认地址")
            }

            val settings = settingsBuilder.build()
            val serviceUuid = ParcelUuid.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(serviceUuid)
                .build()

            // 扫描响应：携带设备固定 GUID
            val deviceGuidParcel = ParcelUuid(deviceGuid)
            val scanResponse = AdvertiseData.Builder()
                .addServiceUuid(deviceGuidParcel)
                .build()

            Log.d(TAG, "开始广播，设备GUID: $deviceGuid")
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
            val channel = NotificationChannel(CHANNEL_ID, "BLE锚点", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE锚点")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // 您可替换为自己的图标
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
