package com.example.cleanchecknative.ui.results

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cleanchecknative.data.db.WashResultEntity
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import java.text.SimpleDateFormat
import java.util.*

class ResultListActivity : ComponentActivity() {

    private val viewModel: ResultListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            CleanCheckNativeTheme {
                val results by viewModel.allResults.collectAsState()
                val backupProgress by viewModel.backupProgress.collectAsState()
                val isBackupRunning by viewModel.isBackupRunning.collectAsState()

                ResultListScreen(
                    results = results,
                    backupProgress = backupProgress,
                    isBackupRunning = isBackupRunning,
                    onBackupClick = { viewModel.startBackup() },
                    onDeleteClick = { result -> viewModel.deleteResult(result) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultListScreen(
    results: List<WashResultEntity>,
    backupProgress: Int,
    isBackupRunning: Boolean,
    onBackupClick: () -> Unit,
    onDeleteClick: (WashResultEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("분석 결과 목록") },
                actions = {
                    IconButton(onClick = onBackupClick, enabled = !isBackupRunning) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "전체 백업")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AnimatedVisibility(visible = isBackupRunning) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = backupProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "백업 진행 중... ($backupProgress%)",
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (results.isEmpty()) {
                    item {
                        Text(
                            text = "저장된 분석 결과가 없습니다.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(results) { result ->
                        ResultItemCard(result, onDelete = { onDeleteClick(result) })
                    }
                }
            }
        }
    }
}

@Composable
fun ResultItemCard(result: WashResultEntity, onDelete: () -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault())
    val dateString = sdf.format(Date(result.timestamp))
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${result.userName} (${result.userAge}세)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (result.isUploaded) Icons.Default.CloudDone else Icons.Default.Cloud,
                            contentDescription = "Upload Status",
                            tint = if (result.isUploaded) Color(0xFF4CAF50) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "분석 시간: $dateString", style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = {
                                onDelete()
                                showMenu = false
                            }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow("진행 시간:", "${result.elapsedTime}초")
            InfoRow("전체 진행률:", "${(result.totalProgress * 100).toInt()}%")
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("세척된 키포인트 번호", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    CleansedKeypointsRow("  - 왼손 손바닥:", result.cleansedLeftPalm)
                    CleansedKeypointsRow("  - 오른손 손바닥:", result.cleansedRightPalm)
                    CleansedKeypointsRow("  - 왼손 손등:", result.cleansedLeftBack)
                    CleansedKeypointsRow("  - 오른손 손등:", result.cleansedRightBack)

                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("파일 경로", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    InfoRow("  - 영상:", result.videoPath ?: "없음")
                    InfoRow("  - 스크린샷:", result.screenshotPath ?: "없음")
                    InfoRow("  - 결과 파일:", result.metadataPath ?: "없음")

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        result.videoPath?.let { path ->
                            Button(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(path), "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "영상을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("영상 보기")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (isExpanded) "간략히 보기" else "상세보기")
            }
        }
    }
}

@Composable
fun CleansedKeypointsRow(label: String, data: String) {
    val cleansedIndices = data.split(',')
        .mapIndexedNotNull { index, s -> if (s.toBoolean()) index else null }
        .joinToString(", ")

    InfoRow(label, if (cleansedIndices.isNotEmpty()) cleansedIndices else "없음")
}

@Composable
fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp)
        )
        Text(text = value)
    }
}
