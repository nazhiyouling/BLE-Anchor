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
            "START" -> startBleAdvertising()
            "STOP" -> stopBleAdvertising()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBleAdvertising() {
        if (isAdvertising) return

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        // 配置广播参数：低延迟、高发射功率、可连接
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)       // 允许电脑端连接以读取 RSSI
            .build()

        // 广播数据：包含一个简单的服务 UUID（电脑端根据此 UUID 发现设备）
        val serviceUuid = ParcelUuid.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)         // 可选：让电脑显示手机名称
            .addServiceUuid(serviceUuid)
            .build()

        advertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
        isAdvertising = true

        // 启动前台服务，确保系统不杀死进程
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun stopBleAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE 广播启动成功")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE 广播启动失败: $errorCode")
            stopSelf()
        }
    }

    // 创建通知渠道（必须前台服务）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BLE 锚点服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // 构建前台通知
    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE 锚点运行中")
            .setContentText("正在持续广播蓝牙信号")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
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
