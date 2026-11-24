package com.yubrae.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.ParseStatus
import com.yubrae.app.data.db.RawCaptureLogEntity
import com.yubrae.app.ui.components.ChannelChip
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.ParseStatusChip
import com.yubrae.app.ui.viewmodel.KairoMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureLogsScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val logs by viewModel.captureLogs.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    var statusFilter by remember { mutableStateOf("ALL") }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    var promoteTargetLog by remember { mutableStateOf<RawCaptureLogEntity?>(null) }

    val filteredLogs = remember(logs, statusFilter) {
        if (statusFilter == "ALL") logs else logs.filter { it.parseStatus == statusFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("捕获日志") },
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
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "status_filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        "ALL" to "全部 (${logs.size})",
                        ParseStatus.MATCHED_LOCAL to "本地命中",
                        ParseStatus.MATCHED_AI to "AI 命中",
                        ParseStatus.MERGED_DEDUP to "去重融合",
                        ParseStatus.IGNORED to "已忽略",
                        ParseStatus.FAILED to "失败"
                    )
                    items(options, key = { it.first }) { (code, label) ->
                        FilterChip(
                            selected = statusFilter == code,
                            onClick = { statusFilter = code },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item(key = "empty_logs") {
                    if (logs.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "暂无捕获日志"
                        )
                    } else {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = "无匹配日志",
                            actionLabel = "查看全部",
                            onAction = { statusFilter = "ALL" }
                        )
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    CaptureLogCard(
                        log = log,
                        expanded = expandedLogId == log.id,
                        onToggleExpand = {
                            expandedLogId = if (expandedLogId == log.id) null else log.id
                        },
                        onCopyLog = {
                            clipboardManager.setText(AnnotatedString(formatCaptureLogForClipboard(log)))
                            viewModel.showMessage("已复制完整捕获报文")
                        },
                        onOpenTransaction = { txId -> onOpenTransaction(txId) },
                        onPromote = { promoteTargetLog = log }
                    )
                }
            }
        }
    }

    promoteTargetLog?.let { log ->
        PromoteLogToAliasDialog(
            log = log,
            accounts = accounts,
            onSave = { accountId, pattern, matchType ->
                viewModel.promoteLogToAlias(log.id, accountId, pattern, matchType) {
                    promoteTargetLog = null
                }
            },
            onDismiss = { promoteTargetLog = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureLogCard(
    log: RawCaptureLogEntity,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyLog: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onPromote: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ParseStatusChip(status = log.parseStatus)
                    ChannelChip(channel = log.channel)
                }
                Text(
                    text = KairoFormats.formatDateTime(log.capturedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (log.title.isNotBlank()) {
                Text(
                    text = log.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (expanded) 5 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = log.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = log.sourcePackage.ifBlank { "-" } +
                    if (log.parserUsed.isNotBlank()) " · ${log.parserUsed}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (log.extractedJson.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "解析 JSON 结果",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = log.extractedJson,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }

                    if (log.errorMessage.isNotBlank()) {
                        Text(
                            text = "错误/提示：${log.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCopyLog,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制报文", maxLines = 1)
                        }
                        if (log.linkedTransactionId != null) {
                            OutlinedButton(
                                onClick = { onOpenTransaction(log.linkedTransactionId) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("查看关联交易 #${log.linkedTransactionId}", maxLines = 1)
                            }
                        }
                        Button(
                            onClick = onPromote,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.AddLink, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("沉淀为账户指纹", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun formatCaptureLogForClipboard(log: RawCaptureLogEntity): String = buildString {
    append("包名/来源：").append(log.sourcePackage.ifBlank { "-" })
    if (log.parserUsed.isNotBlank()) {
        append(" · 解析器：").append(log.parserUsed)
    }
    if (log.title.isNotBlank()) {
        append("\n标题：").append(log.title)
    }
    append("\n原始报文：\n").append(log.content)
    if (log.extractedJson.isNotBlank()) {
        append("\n解析JSON:\n").append(log.extractedJson)
    }
    if (log.errorMessage.isNotBlank()) {
        append("\n提示:").append(log.errorMessage)
    }
}

@Composable
private fun PromoteLogToAliasDialog(
    log: RawCaptureLogEntity,
    accounts: List<AccountEntity>,
    onSave: (Long, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAccountId by remember { mutableLongStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var matchType by remember { mutableStateOf(AliasMatchType.PACKAGE) }
    var pattern by remember { mutableStateOf(log.sourcePackage) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("沉淀为账户指纹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("目标账户", style = MaterialTheme.typography.labelMedium)
                if (accounts.isEmpty()) {
                    Text("暂无可用账户", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(accounts, key = { it.id }) { acc ->
                            FilterChip(
                                selected = selectedAccountId == acc.id,
                                onClick = { selectedAccountId = acc.id },
                                label = { Text("${acc.name}(${acc.currencyCode})") }
                            )
                        }
                    }
                }

                Text("匹配方式", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val types = listOf(
                        AliasMatchType.PACKAGE to "包名",
                        AliasMatchType.CARD_LAST4 to "卡尾号",
                        AliasMatchType.KEYWORD to "关键词",
                        AliasMatchType.REGEX to "正则"
                    )
                    items(types, key = { it.first }) { (type, label) ->
                        FilterChip(
                            selected = matchType == type,
                            onClick = {
                                matchType = type
                                pattern = when (type) {
                                    AliasMatchType.PACKAGE -> log.sourcePackage
                                    AliasMatchType.CARD_LAST4 -> Regex("(\\d{4})").find(log.content)?.groupValues?.getOrNull(1) ?: ""
                                    else -> log.title.ifBlank { log.content.take(12) }
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配模式") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        selectedAccountId == 0L -> errorMsg = "请选择目标账户"
                        pattern.trim().isEmpty() -> errorMsg = "请输入匹配模式"
                        else -> onSave(selectedAccountId, pattern.trim(), matchType)
                    }
                }
            ) {
                Text("保存指纹")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
