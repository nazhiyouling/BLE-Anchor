package com.example.bleanchor

import android.Manifest
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
import com.example.bleanchor.BuildConfig  // 新增

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var addressText: TextView
    private lateinit var versionText: TextView        // 新增：版本号显示
    private var isAdvertising = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            showBluetoothAddress()
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
        versionText = findViewById(R.id.version_text)   // 绑定版本号控件

        // 显示版本号（格式 V2026.07.24.1746）
        val versionName = BuildConfig.VERSION_NAME
        versionText.text = "V$versionName"

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            statusText.text = "请先开启手机蓝牙"
            toggleButton.isEnabled = false
            addressText.text = "本机蓝牙地址: 不可用"
            return
        }

        if (hasRequiredPermissions()) {
            showBluetoothAddress()
        } else {
            addressText.text = "本机蓝牙地址: 需授权后显示"
        }

        toggleButton.setOnClickListener {
            if (isAdvertising) {
                stopService()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun showBluetoothAddress() {
        try {
            val bluetoothManager = getSystemService(BluetoothManager::class.java)
            val adapter = bluetoothManager.adapter
            if (adapter != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        val addr = adapter.address
                        if (addr == "00:00:00:00:00:00" || addr == "02:00:00:00:00:00") {
                            addressText.text = "无法自动获取蓝牙地址\n请前往 设置→关于手机→状态信息 查看"
                        } else {
                            addressText.text = "本机蓝牙地址: $addr"
                        }
                    } else {
                        addressText.text = "本机蓝牙地址: 权限不足"
                    }
                } else {
                    val addr = adapter.address
                    addressText.text = "本机蓝牙地址: $addr"
                }
            }
        } catch (e: SecurityException) {
            addressText.text = "本机蓝牙地址: 权限被拒绝"
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }

        if (hasRequiredPermissions()) {
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
