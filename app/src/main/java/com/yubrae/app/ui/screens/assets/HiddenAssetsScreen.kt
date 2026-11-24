package com.yubrae.app.ui.screens.assets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.prefs.AssetHiddenModule
import com.yubrae.app.ui.components.CartoonAccountIcon
import com.yubrae.app.ui.components.CompactHairlineDivider
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.LocalPrivacyMasked
import com.yubrae.app.ui.components.YimuCard
import com.yubrae.app.ui.components.formatPrivacyAmount
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel

internal data class AssetSectionSpec(
    val id: String,
    val title: String,
    val groupCodes: Set<String>
)

internal val YimuAssetSections: List<AssetSectionSpec> = listOf(
    AssetSectionSpec(
        id = "cash_funds",
        title = "资金账户",
        groupCodes = setOf(AccountGroup.WALLET, AccountGroup.DEBIT, AccountGroup.FOREIGN)
    ),
    AssetSectionSpec(
        id = "credit_accounts",
        title = "信用账户",
        groupCodes = setOf(AccountGroup.CREDIT)
    ),
    AssetSectionSpec(
        id = "invest_accounts",
        title = "理财账户",
        groupCodes = setOf(AccountGroup.INVEST)
    ),
    AssetSectionSpec(
        id = "debt_accounts",
        title = "借贷往来",
        groupCodes = setOf(AccountGroup.DEBT)
    )
)

