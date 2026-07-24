package com.example.bleanchor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat

class BleAdvertiserService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                startForeground(NOTIFICATION_ID, buildNotification("正在启动广播..."))
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
        if (isAdvertising) return

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            updateNotification("❌ 蓝牙未开启")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            updateNotification("❌ 设备不支持BLE广播")
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)          // 必须可连接
                .build()

            val serviceUuid = ParcelUuid.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)   // 不广播名称，避免数据过大
                .addServiceUuid(serviceUuid)
                .build()

            advertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            isAdvertising = true
        } catch (e: Exception) {
            Log.e(TAG, "启动广播异常", e)
            updateNotification("❌ 广播启动异常")
        }
    }

    private fun stopBleAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "停止广播异常", e)
        }
        isAdvertising = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "广播启动成功")
            updateNotification("✅ BLE 锚点运行中")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "广播启动失败: $errorCode")
            val msg = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "广播数据过大"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播实例过多"
                ADVERTISE_FAILED_ALREADY_STARTED -> "已经启动"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持"
                else -> "未知错误($errorCode)"
            }
            updateNotification("❌ 广播失败: $msg")
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE 锚点服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持蓝牙广播运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE 锚点")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopBleAdvertising()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BleAdvertiser"
        private const val CHANNEL_ID = "ble_anchor_channel"
        private const val NOTIFICATION_ID = 1
    }
}
