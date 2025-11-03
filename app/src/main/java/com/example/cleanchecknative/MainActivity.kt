package com.example.cleanchecknative

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cleanchecknative.ui.recognize.RecognizeUserActivity
import com.example.cleanchecknative.ui.register.RegistrationActivity
import com.example.cleanchecknative.ui.results.ResultListActivity
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import com.example.cleanchecknative.ui.userlist.UserListActivity
import kotlin.system.exitProcess

// Kiosk mode password
private const val KIOSK_MODE_PASSWORD = "0802"

// Enum to represent protected actions
enum class ProtectedAction {
    VERIFY_USER, DATA, EXIT
}

class MainActivity : ComponentActivity() {

    private var onPermissionGranted: (() -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                onPermissionGranted?.invoke()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setupImmersiveMode()

        setContent {
            var pendingAction by remember { mutableStateOf<ProtectedAction?>(null) }

            CleanCheckNativeTheme {
                MainMenuScreen(
                    onRegisterClick = {
                        checkCameraPermissionAndNavigate {
                            startActivity(Intent(this, RegistrationActivity::class.java))
                        }
                    },
                    onWashClick = {
                        checkCameraPermissionAndNavigate {
                            startActivity(Intent(this, RecognizeUserActivity::class.java))
                        }
                    },
                    onProtectedAction = { action ->
                        pendingAction = action
                    }
                )

                if (pendingAction != null) {
                    PasswordDialog(
                        onDismiss = { pendingAction = null },
                        onConfirm = { password ->
                            if (password == KIOSK_MODE_PASSWORD) {
                                when (pendingAction) {
                                    ProtectedAction.VERIFY_USER -> startActivity(Intent(this, UserListActivity::class.java))
                                    ProtectedAction.DATA -> startActivity(Intent(this, ResultListActivity::class.java))
                                    ProtectedAction.EXIT -> {
                                        stopLockTask()
                                        exitProcess(0)
                                    }
                                    null -> {}
                                }
                            } else {
                                Toast.makeText(this, "비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                            }
                            pendingAction = null
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startLockTask()
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun checkCameraPermissionAndNavigate(onGranted: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                onGranted()
            }
            else -> {
                this.onPermissionGranted = onGranted
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

data class MenuItem(
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit
)

@Composable
fun MainMenuScreen(
    onRegisterClick: () -> Unit,
    onWashClick: () -> Unit,
    onProtectedAction: (ProtectedAction) -> Unit
) {
    val context = LocalContext.current
    val menuItems = mapOf(
        "register" to MenuItem(Icons.Default.AppRegistration, "사용자 등록", onRegisterClick),
        "homepage" to MenuItem(Icons.Default.Language, "홈페이지", {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cleancheck.org"))
            context.startActivity(intent)
        }),
        "verify" to MenuItem(Icons.Default.FormatListBulleted, "사용자 확인") { onProtectedAction(ProtectedAction.VERIFY_USER) },
        "data" to MenuItem(Icons.Default.Analytics, "데이터 확인") { onProtectedAction(ProtectedAction.DATA) },
        "exit" to MenuItem(Icons.Default.ExitToApp, "끝내기") { onProtectedAction(ProtectedAction.EXIT) }
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Block
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                        .clickable(onClick = onWashClick),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CleanCheck",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F83EB),
                            fontSize = 72.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    MenuCard(item = menuItems.getValue("verify"), modifier = Modifier.weight(1f).fillMaxHeight())
                    MenuCard(item = menuItems.getValue("data"), modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            // Right Block
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    MenuCard(item = menuItems.getValue("register"), modifier = Modifier.weight(1f).fillMaxWidth())
                    MenuCard(item = menuItems.getValue("homepage"), modifier = Modifier.weight(1f).fillMaxWidth())
                }
                MenuCard(item = menuItems.getValue("exit"), modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
fun MenuCard(item: MenuItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = item.action),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("비밀번호 입력") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Preview(showBackground = true, device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun MainMenuScreenPreview() {
    CleanCheckNativeTheme {
        MainMenuScreen(onRegisterClick = {}, onWashClick = {}, onProtectedAction = {})
    }
}