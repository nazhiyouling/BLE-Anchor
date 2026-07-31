package com.example.bleanchor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
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

class BleAdvertiserService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        registerScreenOffReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        if (isAdvertising) return
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter ?: run { updateNotification("❌ 蓝牙不可用"); return }
        advertiser = adapter.bluetoothLeAdvertiser ?: run { updateNotification("❌ 不支持BLE"); return }

        try {
            val settingsBuilder = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)

            // 反射设置公共地址（Android 12+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val method = AdvertiseSettings.Builder::class.java.getMethod(
                        "setOwnAddressType", Int::class.javaPrimitiveType
                    )
                    val publicAddress = AdvertiseSettings::class.java.getDeclaredField(
                        "ADVERTISE_OWN_ADDRESS_PUBLIC"
                    ).getInt(null)
                    method.invoke(settingsBuilder, publicAddress)
                    Log.d(TAG, "已启用公共地址")
                } catch (e: Exception) {
                    Log.e(TAG, "公共地址设置失败", e)
                }
            }

            val settings = settingsBuilder.build()
            val uuid = ParcelUuid.fromString("0000ABCD-0000-1000-8000-00805F9B34FB")
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(uuid)
                .build()

            advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    updateNotification("✅ BLE锚点运行中")
                }
                override fun onStartFailure(errorCode: Int) {
                    updateNotification("❌ 广播失败: $errorCode")
                }
            })
            isAdvertising = true
        } catch (e: Exception) {
            updateNotification("❌ 广播异常: ${e.message}")
        }
    }

    private fun stopBleAdvertising() {
        try {
            advertiser?.stopAdvertising(object : AdvertiseCallback() {})
        } catch (_: Exception) {}
        isAdvertising = false
    }

    // ----- 屏幕关闭时重新启动广播，防止系统暂停 -----
    private fun registerScreenOffReceiver() {
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    Log.d(TAG, "屏幕关闭，重新启动广播")
                    stopBleAdvertising()
                    startBleAdvertising()
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "BLE-Anchor::WakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10分钟，每次启动续期
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "BLE锚点", NotificationManager.IMPORTANCE_LOW
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
            .setSmallIcon(android.R.drawable.ic_menu_compass) // 注意：这里仍用系统图标
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        stopBleAdvertising()
        wakeLock?.let { if (it.isHeld) it.release() }
        screenOffReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BleAdvertiser"
        private const val CHANNEL_ID = "ble_anchor"
        private const val NOTIFICATION_ID = 1
    }
}
