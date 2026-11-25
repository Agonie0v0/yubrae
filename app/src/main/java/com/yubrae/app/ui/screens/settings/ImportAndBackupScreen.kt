package com.yubrae.app.ui.screens.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.contract.RemoteBackupFile
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.CompactSwitchRow
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.viewmodel.KairoMainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportAndBackupScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = KairoTheme.semantic

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val remoteBackups by viewModel.remoteBackups.collectAsStateWithLifecycle()
    val webDavStatus by viewModel.webDavStatusMessage.collectAsStateWithLifecycle()
    val csvDialog by viewModel.csvImportDialog.collectAsStateWithLifecycle()

    var webDavUrl by remember(settings.webDavUrl) { mutableStateOf(settings.webDavUrl) }
    var webDavUsername by remember(settings.webDavUsername) { mutableStateOf(settings.webDavUsername) }
    var webDavPassword by remember(settings.webDavPassword) { mutableStateOf(settings.webDavPassword) }
    var webDavRemoteDir by remember(settings.webDavRemoteDir) { mutableStateOf(settings.webDavRemoteDir) }
    var backupPassphrase by remember(settings.backupPassphrase) { mutableStateOf(settings.backupPassphrase) }
    var showPassword by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }

    var pendingExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingLocalRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restoreRemoteCandidate by remember { mutableStateOf<RemoteBackupFile?>(null) }
    var showExportPassphrasePrompt by remember { mutableStateOf(false) }

    // 1. SAF / GetContent 双通道选择多格式账单文件导入
    val handleBillUriPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                val fileName = resolveDisplayName(context, uri)?.takeIf { it.isNotBlank() } ?: "bill_import"
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) {
                    viewModel.previewCsvImport(fileName, bytes)
                } else {
                    viewModel.showMessage("无法读取所选账单文件")
                }
            }
        }
    }
    val csvOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = handleBillUriPicked
    )
    val csvFallbackOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = handleBillUriPicked
    )

    // 2. SAF 创建本地 .klbk 加密备份文件
    val klbkCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val bytes = pendingExportBytes
        pendingExportBytes = null
        if (uri != null && bytes != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("无法打开目标文件输出流")
                        true
                    }.getOrDefault(false)
                }
                viewModel.showMessage(if (ok) "本地加密备份已保存" else "写入本地备份文件失败")
            }
        }
    }
    val launchKlbkCreate: (ByteArray) -> Unit = { bytes ->
        pendingExportBytes = bytes
        runCatching {
            klbkCreateLauncher.launch("yubrae_backup_${LocalDate.now()}.klbk")
        }.onFailure {
            pendingExportBytes = null
            viewModel.showMessage("无法打开系统保存位置选择器，建议使用 WebDAV 云备份")
        }
    }

    // 3. SAF / GetContent 双通道选择本地 .klbk 文件恢复
    val handleKlbkUriPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) {
                    pendingLocalRestoreBytes = bytes
                } else {
                    viewModel.showMessage("无法读取本地备份文件")
                }
            }
        }
    }
    val klbkOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = handleKlbkUriPicked
    )
    val klbkFallbackOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = handleKlbkUriPicked
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入与备份") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
                start = 12.dp,
                end = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 板块 1：账单导入
            item(key = "csv_import_section") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.UploadFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("导入账单", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "XLSX · XLS · CSV · TSV · ZIP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                launchDocumentPickerSafely(
                                    primary = csvOpenLauncher,
                                    fallback = csvFallbackOpenLauncher,
                                    onAllFailed = {
                                        viewModel.showMessage("无法打开系统文件选择器，请检查文件管理应用权限")
                                    }
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("选择账单文件")
                        }
                    }
                }
            }

            // 板块 2：WebDAV AES-256-GCM 加密云备份
            item(key = "webdav_section") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("WebDAV 加密云备份", style = MaterialTheme.typography.titleMedium)
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = webDavUrl.contains("jianguoyun", ignoreCase = true),
                                onClick = {
                                    webDavUrl = "https://dav.jianguoyun.com/dav/"
                                    if (webDavRemoteDir.isBlank()) webDavRemoteDir = "/Yubrae"
                                },
                                label = { Text("坚果云") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    webDavUrl = "https://nas.example.com:5006/"
                                    if (webDavRemoteDir.isBlank()) webDavRemoteDir = "/Yubrae"
                                },
                                label = { Text("群晖 / NAS") }
                            )
                        }

                        OutlinedTextField(
                            value = webDavUrl,
                            onValueChange = { webDavUrl = it },
                            label = { Text("服务器地址") },
                            placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webDavUsername,
                            onValueChange = { webDavUsername = it },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webDavPassword,
                            onValueChange = { webDavPassword = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webDavRemoteDir,
                            onValueChange = { webDavRemoteDir = it },
                            label = { Text("远程目录") },
                            placeholder = { Text("/Yubrae") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it },
                            label = { Text("加密口令") },
                            singleLine = true,
                            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                    Icon(if (showPassphrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        CompactHairlineDivider(startIndent = 0.dp)

                        CompactSwitchRow(
                            title = "自动云备份",
                            detail = if (settings.lastBackupSyncMillis > 0L) {
                                "上次：${KairoFormats.formatDateTime(settings.lastBackupSyncMillis)}"
                            } else {
                                null
                            },
                            checked = settings.autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateSettings { it.copy(autoBackupEnabled = enabled) }
                            }
                        )

                        val buildUpdatedSettings = {
                            settings.copy(
                                webDavUrl = webDavUrl.trim(),
                                webDavUsername = webDavUsername.trim(),
                                webDavPassword = webDavPassword,
                                webDavRemoteDir = webDavRemoteDir.trim().ifEmpty { "/Yubrae" },
                                backupPassphrase = backupPassphrase
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.saveAndTestWebDav(buildUpdatedSettings())
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存并测试")
                            }
                            Button(
                                onClick = {
                                    viewModel.uploadWebDavBackup(buildUpdatedSettings())
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("上传备份")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshRemoteBackups() },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("云端快照列表")
                        }

                        webDavStatus?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (remoteBackups.isNotEmpty()) {
                            CompactHairlineDivider(startIndent = 0.dp)
                            Text("云端快照 (${remoteBackups.size})", style = MaterialTheme.typography.titleSmall)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                remoteBackups.forEachIndexed { index, file ->
                                    if (index > 0) {
                                        CompactHairlineDivider(startIndent = 0.dp)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${(file.sizeBytes / 1024L).coerceAtLeast(1L)} KB · ${KairoFormats.formatDateTime(file.modifiedAtMillis)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = { restoreRemoteCandidate = file }) {
                                            Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("恢复")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 板块 3：本地 .klbk 加密文件导出与恢复
            item(key = "local_klbk_section") {
                YimuCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("本地加密备份 (.klbk)", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (backupPassphrase.isBlank()) {
                                        showExportPassphrasePrompt = true
                                    } else {
                                        viewModel.exportLocalBackup(backupPassphrase, launchKlbkCreate)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导出 .klbk")
                            }
                            Button(
                                onClick = {
                                    launchDocumentPickerSafely(
                                        primary = klbkOpenLauncher,
                                        fallback = klbkFallbackOpenLauncher,
                                        onAllFailed = {
                                            viewModel.showMessage("无法打开系统文件选择器，请检查文件管理应用权限")
                                        }
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("从 .klbk 恢复")
                            }
                        }
                    }
                }
            }
        }
    }

    // 账单预览与确认导入弹窗
    csvDialog?.let { state ->
        YubraeDialog(
            onDismissRequest = { if (!state.isProcessing) viewModel.dismissCsvImportDialog() },
            title = "账单导入预览",
            subtitle = state.fileName,
            icon = Icons.Rounded.UploadFile,
            dismissOnBackPress = !state.isProcessing,
            dismissOnClickOutside = !state.isProcessing,
            confirmButton = {
                if (state.preview != null && !state.isProcessing) {
                    Button(
                        onClick = { viewModel.confirmCsvImport() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("确认导入 (${state.preview.validRows} 笔)")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissCsvImportDialog() },
                    enabled = !state.isProcessing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("取消")
                }
            }
        ) {
            when {
                state.isProcessing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("正在解析并匹配账本、账户与重复交易...")
                    }
                }
                state.error != null -> {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                state.preview != null -> {
                    val p = state.preview
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "识别格式：${p.formatName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "总记录行：${p.totalRows} · 有效入账：${p.validRows} · 去重跳过：${p.skippedDuplicateRows}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (p.detectedCurrencies.isNotEmpty()) {
                            Text(
                                text = "检测到币种：${p.detectedCurrencies.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (p.newLedgerNames.isNotEmpty()) {
                            Text(
                                text = "将自动新建账本：${p.newLedgerNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (p.newAccountNames.isNotEmpty()) {
                            Text(
                                text = "将自动新建账户：${p.newAccountNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (p.newCategoryNames.isNotEmpty()) {
                            Text(
                                text = "将自动新建分类：${p.newCategoryNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (p.newTagNames.isNotEmpty()) {
                            Text(
                                text = "将同步新增标签：${p.newTagNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        val featureStats = buildList {
                            if (p.reimburseCount > 0) add("含报销 ${p.reimburseCount} 笔")
                            if (p.excludedStatsCount > 0) add("不计收支 ${p.excludedStatsCount} 笔")
                            if (p.refundGeneratedCount > 0) add("自动生成关联退款 ${p.refundGeneratedCount} 笔")
                            if (p.attachedImageCount > 0) add("提取附件图 ${p.attachedImageCount} 张")
                        }
                        if (featureStats.isNotEmpty()) {
                            Text(
                                text = featureStats.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (p.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val displayWarnings = p.warnings.take(8)
                            val remainingCount = p.warnings.size - displayWarnings.size
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = semantic.warning.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = if (remainingCount > 0) {
                                            "注意事项（共 ${p.warnings.size} 条，显示前 8 条）："
                                        } else {
                                            "注意事项（${p.warnings.size} 条）："
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = semantic.warning
                                    )
                                    displayWarnings.forEach { warning ->
                                        Text(
                                            text = "• $warning",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = semantic.warning
                                        )
                                    }
                                    if (remainingCount > 0) {
                                        Text(
                                            text = "…其余 $remainingCount 条提示省略",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = semantic.warning.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 云端恢复二次确认
    restoreRemoteCandidate?.let { file ->
        ConfirmActionDialog(
            title = "从云端快照恢复？",
            message = "将解密「${file.name}」并全量恢复账本数据，当前本地未备份数据将被覆盖。",
            confirmText = "确认恢复",
            isDestructive = true,
            onConfirm = {
                restoreRemoteCandidate = null
                viewModel.restoreRemoteBackup(file.path)
            },
            onDismiss = { restoreRemoteCandidate = null }
        )
    }

    // 导出本地备份输入口令弹窗（当尚未配置全局加密口令时）
    if (showExportPassphrasePrompt) {
        var tempPass by remember { mutableStateOf("") }
        YubraeDialog(
            onDismissRequest = { showExportPassphrasePrompt = false },
            title = "设置备份加密口令",
            icon = Icons.Rounded.Lock,
            confirmButton = {
                Button(
                    onClick = {
                        if (tempPass.isNotBlank()) {
                            backupPassphrase = tempPass
                            viewModel.updateSettings { it.copy(backupPassphrase = tempPass) }
                            showExportPassphrasePrompt = false
                            viewModel.exportLocalBackup(tempPass, launchKlbkCreate)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("导出") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportPassphrasePrompt = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }
            }
        ) {
            OutlinedTextField(
                value = tempPass,
                onValueChange = { tempPass = it },
                label = { Text("输入 AES-256-GCM 加密口令") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 本地 .klbk 恢复输入口令并确认
    pendingLocalRestoreBytes?.let { bytes ->
        var passInput by remember { mutableStateOf(backupPassphrase) }
        YubraeDialog(
            onDismissRequest = { pendingLocalRestoreBytes = null },
            title = "解密并恢复本地备份",
            icon = Icons.Rounded.Lock,
            confirmButton = {
                Button(
                    onClick = {
                        pendingLocalRestoreBytes = null
                        viewModel.importLocalBackup(bytes, passInput)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("解密恢复") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingLocalRestoreBytes = null },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "恢复备份将覆盖当前本地全部数据，请输入该备份文件对应的加密口令：",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = passInput,
                    onValueChange = { passInput = it },
                    label = { Text("解密口令") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun launchDocumentPickerSafely(
    primary: ActivityResultLauncher<Array<String>>,
    fallback: ActivityResultLauncher<String>,
    onAllFailed: () -> Unit
) {
    val primaryResult = runCatching { primary.launch(arrayOf("*/*")) }
    if (primaryResult.isFailure) {
        val fallbackResult = runCatching { fallback.launch("*/*") }
        if (fallbackResult.isFailure) {
            onAllFailed()
        }
    }
}

private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()
}
