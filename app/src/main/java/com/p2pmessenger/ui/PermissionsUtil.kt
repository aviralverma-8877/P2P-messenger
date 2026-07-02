package com.p2pmessenger.ui

import android.os.Build

/**
 * All the runtime permissions this app can ever need, grouped by feature. [MainActivity]
 * requests everything up front for simplicity -- a production app would ask contextually
 * (e.g. only request SMS permissions when the user picks "pair via SMS"), which is a TODO.
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

    val smsPermissions: Array<String> = arrayOf(
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.RECEIVE_SMS,
    )

    val mediaPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        )
    }

    val notificationPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    val all: Array<String>
        get() = blePermissions + smsPermissions + mediaPermissions + notificationPermissions
}
