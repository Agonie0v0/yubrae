package com.yubrae.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yubrae.app.BuildConfig
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.update.AppUpdateInfo
import com.yubrae.app.update.AppUpdateManager
import com.yubrae.app.update.UpdateUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    mirrorPrefix: String,
    canInstallPackages: () -> Boolean,
    onDismiss: () -> Unit,
    onRetryCheck: () -> Unit,
    onStartDownload: (AppUpdateInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallReady: () -> Unit,
    onOpenUnknownSources: () -> Unit,
    onIgnoreVersion: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSaveMirrorPrefix: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val semantic = KairoTheme.semantic
    val isReadyToInstall = state is UpdateUiState.ReadyToInstall
    val latestCanInstallPackages by rememberUpdatedState(canInstallPackages)
    var canInstall by remember(isReadyToInstall) {
        mutableStateOf(if (isReadyToInstall) latestCanInstallPackages() else false)
    }
    var showMirrorConfig by remember { mutableStateOf(false) }
    var mirrorDraft by remember(mirrorPrefix) { mutableStateOf(mirrorPrefix) }
    var mirrorError by remember(mirrorPrefix) { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, isReadyToInstall) {
        if (!isReadyToInstall) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canInstall = latestCanInstallPackages()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    fun applyDraftMirrorIfValid(): Boolean {
        if (mirrorDraft.trim() == mirrorPrefix.trim()) {
            mirrorError = null
            return true
        }
        return when (val validation = AppUpdateManager.validateMirrorPrefix(mirrorDraft)) {
            is AppUpdateManager.MirrorPrefixValidation.Valid -> {
                mirrorError = null
                mirrorDraft = validation.normalizedPrefix
                onSaveMirrorPrefix(validation.normalizedPrefix)
                true
            }
            is AppUpdateManager.MirrorPrefixValidation.Invalid -> {
                showMirrorConfig = true
                mirrorError = validation.reason
                false
            }
        }
    }

    val isDownloadingOrVerifying = state is UpdateUiState.Downloading || state is UpdateUiState.Verifying
    val headerIcon = when (state) {
        is UpdateUiState.UpToDate -> Icons.Rounded.CheckCircle
        is UpdateUiState.Error -> Icons.Rounded.ErrorOutline
        is UpdateUiState.ReadyToInstall -> Icons.Rounded.InstallMobile
        else -> Icons.Rounded.SystemUpdateAlt
    }
    val headerIconTint = when (state) {
        is UpdateUiState.UpToDate -> semantic.income
        is UpdateUiState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val headerIconContainerColor = when (state) {
        is UpdateUiState.UpToDate -> semantic.incomeContainer
        is UpdateUiState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    }
    val titleText = when (state) {
        UpdateUiState.Idle, UpdateUiState.Checking -> "正在检查更新"
        is UpdateUiState.UpToDate -> "当前已是最新版本"
        is UpdateUiState.Available -> "发现新版本 v${state.info.versionName}"
        is UpdateUiState.Downloading -> "正在下载 v${state.info.versionName}"
        is UpdateUiState.Verifying -> "正在校验安装包"
        is UpdateUiState.ReadyToInstall -> "安装包已就绪"
        is UpdateUiState.Error -> "更新遇到问题"
    }
    val showDismissButton = state !is UpdateUiState.Downloading && state !is UpdateUiState.Verifying

    YubraeDialog(
        onDismissRequest = {
            if (!isDownloadingOrVerifying) onDismiss()
        },
        title = titleText,
        icon = headerIcon,
        iconTint = headerIconTint,
        iconContainerColor = headerIconContainerColor,
        dismissOnBackPress = !isDownloadingOrVerifying,
        dismissOnClickOutside = !isDownloadingOrVerifying,
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> {
                    Button(
                        onClick = {
                            if (applyDraftMirrorIfValid()) {
                                onStartDownload(state.info)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "下载并安装",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is UpdateUiState.Downloading -> {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "取消下载",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            if (canInstall) {
                                onInstallReady()
                            } else {
                                onOpenUnknownSources()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (canInstall) Icons.Rounded.InstallMobile else Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (canInstall) "立即安装" else "去允许安装未知应用",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is UpdateUiState.UpToDate -> {
                    TextButton(
                        onClick = {
                            if (applyDraftMirrorIfValid()) {
                                onRetryCheck()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "重新检查",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is UpdateUiState.Error -> {
                    Button(
                        onClick = {
                            if (applyDraftMirrorIfValid()) {
                                val errInfo = state.info
                                if (errInfo != null) onStartDownload(errInfo) else onRetryCheck()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "重试",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                UpdateUiState.Idle, UpdateUiState.Checking, is UpdateUiState.Verifying -> Unit
            }
        },
        dismissButton = if (showDismissButton) {
            {
                when (state) {
                    is UpdateUiState.Available -> {
                        TextButton(
                            onClick = { onIgnoreVersion(state.info.tagName) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("跳过", maxLines = 1, softWrap = false)
                        }
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("稍后", maxLines = 1, softWrap = false)
                        }
                    }
                    is UpdateUiState.Error -> {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("关闭", maxLines = 1, softWrap = false)
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("关闭", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state) {
                UpdateUiState.Idle, UpdateUiState.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.4.dp)
                        Text(
                            text = "正在从 GitHub Releases 获取最新版本信息...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is UpdateUiState.UpToDate -> {
                    Text(
                        text = "当前版本：v${state.currentVersion}（${BuildConfig.BUILD_TYPE}）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (state.latestTag.isNotBlank()) {
                        Text(
                            text = "远端最新发布：${state.latestTag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UpdateUiState.Available -> {
                    UpdateMetaHeader(info = state.info)
                    ReleaseNotesBox(notes = state.info.releaseNotes)
                }

                is UpdateUiState.Downloading -> {
                    val total = state.totalBytes.coerceAtLeast(0L)
                    val downloaded = state.downloadedBytes.coerceAtLeast(0L)
                    if (total > 0L) {
                        val ratio = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        val percent = (ratio * 100f).toInt()
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${formatBytesSize(downloaded)} / ${formatBytesSize(total)} ($percent%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "已下载 ${formatBytesSize(downloaded)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UpdateUiState.Verifying -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.4.dp)
                        Text(
                            text = "正在验证 SHA-256 哈希与包名一致性...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is UpdateUiState.ReadyToInstall -> {
                    UpdateMetaHeader(info = state.info)
                    val verifyHeadline = if (state.sha256Verified) {
                        "SHA-256 完整性校验通过"
                    } else {
                        "包名校验通过（未获取到校验文件）"
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (canInstall) semantic.incomeContainer else semantic.warningContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (canInstall) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (canInstall) semantic.income else semantic.warning,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (canInstall) {
                                    "$verifyHeadline，点击下方「立即安装」即可覆盖更新（数据自动保留）。"
                                } else {
                                    "$verifyHeadline！首次应用内更新需允许「余白」安装未知应用，授权返回后即可安装。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canInstall) semantic.income else semantic.warning
                            )
                        }
                    }
                }

                is UpdateUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "若国内网络直连 GitHub 较慢或超时，可在下方填写镜像加速前缀，或直接使用浏览器下载安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state is UpdateUiState.Available || state is UpdateUiState.UpToDate || state is UpdateUiState.Error) {
                val hasValidMirror = mirrorPrefix.isNotBlank() &&
                    AppUpdateManager.validateMirrorPrefix(mirrorPrefix) is AppUpdateManager.MirrorPrefixValidation.Valid
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = { showMirrorConfig = !showMirrorConfig },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasValidMirror) "下载加速镜像（已配置）" else "下载加速镜像设置",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    when (state) {
                        is UpdateUiState.Available -> {
                            TextButton(
                                onClick = { onOpenBrowser(state.info.htmlUrl) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "浏览器查看",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        is UpdateUiState.Error -> {
                            TextButton(
                                onClick = {
                                    onOpenBrowser(state.fallbackUrl.ifBlank { AppUpdateManager.DEFAULT_RELEASE_PAGE_URL })
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "浏览器下载",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                AnimatedVisibility(visible = showMirrorConfig) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = mirrorDraft,
                            onValueChange = {
                                mirrorDraft = it
                                mirrorError = null
                            },
                            label = { Text("GitHub 下载镜像前缀（可选）") },
                            placeholder = { Text("例如 https://gh-proxy.com/") },
                            isError = mirrorError != null,
                            supportingText = mirrorError?.let { err -> { Text(err) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (mirrorDraft.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        mirrorDraft = ""
                                        mirrorError = null
                                        onSaveMirrorPrefix("")
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("清空") }
                            }
                            TextButton(
                                onClick = {
                                    when (val validation = AppUpdateManager.validateMirrorPrefix(mirrorDraft)) {
                                        is AppUpdateManager.MirrorPrefixValidation.Valid -> {
                                            mirrorError = null
                                            mirrorDraft = validation.normalizedPrefix
                                            onSaveMirrorPrefix(validation.normalizedPrefix)
                                        }
                                        is AppUpdateManager.MirrorPrefixValidation.Invalid -> {
                                            mirrorError = validation.reason
                                            onSaveMirrorPrefix(mirrorDraft)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("保存镜像")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateMetaHeader(info: AppUpdateInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "当前：v${BuildConfig.VERSION_NAME}  →  最新：v${info.versionName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "安装包：${info.assetName}（${formatBytesSize(info.assetSizeBytes)}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReleaseNotesBox(notes: String) {
    val headingColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val formattedNotes = remember(notes, headingColor, mutedColor) {
        formatReleaseNotesForDisplay(
            raw = notes,
            headingColor = headingColor,
            mutedColor = mutedColor
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "更新内容",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formattedNotes,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private const val DEFAULT_RELEASE_NOTES_FALLBACK = "本次发布包含功能优化与稳定性修复。"
private val RELEASE_NOTES_FULL_CHANGELOG_REGEX =
    Regex("""^\*{0,2}\s*Full Changelog\s*\*{0,2}\s*[:：]\s*(.+)$""", RegexOption.IGNORE_CASE)
private val RELEASE_NOTES_COMPARE_RANGE_REGEX = Regex("""/compare/([^)\s]+)""")
private val RELEASE_NOTES_HEADING_REGEX = Regex("""^#{1,6}\s+(.+?)\s*#*$""")
private val RELEASE_NOTES_BULLET_REGEX = Regex("""^(\s*)[-*+]\s+(.+)$""")
private val RELEASE_NOTES_ORDERED_REGEX = Regex("""^(\s*)(\d+)\.\s+(.+)$""")
private val RELEASE_NOTES_HR_REGEX = Regex("""^\s*([-*_])\1{2,}\s*$""")
private val RELEASE_NOTES_LINK_REGEX = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
private val RELEASE_NOTES_INLINE_REGEX = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`""")

/**
 * 将 GitHub Releases Markdown 更新日志转换为适合移动端阅读的 [AnnotatedString]：
 * - `##`/`###` 渲染为加粗小标题；
 * - `-`/`*` 列表项转换为 `• `（子级转换为 `  ◦ `）；
 * - `**bold**` / `__bold__` 转换为粗体，行内代码去除反引号；
 * - 清理裸露的 `**Full Changelog**:` 符号并精简比对链接展示。
 */
internal fun formatReleaseNotesForDisplay(
    raw: String,
    headingColor: Color = Color.Unspecified,
    mutedColor: Color = Color.Unspecified
): AnnotatedString {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    if (normalized.isEmpty()) {
        return AnnotatedString(DEFAULT_RELEASE_NOTES_FALLBACK)
    }

    val rawLines = normalized.split('\n')
    val collapsedLines = mutableListOf<String>()
    var previousBlank = false
    for (line in rawLines) {
        if (RELEASE_NOTES_HR_REGEX.matches(line)) {
            if (collapsedLines.isNotEmpty() && !previousBlank) {
                collapsedLines.add("")
                previousBlank = true
            }
            continue
        }
        val isBlank = line.isBlank()
        if (isBlank) {
            if (collapsedLines.isNotEmpty() && !previousBlank) {
                collapsedLines.add("")
                previousBlank = true
            }
        } else {
            collapsedLines.add(line.trimEnd())
            previousBlank = false
        }
    }
    while (collapsedLines.lastOrNull()?.isEmpty() == true) {
        collapsedLines.removeAt(collapsedLines.lastIndex)
    }
    if (collapsedLines.isEmpty()) {
        return AnnotatedString(DEFAULT_RELEASE_NOTES_FALLBACK)
    }

    return buildAnnotatedString {
        collapsedLines.forEachIndexed { index, line ->
            if (index > 0) append('\n')
            if (line.isEmpty()) return@forEachIndexed

            val trimmed = line.trim()
            val changelogMatch = RELEASE_NOTES_FULL_CHANGELOG_REGEX.matchEntire(trimmed)
            if (changelogMatch != null) {
                val target = changelogMatch.groupValues[1].trim()
                val compareRange = RELEASE_NOTES_COMPARE_RANGE_REGEX.find(target)?.groupValues?.get(1)
                val cleanTarget = if (!compareRange.isNullOrBlank()) {
                    compareRange
                } else {
                    RELEASE_NOTES_LINK_REGEX.replace(target, "$1")
                }
                withStyle(
                    SpanStyle(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = mutedColor
                    )
                ) {
                    append("完整变更记录：$cleanTarget")
                }
                return@forEachIndexed
            }

            val headingMatch = RELEASE_NOTES_HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                val headingBody = headingMatch.groupValues[1]
                    .trim()
                    .removeSurrounding("**")
                    .removeSurrounding("__")
                    .trim()
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = headingColor
                    )
                ) {
                    appendInlineMarkdown(headingBody)
                }
                return@forEachIndexed
            }

            val bulletMatch = RELEASE_NOTES_BULLET_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                val indent = bulletMatch.groupValues[1]
                val body = bulletMatch.groupValues[2].trim()
                append(if (indent.length >= 2) "  ◦ " else "• ")
                appendInlineMarkdown(body)
                return@forEachIndexed
            }

            val orderedMatch = RELEASE_NOTES_ORDERED_REGEX.matchEntire(line)
            if (orderedMatch != null) {
                val indent = if (orderedMatch.groupValues[1].length >= 2) "  " else ""
                val number = orderedMatch.groupValues[2]
                val body = orderedMatch.groupValues[3].trim()
                append("$indent$number. ")
                appendInlineMarkdown(body)
                return@forEachIndexed
            }

            appendInlineMarkdown(trimmed)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    val unlinked = RELEASE_NOTES_LINK_REGEX.replace(text, "$1")
    var cursor = 0
    for (match in RELEASE_NOTES_INLINE_REGEX.findAll(unlinked)) {
        if (match.range.first > cursor) {
            append(unlinked.substring(cursor, match.range.first))
        }
        val boldDoubleStar = match.groupValues[1]
        val boldUnderscore = match.groupValues[2]
        val inlineCode = match.groupValues[3]
        when {
            boldDoubleStar.isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldDoubleStar)
                }
            }
            boldUnderscore.isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldUnderscore)
                }
            }
            inlineCode.isNotEmpty() -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(inlineCode)
                }
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < unlinked.length) {
        append(unlinked.substring(cursor))
    }
}

private fun formatBytesSize(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}
