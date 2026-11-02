package com.example.bleanchor

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var addressText: TextView
    private lateinit var versionText: TextView
    private lateinit var currentNameText: TextView
    private lateinit var nameInput: EditText
    private lateinit var applyNameButton: Button
    private var isAdvertising = false

    private val TAG = "MainActivity"
    private val PREFS_NAME = "ble_anchor_prefs"
    private val KEY_CUSTOM_NAME = "custom_bt_name"
    private val MAX_NAME_LENGTH = 16

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            showBluetoothInfo()
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
        versionText = findViewById(R.id.version_text)
        currentNameText = findViewById(R.id.current_name_text)
        nameInput = findViewById(R.id.name_input)
        applyNameButton = findViewById(R.id.apply_name_button)

        versionText.text = "V${BuildConfig.VERSION_NAME}"

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            statusText.text = "请先开启手机蓝牙"
            toggleButton.isEnabled = false
            addressText.text = "蓝牙地址: 不可用"
            currentNameText.text = "当前设备名称: 不可用"
            return
        }

        if (hasRequiredPermissions()) {
            showBluetoothInfo()
        } else {
            addressText.text = "蓝牙地址: 需授权后显示"
            currentNameText.text = "当前设备名称: 需授权后显示"
        }

        applyNameButton.setOnClickListener {
            applyBluetoothName()
        }

        toggleButton.setOnClickListener {
            if (isAdvertising) {
                stopService()
            } else {
                checkPermissionsAndStart()
            }
        }
    }

    private fun showBluetoothInfo() {
        try {
            val adapter = (getSystemService(BluetoothManager::class.java)).adapter
            if (adapter != null) {
                // 地址
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val addr = adapter.address
                    addressText.text = if (addr == "00:00:00:00:00:00" || addr == "02:00:00:00:00:00")
                        "蓝牙地址: 无法获取（系统限制）"
                    else
                        "蓝牙地址: $addr"
                } else {
                    addressText.text = "蓝牙地址: ${adapter.address}"
                }

                // 名称
                val currentName = adapter.name ?: ""
                currentNameText.text = "当前设备名称: $currentName"
                if (nameInput.text.isNullOrBlank()) {
                    nameInput.setText(currentName)
                }
            }
        } catch (e: SecurityException) {
            addressText.text = "蓝牙地址: 权限不足"
            currentNameText.text = "当前设备名称: 权限不足"
        }
    }

    private fun applyBluetoothName() {
        val newName = nameInput.text.toString().trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        if (newName.length > MAX_NAME_LENGTH) {
            Toast.makeText(this,
                "名称过长（${newName.length} 字符），建议不超过 $MAX_NAME_LENGTH 个字符",
                Toast.LENGTH_LONG).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "缺少蓝牙连接权限", Toast.LENGTH_SHORT).show()
                return
            }

            val adapter = (getSystemService(BluetoothManager::class.java)).adapter
            if (adapter == null) {
                Toast.makeText(this, "蓝牙适配器不可用", Toast.LENGTH_SHORT).show()
                return
            }

            val success = adapter.setName(newName)
            if (success) {
                // 保存到 SharedPreferences，供广播服务读取
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putString(KEY_CUSTOM_NAME, newName).apply()

                currentNameText.text = "当前设备名称: $newName"
                Toast.makeText(this, "名称已更新为: $newName", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "蓝牙名称修改成功: $newName")
            } else {
                Toast.makeText(this, "名称修改失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "修改名称权限不足", e)
            Toast.makeText(this, "权限不足，无法修改名称", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "修改名称异常", e)
            Toast.makeText(this, "修改失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
        if (hasRequiredPermissions()) startService()
        else requestPermissionLauncher.launch(permissions)
    }

    private fun startService() {
        val intent = Intent(this, BleAdvertiserService::class.java).apply { action = "START" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        isAdvertising = true
        toggleButton.text = "停止广播"
        statusText.text = "广播中..."
    }

    private fun stopService() {
        val intent = Intent(this, BleAdvertiserService::class.java).apply { action = "STOP" }
        startService(intent)
        isAdvertising = false
        toggleButton.text = "开始广播"
        statusText.text = "已停止"
    }
}
