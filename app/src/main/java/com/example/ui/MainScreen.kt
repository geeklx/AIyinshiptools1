package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioRecord
import com.example.utils.AudioExtractor
import com.example.utils.VideoInfo
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.app.Activity
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Luxury Theme Palette
val BackgroundDark = Color(0xFF090D16)
val CardBackground = Color(0xFF131E35)
val SurfaceLighter = Color(0xFF1E2D4A)

val AccentCyan = Color(0xFF00F5FF)
val AccentCoral = Color(0xFFFF5A5F)
val SoftCyan = Color(0x3300F5FF)
val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val videoInfo by viewModel.selectedVideoInfo.collectAsStateWithLifecycle()
    val customFileName by viewModel.customFileName.collectAsStateWithLifecycle()
    val selectedFormat by viewModel.selectedFormat.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val extractionProgress by viewModel.extractionProgress.collectAsStateWithLifecycle()
    val audioRecords by viewModel.audioRecords.collectAsStateWithLifecycle()

    val activePlayingRecord by viewModel.activePlayingRecord.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val playbackDuration by viewModel.playbackDuration.collectAsStateWithLifecycle()

    val prefs = remember { context.getSharedPreferences("app_privacy_prefs", Context.MODE_PRIVATE) }
    var hasAcceptedPrivacy by remember { mutableStateOf(prefs.getBoolean("accepted_privacy", false)) }
    var showFullTextType by remember { mutableIntStateOf(0) } // 0 = closed, 1 = Privacy Policy, 2 = Terms of Service
    var showAboutDialog by remember { mutableStateOf(false) }

    // File selection launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectVideo(context, uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundDark, Color(0xFF06090F))
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Elegant Header
            HeaderSection(
                recordsCount = audioRecords.size,
                onInfoClick = { showAboutDialog = true }
            )

            // Scrollable workspace combining extractor panel and history
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If a video has been selected, show customizer/extraction dashboard
                if (videoInfo != null) {
                    item {
                        ExtractionPanel(
                            videoInfo = videoInfo!!,
                            customFileName = customFileName,
                            selectedFormat = selectedFormat,
                            extractionState = extractionState,
                            extractionProgress = extractionProgress,
                            onFileNameChange = { viewModel.setCustomFileName(it) },
                            onFormatSelect = { viewModel.setSelectedFormat(it) },
                            onExtractClick = {
                                keyboardController?.hide()
                                viewModel.startExtraction(context)
                            },
                            onCancelClick = {
                                viewModel.cancelExtraction()
                            },
                            onClearClick = {
                                viewModel.clearActiveSelection()
                            }
                        )
                    }
                } else {
                    // Empty state showing active buttons setup
                    item {
                        ImportVideoCard(onImportClick = {
                            videoPickerLauncher.launch("video/*")
                        })
                    }
                }

                // Show State Indicator if processing/success
                if (extractionState is ExtractionState.Error) {
                    item {
                        ErrorAlert(
                            message = (extractionState as ExtractionState.Error).message,
                            onDismiss = { viewModel.clearActiveSelection() }
                        )
                    }
                }

                if (extractionState is ExtractionState.Success) {
                    val successRecord = (extractionState as ExtractionState.Success).record
                    item {
                        SuccessAlert(
                            record = successRecord,
                            onPlayClick = {
                                viewModel.playAudio(successRecord)
                                viewModel.clearActiveSelection()
                            },
                            onDismiss = { viewModel.clearActiveSelection() }
                        )
                    }
                }

                // Divider and title for history
                item {
                    Text(
                        text = "提取历史记录 (${audioRecords.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (audioRecords.isEmpty()) {
                    item {
                        EmptyHistoryPlaceholder()
                    }
                } else {
                    items(audioRecords, key = { it.id }) { record ->
                        HistoryItemCard(
                            record = record,
                            isActive = activePlayingRecord?.id == record.id,
                            isPlaying = activePlayingRecord?.id == record.id && playbackState == PlaybackState.Playing,
                            onPlayToggle = {
                                if (activePlayingRecord?.id == record.id) {
                                    if (playbackState == PlaybackState.Playing) viewModel.pauseAudio()
                                    else viewModel.playAudio(record)
                                } else {
                                    viewModel.playAudio(record)
                                }
                            },
                            onDelete = {
                                viewModel.deleteRecord(context, record)
                                Toast.makeText(context, "已删除提取记录", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                viewModel.shareFile(context, record)
                            },
                            onSaveToDownloads = {
                                viewModel.saveToDownloads(context, record) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // Floating Quick Audio Player
        AnimatedVisibility(
            visible = activePlayingRecord != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            if (activePlayingRecord != null) {
                QuickPlayerPanel(
                    record = activePlayingRecord!!,
                    playbackState = playbackState,
                    positionMs = playbackPosition,
                    durationMs = playbackDuration,
                    onPauseToggle = {
                        if (playbackState == PlaybackState.Playing) viewModel.pauseAudio()
                        else viewModel.playAudio(activePlayingRecord!!)
                    },
                    onStop = {
                        viewModel.stopAudio()
                    },
                    onSeek = { position ->
                        viewModel.seekTo(position)
                    },
                    onShare = {
                        viewModel.shareFile(context, activePlayingRecord!!)
                    },
                    onExport = {
                        viewModel.saveToDownloads(context, activePlayingRecord!!) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }

        // Privacy Consent Dialog (Locks app workspace until agreed)
        if (!hasAcceptedPrivacy) {
            PrivacyConsentDialog(
                onAgree = {
                    prefs.edit().putBoolean("accepted_privacy", true).apply()
                    hasAcceptedPrivacy = true
                },
                onDecline = {
                    findActivity(context)?.finish()
                },
                onReadPrivacy = { showFullTextType = 1 },
                onReadTerms = { showFullTextType = 2 }
            )
        }

        // Full Text Content Dialog (Overlay for Privacy terms / User terms)
        if (showFullTextType != 0) {
            FullTextPolicyDialog(
                title = if (showFullTextType == 1) "《隐私政策》" else "《用户服务协议》",
                content = if (showFullTextType == 1) PrivacyPolicyContent.privacyPolicyText else PrivacyPolicyContent.userAgreementText,
                onClose = { showFullTextType = 0 }
            )
        }

        // In-app About Dialog
        if (showAboutDialog) {
            AboutAndPrivacyDialog(
                onReadPrivacy = { showFullTextType = 1 },
                onReadTerms = { showFullTextType = 2 },
                onClose = { showAboutDialog = false }
            )
        }
    }
}

@Composable
fun HeaderSection(recordsCount: Int, onInfoClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SoundWave logo mock via Canvas drawing
                Canvas(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentCoral.copy(0.15f))
                        .border(1.dp, AccentCoral.copy(0.3f), CircleShape)
                ) {
                    val bars = 5
                    val spacing = size.width / (bars + 1)
                    val barWidth = 3.dp.toPx()
                    for (i in 0 until bars) {
                        val x = spacing * (i + 1)
                        val height = when (i) {
                            0, 4 -> size.height * 0.35f
                            1, 3 -> size.height * 0.65f
                            else -> size.height * 0.85f
                        }
                        val yStart = (size.height - height) / 2
                        val yEnd = yStart + height
                        drawLine(
                            color = AccentCoral,
                            start = Offset(x, yStart),
                            end = Offset(x, yEnd),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "视频音频提取器",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "无损音轨秒级提取 · 支持 MP3 & M4A",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentCyan,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceLighter.copy(0.4f))
                        .testTag("info_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "关于与隐私政策",
                        tint = AccentCyan
                    )
                }
            }
        }
    }
}

@Composable
fun ImportVideoCard(onImportClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onImportClick() }
            .testTag("import_video_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, Brush.sweepGradient(listOf(AccentCyan.copy(0.3f), AccentCoral.copy(0.2f), AccentCyan.copy(0.3f)))),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Video-to-audio visual representation
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SoftCyan)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "导入视频",
                    tint = AccentCyan,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "导入视频以提取音频",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "支持 MP4, MKV, AVI, 3GP 等格式视频\n自动分析无损提取，保留极致音质",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    lineHeight = 18.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier.testTag("select_video_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BackgroundDark,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(90f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "选择视频文件",
                    color = BackgroundDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun ExtractionPanel(
    videoInfo: VideoInfo,
    customFileName: String,
    selectedFormat: String,
    extractionState: ExtractionState,
    extractionProgress: Float,
    onFileNameChange: (String) -> Unit,
    onFormatSelect: (String) -> Unit,
    onExtractClick: () -> Unit,
    onCancelClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val isExtracting = extractionState is ExtractionState.Processing

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp), clip = false)
            .testTag("extraction_panel"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, SurfaceLighter),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Selection header with close button to release selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentCoral)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "待处理视频信息",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = AccentCoral,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                IconButton(
                    onClick = onClearClick,
                    enabled = !isExtracting,
                    modifier = Modifier.size(28.dp).testTag("clear_selection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "取消选择",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Video Details Item Block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BackgroundDark.copy(0.4f))
                    .border(1.dp, SurfaceLighter.copy(0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon representation of Video
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentCoral.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VIDEO",
                        color = AccentCoral,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = videoInfo.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = videoInfo.sizeText,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .background(TextSecondary)
                        )
                        Text(
                            text = videoInfo.durationText,
                            style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .background(TextSecondary)
                        )
                        Text(
                            text = "编码: ${videoInfo.audioCodec ?: "N/A"}",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Configuration Options (Only shown when NOT active extracting)
            AnimatedVisibility(
                visible = !isExtracting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        text = "导出音频文件名:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = customFileName,
                        onValueChange = onFileNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("file_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = SurfaceLighter,
                            focusedContainerColor = BackgroundDark.copy(0.2f),
                            unfocusedContainerColor = BackgroundDark.copy(0.2f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        placeholder = { Text("请输入音频文件名称", color = TextSecondary.copy(0.4f)) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "导出音频格式:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Stylized Custom Format Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BackgroundDark.copy(0.4f))
                            .border(1.dp, SurfaceLighter, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("MP3", "M4A").forEach { fmt ->
                            val isSelected = selectedFormat == fmt
                            val colorScheme = if (fmt == "MP3") AccentCyan else AccentCoral
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colorScheme.copy(0.15f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) colorScheme.copy(0.8f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onFormatSelect(fmt) }
                                    .padding(vertical = 10.dp)
                                    .testTag("format_tab_$fmt"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (fmt == "MP3") "$fmt (超级兼容)" else "$fmt (高保真 AAC)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (isSelected) colorScheme else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onExtractClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("extract_submit_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "提取",
                            tint = BackgroundDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "立即提取音频 (.${selectedFormat.lowercase(Locale.getDefault())})",
                            color = BackgroundDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Extracting progress layout
            AnimatedVisibility(
                visible = isExtracting,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "正在极速提取音频中...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(SurfaceLighter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = extractionProgress)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentCyan, AccentCyan.copy(0.6f))
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.getDefault(), "进度: %.0f%%", extractionProgress * 100),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "无损高速打包",
                            fontSize = 11.sp,
                            color = AccentCyan.copy(0.7f),
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = onCancelClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCoral),
                        border = BorderStroke(1.dp, AccentCoral.copy(0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("cancel_extraction_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "取消", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "取消提取", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorAlert(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("error_alert"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AccentCoral.copy(0.1f)),
        border = BorderStroke(1.dp, AccentCoral.copy(0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "错误",
                tint = AccentCoral,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentCoral.copy(0.15f))
                    .padding(2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "提取出错",
                    fontWeight = FontWeight.Bold,
                    color = AccentCoral,
                    fontSize = 14.sp
                )
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "关闭",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SuccessAlert(record: AudioRecord, onPlayClick: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("success_alert"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(0.08f)),
        border = BorderStroke(1.dp, AccentCyan.copy(0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "成功",
                    tint = AccentCyan,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(0.15f))
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "音频提取成功！",
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "关闭",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "生成文件名: ${record.title}",
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(text = "大小: ${record.fileSize}", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "格式: ${record.format}", color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp).testTag("play_success_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = BackgroundDark, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "立即播放", color = BackgroundDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = BorderStroke(1.dp, SurfaceLighter),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(text = "好的", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Simple clean Vinyl path outline
        Canvas(modifier = Modifier.size(60.dp)) {
            drawCircle(
                color = SurfaceLighter,
                radius = size.minDimension / 2,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = SurfaceLighter.copy(0.5f),
                radius = size.minDimension / 4,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = AccentCoral,
                radius = size.minDimension * 0.08f
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "暂无提取记录",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "您提取的音频文件将会显示在这里，可进行导出、分享和播放",
            color = TextSecondary.copy(0.6f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 30.dp, end = 30.dp, top = 4.dp)
        )
    }
}

@Composable
fun HistoryItemCard(
    record: AudioRecord,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit
) {
    val dateText = remember(record.timestamp) {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${record.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) CardBackground else CardBackground.copy(0.6f)
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) AccentCyan.copy(0.4f) else SurfaceLighter.copy(0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Style badge representing format (glowing coral/cyan block)
                val formatColor = if (record.format == "MP3") AccentCyan else AccentCoral
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(formatColor.copy(0.1f))
                        .border(1.dp, formatColor.copy(0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = record.format,
                        color = formatColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isActive) AccentCyan else TextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = record.fileSize,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                        Box(
                            modifier = Modifier
                                .size(2.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .background(TextSecondary)
                        )
                        Text(
                            text = record.durationText,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        )
                        Box(
                            modifier = Modifier
                                .size(2.dp)
                                .align(Alignment.CenterVertically)
                                .clip(CircleShape)
                                .background(TextSecondary)
                        )
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary.copy(0.7f))
                        )
                    }
                }

                // Small sleek customized Play Toggle button
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isActive) AccentCyan else SurfaceLighter)
                        .testTag("item_play_button_${record.id}")
                ) {
                    PlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = if (isActive) BackgroundDark else TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = SurfaceLighter.copy(0.3f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Action lists
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary indicators: Original video source name
                Text(
                    text = "源自: ${record.originalVideoName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary.copy(0.5f)
                    ),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Actions row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Export to public folder
                    IconButton(
                        onClick = onSaveToDownloads,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_save_downloads_${record.id}")
                    ) {
                        // Arrow down represents export (standard save/download)
                        Icon(
                            imageVector = Icons.Default.ArrowBack, // Will rotate as downward
                            contentDescription = "导出至下载文件夹",
                            tint = AccentCyan.copy(0.8f),
                            modifier = Modifier.size(16.dp).rotate(270f)
                        )
                    }

                    // Share
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_share_record_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_delete_record_${record.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = AccentCoral.copy(0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPlayerPanel(
    record: AudioRecord,
    playbackState: PlaybackState,
    positionMs: Long,
    durationMs: Long,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    // Rotation animation for spinning CD mockup when playing
    val transition = rememberInfiniteTransition(label = "CDRotation")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CDAnimation"
    )

    val currentRotation = if (playbackState == PlaybackState.Playing) rotationAngle else 0f

    val formattedPosition = remember(positionMs) {
        AudioExtractor.formatDuration(positionMs)
    }
    val formattedDuration = remember(durationMs) {
        AudioExtractor.formatDuration(durationMs)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, SurfaceLighter),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Player Top: Active name and basic actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spinning vinyl-mock disk
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .rotate(currentRotation)
                        .border(1.dp, SurfaceLighter, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Small vinyl layout
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color(0xFF1E293B), radius = size.minDimension*0.4f)
                        drawCircle(color = AccentCyan, radius = size.minDimension*0.12f)
                    }
                    Icon(
                        imageVector = Icons.Default.Add, // Simple central cross/plus mock
                        contentDescription = null,
                        tint = BackgroundDark,
                        modifier = Modifier.size(8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "正在播放 · ${record.format}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentCyan,
                            fontSize = 10.sp
                        )
                    )
                }

                // Controls row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, // Rotated for save
                            contentDescription = "导出",
                            tint = AccentCyan,
                            modifier = Modifier.size(16.dp).rotate(270f)
                        )
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(28.dp).testTag("player_close_button")) {
                        Icon(Icons.Default.Clear, contentDescription = "关闭播放器", tint = AccentCoral, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Player progress bar & elapsed times
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedPosition,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )

                val safeDuration = maxOf(1L, durationMs)
                val safePosition = positionMs.coerceIn(0L, safeDuration)
                Slider(
                    value = safePosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = SurfaceLighter,
                        thumbColor = AccentCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .testTag("player_progress_slider")
                )

                Text(
                    text = formattedDuration,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Central control area: play toggle & dynamic wave visualization
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Wave visualizer simulation Canvas
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(28.dp)
                ) {
                    WaveVisualizer(isPlaying = playbackState == PlaybackState.Playing)
                }

                // Play/Pause button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentCyan)
                            .testTag("player_play_pause_button")
                    ) {
                        PlayPauseIcon(
                            isPlaying = playbackState == PlaybackState.Playing,
                            tint = BackgroundDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WaveVisualizer(isPlaying: Boolean) {
    val barCount = 18
    val waveAmplitudes = remember { List(barCount) { (4..24).random() } }
    
    // Wave animation shifts heights dynamically when playing
    val infiniteTransition = rememberInfiniteTransition(label = "waveShift")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAnimation"
    )

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val width = size.width
        val height = size.height
        val barWidth = 4.dp.toPx()
        val spacing = (width - (barWidth * barCount)) / (barCount - 1)

        for (i in 0 until barCount) {
            val customOffset = (i.toFloat() / barCount.toFloat()) * 2f * Math.PI.toFloat()
            val animatedFactor = if (isPlaying) {
                Math.abs(Math.sin((phaseShift + customOffset).toDouble())).toFloat()
            } else {
                0.2f
            }
            
            val amplitude = waveAmplitudes[i].dp.toPx()
            val minHeight = 4.dp.toPx()
            val currentBarHeight = if (height <= minHeight) {
                height
            } else {
                (amplitude * animatedFactor).coerceIn(minHeight, height)
            }
            
            val x = i * (barWidth + spacing)
            val y = (height - currentBarHeight) / 2

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(AccentCyan, AccentCyan.copy(0.5f))
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, currentBarHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun PlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    if (isPlaying) {
        Row(
            modifier = modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint)
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "播放",
            tint = tint,
            modifier = modifier
        )
    }
}

@Composable
fun FullTextPolicyDialog(
    title: String,
    content: String,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, SurfaceLighter), RoundedCornerShape(24.dp)),
        containerColor = CardBackground,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SurfaceLighter)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("policy_close_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "返回",
                        color = BackgroundDark,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    )
}

@Composable
fun PrivacyConsentDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onReadPrivacy: () -> Unit,
    onReadTerms: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Force action */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, SurfaceLighter), RoundedCornerShape(24.dp)),
        containerColor = CardBackground,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "用户隐私与服务协议提示",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "欢迎使用“视频音频提取器”！为了保障您的合法权益，请您在开始使用前仔细阅读我们的协议内容。\n\n" +
                            "1. 本软件是一款完全离线的纯本地工具，所有的视频读取及音轨提取均直接在您的设备中进行，绝不上传您的媒体文件，充分保障隐私安全性。\n" +
                            "2. 我们需要访问您选择的存储/媒体文件权限，以实现音轨加载及音频保存在本地 Downloads 目录核心服务。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "请点击阅读：",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "《隐私政策》",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.clickable { onReadPrivacy() }
                    )
                    Text(
                        text = " 与 ",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Text(
                        text = "《用户服务协议》",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.clickable { onReadTerms() }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "若您同意以上全部内容，请点击“同意并继续”开始提取音轨。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary.copy(0.8f),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("privacy_agree_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "同意并继续",
                        color = BackgroundDark,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("privacy_decline_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, SurfaceLighter),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "不同意并退出",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    )
}

@Composable
fun AboutAndPrivacyDialog(
    onReadPrivacy: () -> Unit,
    onReadTerms: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, SurfaceLighter), RoundedCornerShape(24.dp)),
        containerColor = CardBackground,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // SoundWave Animated logo in miniature
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentCoral.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCoral,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "视频音频提取器",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    text = "版本 v1.0.0",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = AccentCyan
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "关于服务：\n本软件致力于提供极致纯净、不限速且隐私安全的音轨提取工具。所有的音视频流解析模块底座全部在本地离线装配跑通，不消耗云端流量，不读取用户个人隐私数据。",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SurfaceLighter)
                )
                
                // Item: Read Privacy Policy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceLighter.copy(0.4f))
                        .clickable { onReadPrivacy() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "阅读《隐私政策》",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                    )
                }
                
                // Item: Read Terms of Service
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceLighter.copy(0.4f))
                        .clickable { onReadTerms() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "阅读《用户服务协议》",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(SurfaceLighter)
                )
                
                // Support section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceLighter.copy(0.3f))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "客服与保障支持",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "liangxiaogeek6@gmail.com",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Support Email", "liangxiaogeek6@gmail.com")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "邮箱复制成功！", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(0.15f)),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("一键复制邮箱", color = AccentCyan, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("about_close_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceLighter),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "关闭",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    )
}

private fun findActivity(context: Context): Activity? {
    var currentContext = context
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

