package com.p2pmessenger

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.p2pmessenger.network.P2pConnectionForegroundService
import com.p2pmessenger.ui.P2PMessengerNavHost
import com.p2pmessenger.ui.PermissionsUtil
import com.p2pmessenger.ui.theme.P2PMessengerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var pendingInviteLink by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInviteLink = intent.dataString
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInviteLink = intent?.dataString
        setContent {
            P2PMessengerTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    // The "connectedDevice" foreground service type requires at least one of the
                    // Bluetooth runtime permissions to actually be *granted* (not just declared
                    // in the manifest) before we're allowed to start it -- starting it any
                    // earlier throws a SecurityException at runtime on API 34+.
                    val bleGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        PermissionsUtil.blePermissions.any { results[it] == true }
                    if (bleGranted) {
                        startForegroundService(
                            Intent(this@MainActivity, P2pConnectionForegroundService::class.java),
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(PermissionsUtil.all)
                }

                P2PMessengerNavHost(
                    pendingInviteLink = pendingInviteLink,
                    onInviteLinkConsumed = { pendingInviteLink = null },
                )
            }
        }
    }
}
