package com.p2pmessenger.ui

import android.os.Build

/**
 * All the runtime permissions this app can ever need, grouped by feature. [MainActivity]
 * requests everything up front for simplicity -- a production app would ask contextually
 * (e.g. only request BLE permissions when the user opens "Add contact > Nearby"), which is a TODO.
 */
object PermissionsUtil {

    val blePermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val mediaPermissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
        // API 29 (Q) dropped the need for WRITE_EXTERNAL_STORAGE -- MediaStore inserts of our
        // own files work without it (see MediaStoreSaver).
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        else -> arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

    val notificationPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    val all: Array<String>
        get() = blePermissions + mediaPermissions + notificationPermissions
}
