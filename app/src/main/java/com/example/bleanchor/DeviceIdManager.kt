package com.example.bleanchor

import android.content.Context
import java.util.*

object DeviceIdManager {
    private const val PREFS_NAME = "ble_anchor_prefs"
    private const val KEY_DEVICE_GUID = "device_guid"

    fun getDeviceGuid(context: Context): UUID {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_GUID, null)
        if (existing != null) {
            return UUID.fromString(existing)
        }
        val newGuid = UUID.randomUUID()
        prefs.edit().putString(KEY_DEVICE_GUID, newGuid.toString()).apply()
        return newGuid
    }
}
