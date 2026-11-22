package com.kairoledger.app.ui.screens.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairoledger.app.core.contract.RemoteBackupFile
import com.kairoledger.app.data.prefs.ThemeMode
import com.kairoledger.app.ui.components.ConfirmActionDialog
import com.kairoledger.app.ui.components.CurrencyBadge
import com.kairoledger.app.ui.components.CurrencyPickerDialog
import com.kairoledger.app.ui.components.KairoFormats
import com.kairoledger.app.ui.theme.KairoTheme
import com.kairoledger.app.ui.viewmodel.KairoMainViewModel
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
    var showBaseCurrencyPicker by remember { mutableStateOf(false) }

    // 1. SAF 选择 CSV 文件导入
    val csvOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val fileName = resolveDisplayName(context, uri) ?: "bill.csv"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    viewModel.previewCsvImport(fileName, bytes)
                } else {
                    viewModel.showMessage("无法读取所选 CSV 文件")
                }
            }
        }
    }

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
                        true
                    }.getOrDefault(false)
                }
                viewModel.showMessage(if (ok) "本地加密备份已保存" else "写入本地备份文件失败")
            }
        }
    }

    // 3. SAF 选择本地 .klbk 文件恢复
    val klbkOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    pendingLocalRestoreBytes = bytes
                } else {
                    viewModel.showMessage("无法读取本地备份文件")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入、同步与外观设置") },
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
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 板块 1：账单 CSV 导入
            item(key = "csv_import_section") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, tint = MaterialTheme.colorScheme.primary)
                            Text("账单 CSV 导入", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = "自动识别并导入一木记账、微信支付账单、支付宝账单 CSV（兼容 UTF-8 与 GB18030/GBK 编码，导入前先预览去重统计）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                csvOpenLauncher.launch(
                                    arrayOf(
                                        "text/*",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/csv",
                                        "*/*"
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("选择 CSV 账单文件")
                        }
                    }
                }
            }

            // 板块 2：WebDAV AES-256-GCM 加密云备份
            item(key = "webdav_section") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                            Text("WebDAV 端到端加密云备份", style = MaterialTheme.typography.titleMedium)
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = webDavUrl.contains("jianguoyun", ignoreCase = true),
                                onClick = {
                                    webDavUrl = "https://dav.jianguoyun.com/dav/"
                                    if (webDavRemoteDir.isBlank()) webDavRemoteDir = "/KairoLedger"
                                },
                                label = { Text("坚果云示例") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    webDavUrl = "https://nas.example.com:5006/"
                                    if (webDavRemoteDir.isBlank()) webDavRemoteDir = "/KairoLedger"
                                },
                                label = { Text("群晖/NAS 示例") }
                            )
                        }

                        OutlinedTextField(
                            value = webDavUrl,
                            onValueChange = { webDavUrl = it },
                            label = { Text("WebDAV 服务器地址") },
                            placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webDavUsername,
                            onValueChange = { webDavUsername = it },
                            label = { Text("用户名 / 邮箱") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = webDavPassword,
                            onValueChange = { webDavPassword = it },
                            label = { Text("密码 / 应用专用密码") },
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
                            label = { Text("远程备份目录") },
                            placeholder = { Text("/KairoLedger") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = backupPassphrase,
                            onValueChange = { backupPassphrase = it },
                            label = { Text("本地 AES-256-GCM 加密口令（务必牢记）") },
                            singleLine = true,
                            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                    Icon(if (showPassphrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("自动定时加密云备份", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (settings.lastBackupSyncMillis > 0L) {
                                        "上次备份：${KairoFormats.formatDateTime(settings.lastBackupSyncMillis)}"
                                    } else {
                                        "尚未执行过云端备份"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettings { it.copy(autoBackupEnabled = enabled) }
                                }
                            )
                        }

                        val buildUpdatedSettings = {
                            settings.copy(
                                webDavUrl = webDavUrl.trim(),
                                webDavUsername = webDavUsername.trim(),
                                webDavPassword = webDavPassword,
                                webDavRemoteDir = webDavRemoteDir.trim().ifEmpty { "/KairoLedger" },
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存并测试")
                            }
                            Button(
                                onClick = {
                                    viewModel.uploadWebDavBackup(buildUpdatedSettings())
                                },
                                enabled = !isBusy,
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
                                Text("立即上传备份")
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshRemoteBackups() },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("列出云端历史快照")
                        }

                        webDavStatus?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (remoteBackups.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Text("云端快照 (${remoteBackups.size})", style = MaterialTheme.typography.titleSmall)
                            remoteBackups.forEach { file ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, style = MaterialTheme.typography.titleSmall)
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
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("本地加密备份 (.klbk)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "离线导出或恢复包含全部账户、流水、规则与设置的 AES-256-GCM 加密数据包。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (backupPassphrase.isBlank()) {
                                        showExportPassphrasePrompt = true
                                    } else {
                                        viewModel.exportLocalBackup(backupPassphrase) { bytes ->
                                            pendingExportBytes = bytes
                                            klbkCreateLauncher.launch("kairo_backup_${LocalDate.now()}.klbk")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("导出 .klbk")
                            }
                            Button(
                                onClick = { klbkOpenLauncher.launch(arrayOf("*/*")) },
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

            // 板块 4：主题外观与全局主币种
            item(key = "appearance_section") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                            Text("外观与主币种", style = MaterialTheme.typography.titleMedium)
                        }

                        Text("主题模式", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ThemeMode.SYSTEM to "跟随系统",
                                ThemeMode.LIGHT to "浅色模式",
                                ThemeMode.DARK to "深色模式"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.updateSettings { it.copy(themeMode = mode) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Material You 动态取色", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Android 12+ 自动提取壁纸色板，关闭则使用棱镜青蓝金融色板",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSettings { it.copy(dynamicColor = enabled) }
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text("默认主币种（记账折算基准）", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val quick = if (settings.pinnedCurrencies.contains(settings.baseCurrency)) {
                                settings.pinnedCurrencies
                            } else {
                                settings.pinnedCurrencies + settings.baseCurrency
                            }
                            items(quick, key = { it }) { code ->
                                CurrencyBadge(
                                    currencyCode = code,
                                    selected = code.equals(settings.baseCurrency, true),
                                    onClick = {
                                        viewModel.updateSettings { it.copy(baseCurrency = code) }
                                    }
                                )
                            }
                            item(key = "more_base") {
                                FilterChip(
                                    selected = false,
                                    onClick = { showBaseCurrencyPicker = true },
                                    label = { Text("更多币种") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // CSV 预览与确认导入弹窗
    csvDialog?.let { state ->
        AlertDialog(
            onDismissRequest = { if (!state.isProcessing) viewModel.dismissCsvImportDialog() },
            title = { Text("账单导入预览 · ${state.fileName}") },
            text = {
                when {
                    state.isProcessing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("正在解析并匹配账户与重复交易...")
                        }
                    }
                    state.error != null -> {
                        Text(state.error, color = MaterialTheme.colorScheme.error)
                    }
                    state.preview != null -> {
                        val p = state.preview
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("识别格式：${p.formatName}", fontWeight = FontWeight.Bold)
                            Text("总记录行：${p.totalRows} · 有效入账：${p.validRows} · 去重跳过：${p.skippedDuplicateRows}")
                            if (p.detectedCurrencies.isNotEmpty()) {
                                Text("检测到币种：${p.detectedCurrencies.joinToString(", ")}")
                            }
                            if (p.newAccountNames.isNotEmpty()) {
                                Text("将自动新建账户：${p.newAccountNames.joinToString(", ")}")
                            }
                            if (p.newCategoryNames.isNotEmpty()) {
                                Text("将自动新建分类：${p.newCategoryNames.joinToString(", ")}")
                            }
                            if (p.warnings.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "注意事项：\n${p.warnings.take(5).joinToString("\n")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.warning
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (state.preview != null && !state.isProcessing) {
                    Button(onClick = { viewModel.confirmCsvImport() }) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("确认导入 (${state.preview.validRows} 笔)")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissCsvImportDialog() },
                    enabled = !state.isProcessing
                ) {
                    Text("取消")
                }
            }
        )
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
        AlertDialog(
            onDismissRequest = { showExportPassphrasePrompt = false },
            title = { Text("设置备份加密口令") },
            text = {
                OutlinedTextField(
                    value = tempPass,
                    onValueChange = { tempPass = it },
                    label = { Text("输入 AES-256-GCM 加密口令") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempPass.isNotBlank()) {
                            backupPassphrase = tempPass
                            viewModel.updateSettings { it.copy(backupPassphrase = tempPass) }
                            showExportPassphrasePrompt = false
                            viewModel.exportLocalBackup(tempPass) { bytes ->
                                pendingExportBytes = bytes
                                klbkCreateLauncher.launch("kairo_backup_${LocalDate.now()}.klbk")
                            }
                        }
                    }
                ) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassphrasePrompt = false }) { Text("取消") }
            }
        )
    }

    // 本地 .klbk 恢复输入口令并确认
    pendingLocalRestoreBytes?.let { bytes ->
        var passInput by remember { mutableStateOf(backupPassphrase) }
        AlertDialog(
            onDismissRequest = { pendingLocalRestoreBytes = null },
            title = { Text("解密并恢复本地备份") },
            text = {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingLocalRestoreBytes = null
                        viewModel.importLocalBackup(bytes, passInput)
                    }
                ) { Text("解密恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLocalRestoreBytes = null }) { Text("取消") }
            }
        )
    }

    if (showBaseCurrencyPicker) {
        CurrencyPickerDialog(
            selectedCode = settings.baseCurrency,
            pinnedCodes = settings.pinnedCurrencies,
            onSelect = { code -> viewModel.updateSettings { it.copy(baseCurrency = code) } },
            onDismiss = { showBaseCurrencyPicker = false }
        )
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
