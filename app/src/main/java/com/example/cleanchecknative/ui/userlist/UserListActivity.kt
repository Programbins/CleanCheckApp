package com.example.cleanchecknative.ui.userlist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import com.example.cleanchecknative.data.db.UserEntity
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import com.example.cleanchecknative.ui.user.UserDetailsActivity
import java.io.File

class UserListActivity : ComponentActivity() {

    private val viewModel: UserListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Start: Fullscreen & Immersive Mode ---
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // --- End: Fullscreen & Immersive Mode ---

        setContent {
            CleanCheckNativeTheme {
                val users by viewModel.users.collectAsState()
                UserListScreen(
                    users = users,
                    onDeleteUser = { user -> viewModel.deleteUser(user) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUsers()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    users: List<UserEntity>,
    onDeleteUser: (UserEntity) -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<UserEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("등록된 사용자 목록") })
        }
    ) { padding ->
        if (users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("등록된 사용자가 없습니다.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(users) { user ->
                    UserListItem(
                        user = user,
                        onEditClick = {
                            val intent = Intent(context, UserDetailsActivity::class.java).apply {
                                putExtra(UserDetailsActivity.EXTRA_USER_ID, user.id)
                            }
                            context.startActivity(intent)
                        },
                        onDeleteClick = { showDeleteDialog = user }
                    )
                }
            }
        }

        showDeleteDialog?.let { user ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("사용자 삭제") },
                text = { Text("${user.name} 님을 목록에서 삭제하시겠습니까?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteUser(user)
                            showDeleteDialog = null
                        }
                    ) {
                        Text("삭제")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteDialog = null }) {
                        Text("취소")
                    }
                }
            )
        }
    }
}

@Composable
fun UserListItem(
    user: UserEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = rememberAsyncImagePainter(File(user.photoPath)),
                    contentDescription = "User Photo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .align(Alignment.Center),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("수정") }, onClick = onEditClick)
                        DropdownMenuItem(text = { Text("삭제") }, onClick = onDeleteClick)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${user.age}세 / ${user.gender}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
