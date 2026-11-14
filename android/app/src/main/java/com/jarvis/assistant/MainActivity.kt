package com.jarvis.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jarvis.assistant.service.JarvisService
import com.jarvis.assistant.ui.JarvisViewModel
import com.jarvis.assistant.ui.screens.AboutScreen
import com.jarvis.assistant.ui.screens.ChatScreen
import com.jarvis.assistant.ui.screens.HistoryScreen
import com.jarvis.assistant.ui.screens.HomeScreen
import com.jarvis.assistant.ui.screens.MemoryScreen
import com.jarvis.assistant.ui.screens.PrivacyScreen
import com.jarvis.assistant.ui.screens.ProvidersScreen
import com.jarvis.assistant.ui.screens.RemindersScreen
import com.jarvis.assistant.ui.screens.RoutinesScreen
import com.jarvis.assistant.ui.screens.SettingsScreen
import com.jarvis.assistant.ui.screens.TasksScreen
import com.jarvis.assistant.ui.screens.VoiceScreen
import com.jarvis.assistant.ui.screens.VoiceSettingsScreen
import com.jarvis.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JarvisTheme {
                JarvisRoot()
            }
        }
    }
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val drawerDestinations = listOf(
    Destination("home", "Home", Icons.Outlined.Home),
    Destination("chat", "Assistant", Icons.Outlined.ChatBubbleOutline),
    Destination("voice", "Voice", Icons.Outlined.Mic),
    Destination("tasks", "Tasks", Icons.Outlined.TaskAlt),
    Destination("reminders", "Reminders", Icons.Outlined.Alarm),
    Destination("routines", "Routines", Icons.Outlined.Repeat),
    Destination("memory", "Memory", Icons.Outlined.Psychology),
    Destination("history", "History", Icons.Outlined.History),
    Destination("providers", "AI models", Icons.Outlined.Cloud),
    Destination("voice_settings", "Voice & language", Icons.Outlined.Language),
    Destination("privacy", "Privacy & security", Icons.Outlined.Security),
    Destination("settings", "Settings", Icons.Outlined.Settings),
    Destination("about", "About Jarvis", Icons.Outlined.Info),
)

private val bottomDestinations = drawerDestinations.take(5).filter { it.route != "voice" }

