package com.yubrae.app.capture

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.LedgerEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TagTextCodec
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.domain.RecordUsageRanker
import com.yubrae.app.ui.components.CartoonAccountIcon
import com.yubrae.app.ui.components.CartoonCategoryIcon
import com.yubrae.app.ui.components.CategoryExplorerPanel
import com.yubrae.app.ui.components.CompactCalculatorKeypad
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.CompactSwitchRow
import com.yubrae.app.ui.components.CurrencyInlinePickerPanel
import com.yubrae.app.ui.components.GroupedAccountPickerPanel
import com.yubrae.app.ui.components.GroupedTagPickerPanel
import com.yubrae.app.ui.components.InlineSearchInputBar
import com.yubrae.app.ui.components.KairoIcons
import com.yubrae.app.ui.components.QuickExprEval
import com.yubrae.app.ui.components.TagBadgeChip
import com.yubrae.app.ui.components.transactionAmountColor
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.YubraeTheme
import com.yubrae.app.ui.theme.tabular

internal enum class OverlaySubPage {
    MAIN,
    CATEGORY,
    TAGS,
    ACCOUNT,
    TO_ACCOUNT,
    CURRENCY,
    MORE,
    AMOUNT_KEYPAD
}

@Stable
internal class FloatingEditorUiState(
    val txId: Long,
    val isPreviewMode: Boolean,
    val sourceBadge: String,
    val fallbackCategoryName: String,
    val fallbackAccountName: String,
    initialType: String,
    initialOrigAmountMicros: Long,
    initialOrigCurrency: String,
    initialAccountId: Long,
    initialToAccountId: Long?,
    initialCategoryId: Long?,
    initialLedgerId: Long,
    initialMerchant: String,
    initialNote: String,
    initialReimburseStatus: String,
    initialExcludeFromStats: Boolean,
    initialExcludeFromBudget: Boolean,
    val timestamp: Long,
    val sourceType: String,
    val rawEventId: Long?,
    initialTags: String,
    val attachmentPaths: String,
    val refundOfTxId: Long?,
    val reimbursedTxId: Long?,
    initialPreserveRate: Boolean,
    val initialAccountAmountMicros: Long,
    val initialAccountCurrency: String,
    val initialRateToAccountMicros: Long?,
    initialPreviewAmountMicros: Long,
    initialPreviewCurrency: String,
    initialPreviewRateMicros: Long,
    initialExpanded: Boolean,
    initialRemainingSeconds: Int,
    initialAccounts: List<AccountEntity>,
    initialCategories: List<CategoryEntity>,
    initialLedgers: List<LedgerEntity>,
    initialTagsList: List<TagEntity>,
    initialRecentTransactions: List<TransactionEntity>
) {
    var type by mutableStateOf(initialType)
    var origAmountMicros by mutableLongStateOf(initialOrigAmountMicros)
    var origCurrency by mutableStateOf(initialOrigCurrency)
    var accountId by mutableLongStateOf(initialAccountId)
    var toAccountId by mutableStateOf(initialToAccountId)
    var categoryId by mutableStateOf(initialCategoryId)
    var ledgerId by mutableLongStateOf(initialLedgerId)
    var merchant by mutableStateOf(initialMerchant)
    var note by mutableStateOf(initialNote)
    var reimburseStatus by mutableStateOf(initialReimburseStatus)
    var excludeFromStats by mutableStateOf(initialExcludeFromStats)
    var excludeFromBudget by mutableStateOf(initialExcludeFromBudget)
    var tags by mutableStateOf(initialTags)

    var preserveInitialRate by mutableStateOf(initialPreserveRate)
    var previewAccountAmountMicros by mutableLongStateOf(initialPreviewAmountMicros)
    var previewAccountCurrency by mutableStateOf(initialPreviewCurrency)
    var previewRateToAccountMicros by mutableLongStateOf(initialPreviewRateMicros)

    var isExpanded by mutableStateOf(initialExpanded)
    var subPage by mutableStateOf(OverlaySubPage.MAIN)
    var countdownPaused by mutableStateOf(false)
    var remainingSeconds by mutableIntStateOf(initialRemainingSeconds)
    var imeFocused by mutableStateOf(false)
    var amountExprDraft by mutableStateOf("")

    var accounts by mutableStateOf(initialAccounts)
    var categories by mutableStateOf(initialCategories)
    var ledgers by mutableStateOf(initialLedgers)
    var availableTags by mutableStateOf(initialTagsList)
    var recentTransactions by mutableStateOf(initialRecentTransactions)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FloatingConfirmOverlayRoot(
    settings: AppSettings,
    state: FloatingEditorUiState,
    onUserInteracted: () -> Unit,
    onRequestImeFocus: () -> Unit,
    onReleaseImeFocus: () -> Unit,
    onTypeOrAmountOrAccountChanged: () -> Unit,
    onCreateCategory: suspend (CategoryEntity) -> Long?,
    onCreateTag: suspend (name: String, groupName: String, colorHex: Long, icon: String) -> Unit,
    onQuickConfirm: () -> Unit,
    onSaveConfirm: () -> Unit,
    onDelete: () -> Unit,
    onDismissOnly: () -> Unit
) {
    YubraeTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        themeStyle = settings.themeStyle,
        oledPureBlack = settings.oledPureBlack,
        greenExpenseMode = settings.greenExpenseMode
    ) {
        val focusManager = LocalFocusManager.current

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .pointerInteropFilter {
                    onUserInteracted()
                    false
                }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = state.isExpanded,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "floating_overlay_expand"
                ) { expanded ->
                    if (!expanded) {
                        FloatingCompactCard(
                            state = state,
                            onExpand = { targetPage ->
                                onUserInteracted()
                                state.subPage = targetPage
                                state.isExpanded = true
                            },
                            onDelete = {
                                onUserInteracted()
                                onDelete()
                            },
                            onQuickConfirm = onQuickConfirm
                        )
                    } else {
                        FloatingRichEditorCard(
                            settings = settings,
                            state = state,
                            onUserInteracted = onUserInteracted,
                            onRequestImeFocus = {
                                onUserInteracted()
                                state.imeFocused = true
                                onRequestImeFocus()
                            },
                            onReleaseImeFocus = {
                                focusManager.clearFocus()
                                state.imeFocused = false
                                onReleaseImeFocus()
                            },
                            onTypeOrAmountOrAccountChanged = onTypeOrAmountOrAccountChanged,
                            onCreateCategory = onCreateCategory,
                            onCreateTag = onCreateTag,
                            onSaveConfirm = {
                                focusManager.clearFocus()
                                state.imeFocused = false
                                onReleaseImeFocus()
                                onSaveConfirm()
                            },
                            onDelete = {
                                focusManager.clearFocus()
                                state.imeFocused = false
                                onReleaseImeFocus()
                                onDelete()
                            },
                            onDismissOnly = {
                                focusManager.clearFocus()
                                state.imeFocused = false
                                onReleaseImeFocus()
                                onDismissOnly()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingHeaderBar(
    state: FloatingEditorUiState,
    showCollapseButton: Boolean,
    onCollapse: () -> Unit,
    onExpand: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val cleanBadge = state.sourceBadge.trim()
    val brandTitle = when {
        state.isPreviewMode -> "余白 · 弹窗预览"
        cleanBadge.isNotEmpty() -> "余白 · $cleanBadge"
        else -> "余白 · 自动记账"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = brandTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        val badgeBg = if (state.countdownPaused) {
            semantic.warningContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        }
        val badgeFg = if (state.countdownPaused) {
            semantic.warning
        } else {
            MaterialTheme.colorScheme.primary
        }
        val badgeText = when {
            state.isPreviewMode -> "预览模式"
            state.countdownPaused -> "已暂停"
            state.remainingSeconds > 0 -> "${state.remainingSeconds}s 自动确认"
            else -> "待确认"
        }

        Surface(
            shape = CircleShape,
            color = badgeBg
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = badgeFg,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        if (showCollapseButton) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onCollapse)
            ) {
                Text(
                    text = "切换精简",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onExpand)
            ) {
                Text(
                    text = "展开",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingCompactCard(
    state: FloatingEditorUiState,
    onExpand: (OverlaySubPage) -> Unit,
    onDelete: () -> Unit,
    onQuickConfirm: () -> Unit
) {
    val categoryObj = remember(state.categories, state.categoryId) {
        state.categories.firstOrNull { it.id == state.categoryId }
    }
    val accountObj = remember(state.accounts, state.accountId) {
        state.accounts.firstOrNull { it.id == state.accountId }
    }
    val selectedTags = remember(state.tags) { TagTextCodec.parse(state.tags) }
    val amountColor = transactionAmountColor(state.type)
    val signPrefix = when (state.type) {
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> "⇄ "
        else -> "-"
    }
    val formattedAmount = signPrefix + MoneyAmount.format(
        amountMicros = state.origAmountMicros,
        currencyCode = state.origCurrency,
        showSign = false,
        showCurrencyCode = true
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FloatingHeaderBar(
            state = state,
            showCollapseButton = false,
            onCollapse = {},
            onExpand = { onExpand(OverlaySubPage.MAIN) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable {
                        onExpand(
                            if (state.type == TransactionType.TRANSFER) OverlaySubPage.ACCOUNT else OverlaySubPage.CATEGORY
                        )
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.type == TransactionType.TRANSFER) {
                        CartoonAccountIcon(
                            iconKey = accountObj?.icon ?: "wallet",
                            accountName = accountObj?.name ?: state.fallbackAccountName,
                            colorHex = accountObj?.colorHex ?: 0xFF2AA876L,
                            size = 38.dp
                        )
                    } else {
                        CartoonCategoryIcon(
                            iconKey = categoryObj?.icon ?: "category",
                            size = 36.dp,
                            fallbackColorHex = categoryObj?.colorHex ?: 0xFF2AA876L
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onExpand(OverlaySubPage.MAIN) },
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val primaryTitle = state.merchant.ifBlank {
                    categoryObj?.name ?: state.fallbackCategoryName.ifBlank { "待核对账单" }
                }
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaParts = listOfNotNull(
                    categoryObj?.name?.takeIf { it != primaryTitle },
                    accountObj?.name ?: state.fallbackAccountName.takeIf { it.isNotBlank() },
                    selectedTags.take(2).joinToString(" ") { "#$it" }.takeIf { it.isNotBlank() }
                )
                Text(
                    text = metaParts.joinToString(" · ").ifBlank { "点击查看详情" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    ).tabular(),
                    color = amountColor,
                    maxLines = 1
                )
                val convertedHint = formatConvertedAccountHint(state)
                if (convertedHint.isNotEmpty()) {
                    Text(
                        text = convertedHint,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp).tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(0.85f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(onClick = onDelete)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isPreviewMode) "关闭" else "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onExpand(OverlaySubPage.MAIN) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "修改详情",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = onQuickConfirm,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1.15f)
                    .height(40.dp)
            ) {
                Text(
                    text = if (state.isPreviewMode) "模拟确认" else "确认入账",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingRichEditorCard(
    settings: AppSettings,
    state: FloatingEditorUiState,
    onUserInteracted: () -> Unit,
    onRequestImeFocus: () -> Unit,
    onReleaseImeFocus: () -> Unit,
    onTypeOrAmountOrAccountChanged: () -> Unit,
    onCreateCategory: suspend (CategoryEntity) -> Long?,
    onCreateTag: suspend (name: String, groupName: String, colorHex: Long, icon: String) -> Unit,
    onSaveConfirm: () -> Unit,
    onDelete: () -> Unit,
    onDismissOnly: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val selectedCategory = remember(state.categories, state.categoryId) {
        state.categories.firstOrNull { it.id == state.categoryId }
    }
    val selectedParent = remember(state.categories, selectedCategory) {
        selectedCategory?.parentId?.let { pid -> state.categories.firstOrNull { it.id == pid } }
    }
    val selectedAccount = remember(state.accounts, state.accountId) {
        state.accounts.firstOrNull { it.id == state.accountId }
    }
    val selectedToAccount = remember(state.accounts, state.toAccountId) {
        state.accounts.firstOrNull { it.id == state.toAccountId }
    }
    val selectedTags = remember(state.tags) { TagTextCodec.parse(state.tags) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FloatingHeaderBar(
            state = state,
            showCollapseButton = state.subPage == OverlaySubPage.MAIN,
            onCollapse = {
                onReleaseImeFocus()
                state.isExpanded = false
            },
            onExpand = {}
        )

        AnimatedContent(
            targetState = state.subPage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "floating_subpage"
        ) { page ->
            when (page) {
                OverlaySubPage.MAIN -> {
                    FloatingMainEditorBody(
                        state = state,
                        semantic = semantic,
                        selectedCategory = selectedCategory,
                        selectedParent = selectedParent,
                        selectedAccount = selectedAccount,
                        selectedToAccount = selectedToAccount,
                        selectedTags = selectedTags,
                        onUserInteracted = onUserInteracted,
                        onRequestImeFocus = onRequestImeFocus,
                        onReleaseImeFocus = onReleaseImeFocus,
                        onTypeChanged = { newType ->
                            if (newType == TransactionType.TRANSFER && state.accounts.size < 2) {
                                return@FloatingMainEditorBody
                            }
                            if (state.type != newType) {
                                state.type = newType
                                if (newType != TransactionType.TRANSFER) {
                                    val valid = state.categories.firstOrNull {
                                        it.id == state.categoryId && it.type.equals(newType, ignoreCase = true)
                                    }
                                    if (valid == null) {
                                        state.categoryId = state.categories
                                            .firstOrNull { it.type.equals(newType, ignoreCase = true) && it.parentId == null }
                                            ?.id
                                            ?: state.categories.firstOrNull { it.type.equals(newType, ignoreCase = true) }?.id
                                    }
                                } else if (state.toAccountId == null || state.toAccountId == state.accountId) {
                                    state.toAccountId = state.accounts.firstOrNull { it.id != state.accountId }?.id
                                }
                                onTypeOrAmountOrAccountChanged()
                            }
                        },
                        onSelectCategoryDirect = { catId ->
                            onUserInteracted()
                            state.categoryId = catId
                        },
                        onNavigate = { target ->
                            onUserInteracted()
                            onReleaseImeFocus()
                            if (target == OverlaySubPage.AMOUNT_KEYPAD) {
                                val digits = CurrencyCatalog.find(state.origCurrency).minorDigits
                                state.amountExprDraft = if (state.origAmountMicros > 0L) {
                                    MoneyAmount.formatPlain(state.origAmountMicros, digits, grouping = false)
                                } else ""
                            }
                            state.subPage = target
                        },
                        onSaveConfirm = onSaveConfirm,
                        onDelete = onDelete,
                        onDismissOnly = onDismissOnly
                    )
                }

                OverlaySubPage.CATEGORY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySubPageTopBar(
                            title = if (state.type == TransactionType.INCOME) "选择收入分类" else "选择支出分类",
                            onBack = {
                                onReleaseImeFocus()
                                state.subPage = OverlaySubPage.MAIN
                            }
                        )
                        CategoryExplorerPanel(
                            txType = state.type,
                            categories = state.categories,
                            selectedCategoryId = state.categoryId,
                            recentTransactions = state.recentTransactions,
                            onSelectCategory = { catId ->
                                onUserInteracted()
                                state.categoryId = catId
                                onReleaseImeFocus()
                                state.subPage = OverlaySubPage.MAIN
                            },
                            onCreateCategory = { entity ->
                                val newId = onCreateCategory(entity)
                                if (newId != null && newId > 0L) {
                                    state.categoryId = newId
                                }
                                newId
                            },
                            onRequestImeFocus = onRequestImeFocus,
                            onReleaseImeFocus = onReleaseImeFocus,
                            compactMode = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                OverlaySubPage.TAGS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySubPageTopBar(
                            title = "标签筛选与新建",
                            onBack = {
                                onReleaseImeFocus()
                                state.subPage = OverlaySubPage.MAIN
                            }
                        )
                        GroupedTagPickerPanel(
                            selectedTagNames = selectedTags,
                            availableTags = state.availableTags,
                            allTransactions = state.recentTransactions,
                            onToggleTag = { tagName ->
                                onUserInteracted()
                                val updated = if (tagName in selectedTags) {
                                    selectedTags.filterNot { it == tagName }
                                } else {
                                    selectedTags + tagName
                                }
                                state.tags = TagTextCodec.format(updated)
                            },
                            onClearTags = {
                                onUserInteracted()
                                state.tags = ""
                            },
                            onQuickCreateTag = { name, groupName, colorHex, icon ->
                                onUserInteracted()
                                state.tags = TagTextCodec.merge(state.tags, name)
                                onCreateTag(name, groupName, colorHex, icon)
                            },
                            onDone = {
                                onReleaseImeFocus()
                                state.subPage = OverlaySubPage.MAIN
                            },
                            onRequestImeFocus = onRequestImeFocus,
                            onReleaseImeFocus = onReleaseImeFocus,
                            compactMode = true,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                OverlaySubPage.ACCOUNT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySubPageTopBar(
                            title = if (state.type == TransactionType.TRANSFER) "选择转出账户" else "选择结算账户",
                            onBack = { state.subPage = OverlaySubPage.MAIN }
                        )
                        GroupedAccountPickerPanel(
                            accounts = state.accounts,
                            selectedAccountId = state.accountId,
                            onSelect = { acc ->
                                onUserInteracted()
                                if (state.accountId != acc.id) {
                                    state.accountId = acc.id
                                    state.preserveInitialRate = false
                                    if (state.toAccountId == acc.id) {
                                        state.toAccountId = state.accounts.firstOrNull { it.id != acc.id }?.id
                                    }
                                    onTypeOrAmountOrAccountChanged()
                                }
                                state.subPage = OverlaySubPage.MAIN
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                OverlaySubPage.TO_ACCOUNT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySubPageTopBar(
                            title = "选择转入账户",
                            onBack = { state.subPage = OverlaySubPage.MAIN }
                        )
                        GroupedAccountPickerPanel(
                            accounts = state.accounts,
                            selectedAccountId = state.toAccountId,
                            excludedAccountId = state.accountId,
                            onSelect = { acc ->
                                onUserInteracted()
                                state.toAccountId = acc.id
                                state.subPage = OverlaySubPage.MAIN
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                OverlaySubPage.CURRENCY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySubPageTopBar(
                            title = "选择交易币种",
                            onBack = { state.subPage = OverlaySubPage.MAIN }
                        )
                        CurrencyInlinePickerPanel(
                            selectedCode = state.origCurrency,
                            pinnedCodes = settings.pinnedCurrencies,
                            onSelect = { code ->
                                onUserInteracted()
                                if (!state.origCurrency.equals(code, ignoreCase = true)) {
                                    state.origCurrency = code
                                    state.preserveInitialRate = false
                                    onTypeOrAmountOrAccountChanged()
                                }
                                state.subPage = OverlaySubPage.MAIN
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                OverlaySubPage.MORE -> {
                    FloatingMoreSubPage(
                        state = state,
                        onUserInteracted = onUserInteracted,
                        onRequestImeFocus = onRequestImeFocus,
                        onReleaseImeFocus = onReleaseImeFocus,
                        onBack = {
                            onReleaseImeFocus()
                            state.subPage = OverlaySubPage.MAIN
                        }
                    )
                }

                OverlaySubPage.AMOUNT_KEYPAD -> {
                    val info = CurrencyCatalog.find(state.origCurrency)
                    val evaluatedMicros = QuickExprEval.evaluateToMicros(state.amountExprDraft) ?: 0L
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlaySubPageTopBar(
                            title = "修改金额 (${info.code})",
                            onBack = { state.subPage = OverlaySubPage.MAIN }
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${info.flag} ${info.code}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${info.symbol}${state.amountExprDraft.ifEmpty { "0.00" }}",
                                        style = MaterialTheme.typography.headlineSmall.tabular(),
                                        fontWeight = FontWeight.Bold,
                                        color = transactionAmountColor(state.type),
                                        maxLines = 1
                                    )
                                    if (QuickExprEval.containsOperator(state.amountExprDraft) && evaluatedMicros > 0L) {
                                        Text(
                                            text = "= ${MoneyAmount.format(evaluatedMicros, state.origCurrency)}",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        CompactCalculatorKeypad(
                            hasOperator = QuickExprEval.containsOperator(state.amountExprDraft),
                            onDigit = { d ->
                                onUserInteracted()
                                state.amountExprDraft = QuickExprEval.appendDigit(state.amountExprDraft, d)
                            },
                            onDot = {
                                onUserInteracted()
                                state.amountExprDraft = QuickExprEval.appendDot(state.amountExprDraft)
                            },
                            onOperator = { op ->
                                onUserInteracted()
                                state.amountExprDraft = QuickExprEval.appendOperator(state.amountExprDraft, op)
                            },
                            onEquals = {
                                onUserInteracted()
                                val eval = QuickExprEval.evaluateAndFormat(state.amountExprDraft, info.minorDigits)
                                if (eval != null) {
                                    state.amountExprDraft = eval
                                }
                            },
                            onBackspace = {
                                onUserInteracted()
                                if (state.amountExprDraft.isNotEmpty()) {
                                    state.amountExprDraft = state.amountExprDraft.dropLast(1)
                                }
                            },
                            onClear = {
                                onUserInteracted()
                                state.amountExprDraft = ""
                            },
                            onHideKeypad = { state.subPage = OverlaySubPage.MAIN },
                            onSave = {
                                onUserInteracted()
                                val finalMicros = QuickExprEval.evaluateToMicros(state.amountExprDraft)
                                if (finalMicros != null && finalMicros > 0L && finalMicros != state.origAmountMicros) {
                                    state.origAmountMicros = finalMicros
                                    state.preserveInitialRate = false
                                    onTypeOrAmountOrAccountChanged()
                                }
                                state.subPage = OverlaySubPage.MAIN
                            },
                            saveLabel = "确定",
                            keyHeight = 40.dp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingMainEditorBody(
    state: FloatingEditorUiState,
    semantic: com.yubrae.app.ui.theme.KairoSemanticColors,
    selectedCategory: CategoryEntity?,
    selectedParent: CategoryEntity?,
    selectedAccount: AccountEntity?,
    selectedToAccount: AccountEntity?,
    selectedTags: List<String>,
    onUserInteracted: () -> Unit,
    onRequestImeFocus: () -> Unit,
    onReleaseImeFocus: () -> Unit,
    onTypeChanged: (String) -> Unit,
    onSelectCategoryDirect: (Long) -> Unit,
    onNavigate: (OverlaySubPage) -> Unit,
    onSaveConfirm: () -> Unit,
    onDelete: () -> Unit,
    onDismissOnly: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val currencyInfo = remember(state.origCurrency) { CurrencyCatalog.find(state.origCurrency) }
    val amountColor = transactionAmountColor(state.type)
    val signPrefix = when (state.type) {
        TransactionType.INCOME -> "+"
        TransactionType.TRANSFER -> "⇄ "
        else -> "-"
    }
    val frequentCategories = remember(state.type, state.categories, state.recentTransactions) {
        RecordUsageRanker.rankFrequentCategories(
            type = state.type,
            categories = state.categories,
            transactions = state.recentTransactions,
            limit = 5
        )
    }

    val moreActiveCount = remember(
        state.reimburseStatus,
        state.excludeFromStats,
        state.excludeFromBudget,
        state.ledgerId
    ) {
        var count = 0
        if (state.reimburseStatus != ReimburseStatus.NONE) count++
        if (state.excludeFromStats) count++
        if (state.excludeFromBudget) count++
        if (state.ledgers.size > 1) count++
        count
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. 支出 / 收入 / 转账 分段切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            listOf(
                TransactionType.EXPENSE to "支出",
                TransactionType.INCOME to "收入",
                TransactionType.TRANSFER to "转账"
            ).forEach { (typeKey, label) ->
                val selected = state.type == typeKey
                val bg = when {
                    !selected -> Color.Transparent
                    typeKey == TransactionType.EXPENSE -> semantic.expenseContainer
                    typeKey == TransactionType.INCOME -> semantic.incomeContainer
                    else -> semantic.transferContainer
                }
                val fg = when {
                    !selected -> MaterialTheme.colorScheme.onSurfaceVariant
                    typeKey == TransactionType.EXPENSE -> semantic.expense
                    typeKey == TransactionType.INCOME -> semantic.income
                    else -> semantic.transfer
                }
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = bg,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onTypeChanged(typeKey) }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. 分类与大金额主摘要卡片
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onNavigate(
                                if (state.type == TransactionType.TRANSFER) OverlaySubPage.ACCOUNT else OverlaySubPage.CATEGORY
                            )
                        }
                        .padding(vertical = 2.dp)
                ) {
                    if (state.type == TransactionType.TRANSFER) {
                        CartoonAccountIcon(
                            iconKey = selectedAccount?.icon ?: "wallet",
                            accountName = selectedAccount?.name.orEmpty(),
                            colorHex = selectedAccount?.colorHex ?: 0xFF2AA876L,
                            size = 34.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedAccount?.name ?: "转出"} → ${selectedToAccount?.name ?: "转入"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "点击更换账户",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        CartoonCategoryIcon(
                            iconKey = selectedCategory?.icon ?: "category",
                            size = 34.dp,
                            fallbackColorHex = selectedCategory?.colorHex ?: 0xFF2AA876L
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = selectedCategory?.name ?: state.fallbackCategoryName.ifBlank { "选分类" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = selectedParent?.name ?: "点击选全部分类 / 新建",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .height(24.dp)
                                .clip(CircleShape)
                                .clickable { onNavigate(OverlaySubPage.CURRENCY) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(text = currencyInfo.flag, fontSize = 10.sp)
                                Text(
                                    text = currencyInfo.code,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigate(OverlaySubPage.AMOUNT_KEYPAD) }
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = signPrefix + MoneyAmount.format(
                                    amountMicros = state.origAmountMicros,
                                    currencyCode = state.origCurrency,
                                    showSign = false,
                                    showCurrencyCode = false
                                ),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                ).tabular(),
                                color = amountColor,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "修改金额",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    val convertedHint = formatConvertedAccountHint(state)
                    if (convertedHint.isNotEmpty()) {
                        Text(
                            text = convertedHint,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp).tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. 高频常用分类 1-Tap 速切行（无需横滑地狱）
        if (state.type != TransactionType.TRANSFER && frequentCategories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(frequentCategories, key = { "ov_freq_${it.id}" }) { cat ->
                    val selected = state.categoryId == cat.id
                    val catColor = Color(cat.colorHex)
                    Surface(
                        shape = CircleShape,
                        color = if (selected) catColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = if (selected) 1.dp else 0.5.dp,
                            color = if (selected) catColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .clickable { onSelectCategoryDirect(cat.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CartoonCategoryIcon(
                                iconKey = cat.icon,
                                size = 17.dp,
                                fallbackColorHex = cat.colorHex
                            )
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = if (selected) catColor else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
                item(key = "ov_all_cats") {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .height(30.dp)
                            .clip(CircleShape)
                            .clickable { onNavigate(OverlaySubPage.CATEGORY) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "全部分类",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. 结构化高频属性胶囊（账户 / 标签 / 更多）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.type == TransactionType.TRANSFER) {
                OverlayAttrPill(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = selectedAccount?.name ?: "转出",
                    selected = selectedAccount != null,
                    accentColor = selectedAccount?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary,
                    customLeading = selectedAccount?.let { acc ->
                        {
                            CartoonAccountIcon(
                                iconKey = acc.icon,
                                accountName = acc.name,
                                colorHex = acc.colorHex,
                                size = 15.dp
                            )
                        }
                    },
                    onClick = { onNavigate(OverlaySubPage.ACCOUNT) },
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                OverlayAttrPill(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = selectedToAccount?.name ?: "转入",
                    selected = selectedToAccount != null,
                    accentColor = selectedToAccount?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary,
                    customLeading = selectedToAccount?.let { acc ->
                        {
                            CartoonAccountIcon(
                                iconKey = acc.icon,
                                accountName = acc.name,
                                colorHex = acc.colorHex,
                                size = 15.dp
                            )
                        }
                    },
                    onClick = { onNavigate(OverlaySubPage.TO_ACCOUNT) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                OverlayAttrPill(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    label = selectedAccount?.name ?: state.fallbackAccountName.ifBlank { "账户" },
                    selected = selectedAccount != null,
                    accentColor = selectedAccount?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.primary,
                    customLeading = selectedAccount?.let { acc ->
                        {
                            CartoonAccountIcon(
                                iconKey = acc.icon,
                                accountName = acc.name,
                                colorHex = acc.colorHex,
                                size = 15.dp
                            )
                        }
                    },
                    onClick = { onNavigate(OverlaySubPage.ACCOUNT) },
                    modifier = Modifier.weight(1.1f)
                )
            }

            OverlayAttrPill(
                icon = Icons.AutoMirrored.Rounded.Label,
                label = if (selectedTags.isEmpty()) "标签" else "标签(${selectedTags.size})",
                selected = selectedTags.isNotEmpty(),
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { onNavigate(OverlaySubPage.TAGS) },
                modifier = Modifier.weight(0.95f)
            )

            OverlayAttrPill(
                icon = Icons.Rounded.Tune,
                label = if (moreActiveCount == 0) "更多" else "更多($moreActiveCount)",
                selected = state.reimburseStatus != ReimburseStatus.NONE || state.excludeFromStats || state.excludeFromBudget,
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { onNavigate(OverlaySubPage.MORE) },
                modifier = Modifier.weight(0.85f)
            )
        }

        // 已选标签回显一行
        AnimatedVisibility(visible = selectedTags.isNotEmpty()) {
            val colorMap = remember(state.availableTags) {
                state.availableTags.associate { it.name to it.colorHex }
            }
            val iconMap = remember(state.availableTags) {
                state.availableTags.associate { it.name to it.icon }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(selectedTags, key = { "ov_sel_tag_$it" }) { tagName ->
                    TagBadgeChip(
                        name = tagName,
                        colorHex = colorMap[tagName] ?: RecordUsageRanker.stableFallbackColorHex(tagName),
                        selected = true,
                        compact = true,
                        onClick = { onNavigate(OverlaySubPage.TAGS) },
                        iconKey = iconMap[tagName].orEmpty()
                    )
                }
            }
        }

        // 5. 商户 & 备注快捷输入（带按需软键盘控制与完成收起）
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.imeFocused) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "正在输入商户 / 备注",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReleaseImeFocus() }
                    ) {
                        Text(
                            text = "完成输入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OverlayCompactTextField(
                    value = state.merchant,
                    onValueChange = {
                        onUserInteracted()
                        state.merchant = it
                    },
                    placeholder = "商户",
                    onFocusGained = onRequestImeFocus,
                    onDone = onReleaseImeFocus,
                    modifier = Modifier.weight(0.95f)
                )
                OverlayCompactTextField(
                    value = state.note,
                    onValueChange = {
                        onUserInteracted()
                        state.note = it
                    },
                    placeholder = "备注…",
                    onFocusGained = onRequestImeFocus,
                    onDone = onReleaseImeFocus,
                    modifier = Modifier.weight(1.25f)
                )
            }
        }

        // 6. 底部固定操作栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(0.85f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(onClick = onDelete)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isPreviewMode) "关闭" else "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .weight(0.95f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(onClick = onDismissOnly)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "仅收起",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Button(
                onClick = onSaveConfirm,
                shape = RoundedCornerShape(11.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(40.dp)
            ) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (state.isPreviewMode) "模拟保存" else "保存确认",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FloatingMoreSubPage(
    state: FloatingEditorUiState,
    onUserInteracted: () -> Unit,
    onRequestImeFocus: () -> Unit,
    onReleaseImeFocus: () -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OverlaySubPageTopBar(
            title = "账本、报销与高级属性",
            onBack = onBack
        )

        if (state.ledgers.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "所属账本",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.ledgers, key = { "ov_ldg_${it.id}" }) { ledger ->
                        val selected = state.ledgerId == ledger.id
                        val ledgerColor = Color(ledger.colorHex)
                        Surface(
                            shape = CircleShape,
                            color = if (selected) ledgerColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (selected) 1.dp else 0.5.dp,
                                color = if (selected) ledgerColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onUserInteracted()
                                    state.ledgerId = ledger.id
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = KairoIcons.ledgerIcon(ledger.icon),
                                    contentDescription = null,
                                    tint = ledgerColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = ledger.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) ledgerColor else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "商户名称",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            InlineSearchInputBar(
                query = state.merchant,
                onQueryChange = {
                    onUserInteracted()
                    state.merchant = it
                },
                placeholder = "输入商户或交易对象",
                leadingIcon = Icons.Rounded.Storefront,
                onFocusGained = onRequestImeFocus,
                onFocusLost = onReleaseImeFocus,
                onDone = {
                    focusManager.clearFocus()
                    onReleaseImeFocus()
                },
                height = 36.dp
            )
        }

        if (state.type == TransactionType.EXPENSE) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "报销状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ReimburseStatus.NONE to "不报销",
                        ReimburseStatus.PENDING to "待报销",
                        ReimburseStatus.REIMBURSED to "已报销"
                    ).forEach { (code, label) ->
                        val selected = state.reimburseStatus == code
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (selected) 1.dp else 0.5.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onUserInteracted()
                                    state.reimburseStatus = code
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                CompactSwitchRow(
                    title = "不计入收支统计",
                    checked = state.excludeFromStats,
                    onCheckedChange = {
                        onUserInteracted()
                        state.excludeFromStats = it
                    }
                )
                CompactHairlineDivider(startIndent = 0.dp)
                CompactSwitchRow(
                    title = "不计入预算消耗",
                    checked = state.excludeFromBudget,
                    onCheckedChange = {
                        onUserInteracted()
                        state.excludeFromBudget = it
                    }
                )
            }
        }
    }
}

@Composable
private fun OverlaySubPageTopBar(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OverlayAttrPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color,
    customLeading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) accentColor.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
    val borderColor = if (selected) accentColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(if (selected) 0.8.dp else 0.5.dp, borderColor),
        modifier = modifier
            .height(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        ) {
            if (customLeading != null) {
                customLeading()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OverlayCompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onFocusGained: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.height(34.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onFocusGained()
                    }
            )
        }
    }
}

private fun formatConvertedAccountHint(state: FloatingEditorUiState): String {
    val accCur = state.previewAccountCurrency.trim()
    val accAmt = state.previewAccountAmountMicros
    if (accCur.isBlank() || state.origCurrency.equals(accCur, ignoreCase = true) || accAmt <= 0L) {
        return ""
    }
    val prefix = if (state.type == TransactionType.INCOME) "≈ 账户实入 " else "≈ 账户实扣 "
    return prefix + MoneyAmount.format(accAmt, accCur, showCurrencyCode = true)
}