/**
 * 资产隐藏页（复刻一木记账「资产隐藏」体验）：
 * - 上方 2 列模块卡（报销 / 债务 / 理财）与下方全量账户卡均可长按切换在资产主页的显隐；
 * - 隐藏项在本页仍可正常查阅真实余额与进入详情，便于私密核账。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenAssetsScreen(
    viewModel: KairoMainViewModel,
    onOpenAccountDetail: (AccountEntity) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.assetsUiState.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val privacyMasked = LocalPrivacyMasked.current
    val haptic = LocalHapticFeedback.current
    val semantic = KairoTheme.semantic
    var collapsedSections by remember { mutableStateOf<Set<String>>(emptySet()) }

    val activeAccounts = remember(allAccounts) {
        allAccounts.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val hasAnyHidden = uiState.hiddenAccountIds.isNotEmpty() || uiState.hiddenModules.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                title = {
                    Text(
                        text = "资产隐藏",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (hasAnyHidden) {
                        TextButton(
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        hiddenAssetAccountIds = emptySet(),
                                        hiddenAssetModules = emptySet()
                                    )
                                }
                                viewModel.showMessage("已全部恢复显示")
                            }
                        ) {
                            Text("全部显示")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hidden_hint") {
                Text(
                    text = "提示：长按资产可进行隐藏或显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // 上方 2 列模块卡片（报销 / 债务 / 理财）
            item(key = "hidden_modules_grid") {
                val reimburseHidden = AssetHiddenModule.REIMBURSE in uiState.hiddenModules
                val debtHidden = AssetHiddenModule.DEBT in uiState.hiddenModules
                val investHidden = AssetHiddenModule.INVEST in uiState.hiddenModules

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HiddenModuleCard(
                            title = "报销",
                            firstLabel = "可报",
                            firstValue = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.formatPlain(uiState.pendingReimburseMicros, 2, grouping = true)
                            ),
                            secondLabel = "已报",
                            secondValue = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.formatPlain(uiState.reimbursedMicros, 2, grouping = true)
                            ),
                            firstValueColor = MaterialTheme.colorScheme.onSurface,
                            secondValueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            hidden = reimburseHidden,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleAssetModuleHidden(AssetHiddenModule.REIMBURSE, "报销")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HiddenModuleCard(
                            title = "债务",
                            firstLabel = "应付",
                            firstValue = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.formatPlain(uiState.debtPayableMicros, 2, grouping = true)
                            ),
                            secondLabel = "应收",
                            secondValue = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.formatPlain(uiState.debtReceivableMicros, 2, grouping = true)
                            ),
                            firstValueColor = if (uiState.debtPayableMicros > 0L) semantic.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                            secondValueColor = semantic.income,
                            hidden = debtHidden,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleAssetModuleHidden(AssetHiddenModule.DEBT, "债务")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val investProfitPrefix = if (uiState.investProfitMicros > 0L) "+" else ""
                        HiddenModuleCard(
                            title = "理财",
                            firstLabel = "总额",
                            firstValue = formatPrivacyAmount(
                                privacyMasked,
                                MoneyAmount.formatPlain(uiState.investTotalMicros, 2, grouping = true)
                            ),
                            secondLabel = "盈亏",
                            secondValue = formatPrivacyAmount(
                                privacyMasked,
                                "$investProfitPrefix${MoneyAmount.formatPlain(uiState.investProfitMicros, 2, grouping = true)}"
                            ),
                            firstValueColor = MaterialTheme.colorScheme.onSurface,
                            secondValueColor = if (uiState.investProfitMicros < 0L) semantic.expense else semantic.income,
                            hidden = investHidden,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleAssetModuleHidden(AssetHiddenModule.INVEST, "理财")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (activeAccounts.isEmpty()) {
                item(key = "hidden_empty_accounts") {
                    EmptyStateCard(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "暂无账户"
                    )
                }
            } else {
                YimuAssetSections.forEach { section ->
                    val sectionAccounts = activeAccounts.filter { it.groupType in section.groupCodes }
                    if (sectionAccounts.isNotEmpty()) {
                        item(key = "hidden_sec_${section.id}") {
                            val collapsed = section.id in collapsedSections
                            val subtotalMicros = sectionAccounts.sumOf { acc ->
                                uiState.netWorth.accountConvertedBalances[acc.id] ?: acc.balanceMicros
                            }
                            YimuCard(cornerRadius = 16.dp) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                collapsedSections = if (collapsed) {
                                                    collapsedSections - section.id
                                                } else {
                                                    collapsedSections + section.id
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formatPrivacyAmount(
                                                    privacyMasked,
                                                    MoneyAmount.formatPlain(subtotalMicros, 2, grouping = true)
                                                ),
                                                style = MaterialTheme.typography.titleSmall.tabular(),
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    if (!collapsed) {
                                        sectionAccounts.forEachIndexed { idx, acc ->
                                            CompactHairlineDivider(
                                                startIndent = if (idx == 0) 16.dp else 56.dp,
                                                modifier = Modifier.padding(end = 16.dp)
                                            )
                                            val isHidden = acc.id in uiState.hiddenAccountIds
                                            HiddenAccountRow(
                                                account = acc,
                                                hidden = isHidden,
                                                privacyMasked = privacyMasked,
                                                onClick = { onOpenAccountDetail(acc) },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.toggleAccountHidden(acc.id, acc.name)
                                                }
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenModuleCard(
    title: String,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    firstValueColor: Color,
    secondValueColor: Color,
    hidden: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .alpha(if (hidden) 0.52f else 1f)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (hidden) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            width = 0.5.dp,
            color = if (hidden) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (hidden) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "已隐藏",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "已隐藏",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = firstLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = firstValue,
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        fontWeight = FontWeight.Medium,
                        color = firstValueColor
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = secondLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = secondValue,
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        fontWeight = FontWeight.Medium,
                        color = secondValueColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenAccountRow(
    account: AccountEntity,
    hidden: Boolean,
    privacyMasked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val semantic = KairoTheme.semantic
    val formattedAmount = formatPrivacyAmount(
        privacyMasked,
        MoneyAmount.format(
            amountMicros = account.balanceMicros,
            currencyCode = account.currencyCode,
            showCurrencyCode = false
        )
    )
    val amountColor = if (account.balanceMicros < 0L) semantic.expense else semantic.income

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (hidden) 0.48f else 1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CartoonAccountIcon(
            iconKey = account.icon,
            accountName = account.name,
            colorHex = account.colorHex,
            size = 28.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (hidden) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "已隐藏",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formattedAmount,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp).tabular(),
            fontWeight = FontWeight.SemiBold,
            color = amountColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