@Composable
fun JarvisRoot(viewModel: JarvisViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissions()
        val action = pendingAfterPermission
        pendingAfterPermission = null
        action?.invoke()
    }

    fun requestPermissions(action: (() -> Unit)? = null) {
        pendingAfterPermission = action
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun openRoute(route: String) {
        navController.navigate(route) { launchSingleTop = true }
        scope.launch { drawerState.close() }
    }

    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                viewModel.refresh()
                viewModel.refreshPermissions()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(JarvisService.ACTION_REFRESH),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "JARVIS",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )
                drawerDestinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        selected = currentRoute == destination.route,
                        onClick = {
                            openRoute(destination.route)
                            if (destination.route == "providers") viewModel.refreshProviders()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { openRoute(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onOpenVoice = { openRoute("voice") },
                            onOpenChat = { openRoute("chat") },
                            onOpenTasks = { openRoute("tasks") },
                            onOpenReminders = { openRoute("reminders") },
                            onToggleWakeWord = { enabled ->
                                if (enabled && !state.micPermission) {
                                    requestPermissions { viewModel.setWakeWord(true) }
                                } else {
                                    viewModel.setWakeWord(enabled)
                                }
                            },
                            onTaskToggle = { viewModel.completeTask(it.id) },
                            onTaskSnooze = { viewModel.snoozeTask(it) },
                            onTaskDelete = { viewModel.deleteTask(it) },
                            onTaskTomorrow = { viewModel.moveTaskToTomorrow(it) },
                            onQuickAdd = { title, at, priority -> viewModel.addTask(title, at, priority) },
                            onRetryModels = { viewModel.retryModels() },
                            onBackendSetup = { openRoute("settings") },
                        )
                    }
                    composable("chat") {
                        ChatScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onSend = { viewModel.sendText(it) },
                            onOpenVoice = { openRoute("voice") },
                        )
                    }
                    composable("voice") {
                        VoiceScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onListen = {
                                if (!state.micPermission) {
                                    requestPermissions { viewModel.startListening() }
                                } else {
                                    viewModel.startListening()
                                }
                            },
                            onStop = { viewModel.stopListening() },
                            onSend = { viewModel.sendText(it) },
                            onOpenSettings = { openRoute("voice_settings") },
                        )
                    }
                    composable("tasks") {
                        TasksScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onAdd = { title, at, priority -> viewModel.addTask(title, at, priority) },
                            onToggle = { viewModel.completeTask(it.id) },
                            onSnooze = { viewModel.snoozeTask(it) },
                            onDelete = { viewModel.deleteTask(it) },
                            onTomorrow = { viewModel.moveTaskToTomorrow(it) },
                        )
                    }
                    composable("reminders") {
                        RemindersScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onAdd = { title, at, priority -> viewModel.addTask(title, at, priority) },
                            onToggle = { viewModel.completeTask(it.id) },
                            onSnooze = { viewModel.snoozeTask(it) },
                            onRequestExactAlarms = { requestExactAlarms(context) },
                        )
                    }
                    composable("routines") {
                        RoutinesScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onAdd = { name, hour, minute, days, prompt ->
                                viewModel.addRoutine(name, hour, minute, days, prompt)
                            },
                            onToggle = { routine, enabled -> viewModel.toggleRoutine(routine, enabled) },
                            onDelete = { viewModel.deleteRoutine(it) },
                            onAcceptSuggestion = { viewModel.acceptSuggestion(it) },
                        )
                    }
                    composable("memory") {
                        MemoryScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onAdd = { text, kind -> viewModel.saveMemory(text, kind) },
                            onUpdate = { id, text -> viewModel.updateMemory(id, text) },
                            onDelete = { viewModel.deleteMemory(it) },
                            onClearAll = { viewModel.clearMemories() },
                            onToggleLearning = { viewModel.setMemoryLearning(it) },
                            onToggleEncrypt = { viewModel.setEncryptMemories(it) },
                        )
                    }
                    composable("history") {
                        HistoryScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onClear = { viewModel.clearHistory() },
                        )
                    }
                    composable("providers") {
                        ProvidersScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onRefresh = { viewModel.refreshProviders() },
                        )
                    }
                    composable("voice_settings") {
                        VoiceSettingsScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onLanguage = { viewModel.setLanguage(it) },
                            onSpeakReplies = { viewModel.setSpeakReplies(it) },
                            onQuietDuringMedia = { viewModel.setQuietDuringMedia(it) },
                            onSpeechRate = { viewModel.setSpeechRate(it) },
                            onRetryModels = { viewModel.retryModels() },
                            onTestVoice = {
                                val sample = if (state.hindi) {
                                    "नमस्ते, मैं जार्विस हूँ। आपकी क्या सहायता कर सकता हूँ?"
                                } else {
                                    "Hello, I am Jarvis. How can I help you today?"
                                }
                                viewModel.speak(sample)
                            },
                        )
                    }
                    composable("privacy") {
                        PrivacyScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onRequestMic = { requestPermissions() },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                }
                            },
                            onRequestCalendar = {
                                permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_CALENDAR))
                            },
                            onRequestExactAlarms = { requestExactAlarms(context) },
                            onToggleLearning = { viewModel.setMemoryLearning(it) },
                            onToggleEncrypt = { viewModel.setEncryptMemories(it) },
                            onClearMemories = { viewModel.clearMemories() },
                            onClearAll = { viewModel.clearAllData() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            state = state,
                            onMenu = { scope.launch { drawerState.open() } },
                            onSaveBackend = { url, token -> viewModel.saveBackend(url, token) },
                            onSyncTasks = { viewModel.pushTasksToCloud() },
                            onFollowUpMinutes = { viewModel.setFollowUpMinutes(it) },
                            onOpenProviders = { openRoute("providers") },
                            onOpenVoice = { openRoute("voice_settings") },
                            onOpenPrivacy = { openRoute("privacy") },
                            onOpenAbout = { openRoute("about") },
                        )
                    }
                    composable("about") {
                        AboutScreen(state = state, onMenu = { scope.launch { drawerState.open() } })
                    }
                }
            }
        }
    }
}

private fun requestExactAlarms(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Suppress("unused")
private fun navigate(navController: NavHostController, route: String) {
    navController.navigate(route) { launchSingleTop = true }
}
