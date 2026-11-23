package com.moyu.novel

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.moyu.novel.ui.ChatBody
import com.moyu.novel.ui.ChatTopBar
import com.moyu.novel.ui.Composer
import com.moyu.novel.ui.DrawerContent
import com.moyu.novel.ui.HistorySheet
import com.moyu.novel.ui.ImportScreen
import com.moyu.novel.ui.LocalModelManagerSheet
import com.moyu.novel.ui.MemorySheet
import com.moyu.novel.ui.ModeSheet
import com.moyu.novel.ui.ModelHubScreen
import com.moyu.novel.ui.ModelPickerSheet
import com.moyu.novel.ui.MoyuBg
import com.moyu.novel.ui.MoyuTheme
import com.moyu.novel.ui.SettingsScreen
import com.moyu.novel.ui.SimpleTopBar
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            MoyuTheme {
                MoyuRoot(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.flush()
    }
}

@Composable
fun MoyuRoot(vm: ChatViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val screen = vm.screen
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(Unit) {
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                missing += Manifest.permission.READ_MEDIA_IMAGES
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                missing += Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(vm.toast) {
        val message = vm.toast
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.clearToast()
        }
    }

    BackHandler(enabled = screen != Screen.CHAT || drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            vm.goTo(Screen.CHAT)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = screen == Screen.CHAT,
        drawerContent = { DrawerContent(vm, drawerState) },
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MoyuBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                when (screen) {
                    Screen.CHAT -> ChatTopBar(vm) {
                        scope.launch { drawerState.open() }
                    }
                    Screen.MODELS -> SimpleTopBar("模型库") { vm.goTo(Screen.CHAT) }
                    Screen.SETTINGS -> SimpleTopBar("设置") { vm.goTo(Screen.CHAT) }
                    Screen.IMPORT -> SimpleTopBar("导入聊天记录") { vm.goTo(Screen.CHAT) }
                }
            },
            bottomBar = {
                if (screen == Screen.CHAT) {
                    Composer(vm)
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.CHAT -> ChatBody(vm)
                    Screen.MODELS -> ModelHubScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                    Screen.IMPORT -> ImportScreen(vm)
                }
            }
        }
    }

    if (vm.showModelPicker) {
        ModelPickerSheet(vm)
    }
    if (vm.showHistorySheet) {
        HistorySheet(vm)
    }
    if (vm.showMemorySheet) {
        MemorySheet(vm)
    }
    if (vm.showLocalModelManager) {
        LocalModelManagerSheet(vm)
    }
    if (vm.showModeSheet) {
        ModeSheet(vm)
    }
}
