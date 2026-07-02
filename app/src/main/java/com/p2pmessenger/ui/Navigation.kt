package com.p2pmessenger.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.p2pmessenger.ui.addcontact.AddContactScreen
import com.p2pmessenger.ui.call.CallScreen
import com.p2pmessenger.ui.chat.ChatScreen
import com.p2pmessenger.ui.home.HomeScreen
import com.p2pmessenger.ui.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val ADD_CONTACT = "addContact"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{contactId}"
    const val CALL = "call/{contactId}"
    fun chat(contactId: String) = "chat/$contactId"
    fun call(contactId: String) = "call/$contactId"
}

@Composable
fun P2PMessengerNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddContact = { navController.navigate(Routes.ADD_CONTACT) },
                onOpenChat = { contactId -> navController.navigate(Routes.chat(contactId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.ADD_CONTACT) {
            AddContactScreen(
                onPaired = { contactId ->
                    navController.navigate(Routes.chat(contactId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CHAT) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onStartCall = { contactId -> navController.navigate(Routes.call(contactId)) },
            )
        }
        composable(Routes.CALL) {
            CallScreen(onBack = { navController.popBackStack() })
        }
    }
}
