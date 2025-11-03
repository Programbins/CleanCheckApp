package com.example.cleanchecknative.ui.user

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import com.example.cleanchecknative.data.db.UserEntity
import com.example.cleanchecknative.ui.register.RegistrationActivity
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import java.io.File

class UserDetailsActivity : ComponentActivity() {

    private val viewModel: UserDetailsViewModel by viewModels()

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_EMBEDDING = "extra_embedding"
        const val EXTRA_USER_ID = "extra_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupImmersiveMode()

        val userId = intent.getStringExtra(EXTRA_USER_ID)
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val embedding = intent.getFloatArrayExtra(EXTRA_EMBEDDING)

        if (userId == null && (imagePath == null || embedding == null)) {
            Toast.makeText(this, "사용자 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (userId != null) {
            viewModel.loadUser(userId)
        }

        setContent {
            CleanCheckNativeTheme {
                val user by viewModel.user.collectAsState()
                UserDetailsScreen(
                    user = user,
                    imagePath = imagePath ?: user?.photoPath,
                    onSave = { name, age, gender ->
                        viewModel.saveOrUpdateUser(userId, name, age, gender, imagePath ?: user!!.photoPath, embedding)
                        val message = if (userId == null) "사용자가 등록되었습니다." else "사용자 정보가 수정되었습니다."
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onRetakePhoto = {
                        startActivity(Intent(this, RegistrationActivity::class.java))
                        finish()
                    },
                    onGoToMainMenu = {
                        finish()
                    }
                )
            }
        }
    }

    private fun setupImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsScreen(
    user: UserEntity?,
    imagePath: String?,
    onSave: (String, Int, String) -> Unit,
    onRetakePhoto: () -> Unit,
    onGoToMainMenu: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("남성") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(user) {
        if (user != null) {
            name = user.name
            age = user.age.toString()
            gender = user.gender
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(), // Apply padding here to push content up
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CleanCheck",
                color = Color(0xFF4F83EB),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
            )

            // This Column makes the Card scrollable if the screen gets too small
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .width(400.dp)
                        .padding(vertical = 24.dp), // Add padding to ensure space on small screens
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (user == null) "사용자 등록" else "사용자 정보 수정",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Circular Photo
                        if (imagePath != null) {
                            Image(
                                painter = rememberAsyncImagePainter(File(imagePath)),
                                contentDescription = "User Photo",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .scale(scaleX = -1f, scaleY = 1f),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // Name
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("이름") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Age
                        OutlinedTextField(
                            value = age,
                            onValueChange = { age = it.filter { c -> c.isDigit() } },
                            label = { Text("나이") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Gender
                        GenderSelector(
                            selectedGender = gender,
                            onGenderSelected = { gender = it }
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        // Save Button
                        Button(
                            onClick = {
                                val ageInt = age.toIntOrNull()
                                if (name.isNotBlank() && ageInt != null && gender.isNotBlank()) {
                                    onSave(name, ageInt, gender)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "모든 정보를 올바르게 입력해주세요.",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("저장", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Text Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "사진 다시찍기",
                                modifier = Modifier.clickable { onRetakePhoto() },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = " | ",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "메인메뉴 돌아가기",
                                modifier = Modifier.clickable { onGoToMainMenu() },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenderSelector(selectedGender: String, onGenderSelected: (String) -> Unit) {
    val genders = listOf("남성", "여성")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("성별", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            genders.forEach { gender ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onGenderSelected(gender) }
                        .padding(horizontal = 8.dp)
                ) {
                    RadioButton(
                        selected = (gender == selectedGender),
                        onClick = { onGenderSelected(gender) }
                    )
                    Text(
                        text = gender,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
