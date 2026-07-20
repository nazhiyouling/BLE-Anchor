package com.example.bleanchor

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private var isAdvertising = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startAdvertisingService()
        } else {
            statusText.text = "缺少必要权限，无法启动广播"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleButton = findViewById(R.id.toggle_button)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            statusText.text = "请先开启手机蓝牙"
            toggleButton.isEnabled = false
            return
        }

        toggleButton.setOnClickListener {
            if (isAdvertising) {
                stopAdvertisingService()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startAdvertisingService()
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startAdvertisingService()
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.BLUETOOTH)
                )
            }
        }
    }

    private fun startAdvertisingService() {
        val intent = Intent(this, BleAdvertiserService::class.java).apply {
            action = "START"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isAdvertising = true
        toggleButton.text = "停止广播"
        statusText.text = "正在广播 BLE 信号..."
    }

    private fun stopAdvertisingService() {
        val intent = Intent(this, BleAdvertiserService::class.java).apply {
            action = "STOP"
        }
        startService(intent)  // 发送 STOP 指令
        isAdvertising = false
        toggleButton.text = "开始广播"
        statusText.text = "广播已停止"
    }
}
