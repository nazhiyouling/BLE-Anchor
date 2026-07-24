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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var addressText: TextView
    private var isAdvertising = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startService()
        } else {
            Toast.makeText(this, "必须授予蓝牙权限才能广播", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleButton = findViewById(R.id.toggle_button)
        addressText = findViewById(R.id.address_text)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            statusText.text = "请先开启手机蓝牙"
            toggleButton.isEnabled = false
            return
        }

        // 显示本机蓝牙地址
        val address = bluetoothAdapter.address
        addressText.text = "本机蓝牙地址: $address"

        toggleButton.setOnClickListener {
            if (isAdvertising) {
                stopService()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            startService()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun startService() {
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
        statusText.text = "正在广播..."
    }

    private fun stopService() {
        val intent = Intent(this, BleAdvertiserService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        isAdvertising = false
        toggleButton.text = "开始广播"
        statusText.text = "已停止"
    }
}
