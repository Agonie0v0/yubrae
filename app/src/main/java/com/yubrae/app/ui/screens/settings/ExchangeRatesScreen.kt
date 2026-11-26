package com.yubrae.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.CurrencyCatalog
import com.yubrae.app.core.model.CurrencyInfo
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.components.YubraeDialog
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel

private data class CurrencyRateRowData(
    val info: CurrencyInfo,
    val rateToCnyMicros: Long,
    val date: String,
    val source: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeRatesScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val rates by viewModel.exchangeRates.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var editingRow by remember { mutableStateOf<CurrencyRateRowData?>(null) }

    val rowList = remember(rates, settings.pinnedCurrencies) {
        val orderedCodes = CurrencyCatalog.orderedCodes(settings.pinnedCurrencies)
            .filter { !it.equals("CNY", ignoreCase = true) }
        orderedCodes.map { code ->
            val info = CurrencyCatalog.find(code)
            val direct = rates
                .filter { it.baseCurrency.equals(code, true) && it.targetCurrency.equals("CNY", true) }
                .maxByOrNull { it.date + "_" + it.updatedAt }
            val inverse = if (direct == null) {
                rates.filter { it.baseCurrency.equals("CNY", true) && it.targetCurrency.equals(code, true) }
                    .maxByOrNull { it.date + "_" + it.updatedAt }
            } else {
                null
            }
            when {
                direct != null -> CurrencyRateRowData(info, direct.rateMicros, direct.date, direct.source)
                inverse != null -> CurrencyRateRowData(info, MoneyAmount.invertRate(inverse.rateMicros), inverse.date, inverse.source)
                else -> CurrencyRateRowData(info, info.fallbackRateToCnyMicros, "离线内置", "BUILTIN")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("汇率中心") },
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
            item(key = "sync_header") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "对人民币 (CNY) 汇率",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (settings.lastRateSyncMillis > 0L) {
                                    "同步于 ${KairoFormats.formatDateTime(settings.lastRateSyncMillis)}"
                                } else {
                                    "内置参考汇率"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.refreshNetworkRates("CNY") },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("立即刷新")
                        }
                    }
                }
            }

            items(rowList, key = { it.info.code }) { row ->
                val inverseRate = MoneyAmount.invertRate(row.rateToCnyMicros)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editingRow = row }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = row.info.flag, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${row.info.code} · ${row.info.nameZh}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (row.source == "CUSTOM") {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ) {
                                    Text(
                                        text = when (row.source) {
                                            "CUSTOM" -> "自定义"
                                            "BUILTIN" -> "内置"
                                            else -> row.source
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${row.date} · 1 CNY ≈ ${MoneyAmount.formatRate(inverseRate, 4)} ${row.info.code}",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "¥${MoneyAmount.formatRate(row.rateToCnyMicros, 4)}",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "自定义汇率",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    editingRow?.let { row ->
        var rateInput by remember(row) { mutableStateOf(MoneyAmount.formatRate(row.rateToCnyMicros, 4)) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        YubraeDialog(
            onDismissRequest = { editingRow = null },
            title = "自定义 ${row.info.code} 汇率",
            subtitle = row.info.nameZh,
            icon = Icons.Rounded.CurrencyExchange,
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = MoneyAmount.parseRateToMicros(rateInput)
                        if (parsed == null || parsed <= 0L) {
                            errorMsg = "请输入大于 0 的有效汇率"
                        } else {
                            viewModel.saveCustomRate(row.info.code, "CNY", parsed) {
                                editingRow = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingRow = null },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rateInput,
                    onValueChange = { rateInput = it },
                    label = { Text("1 ${row.info.code} = ? CNY") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMsg?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
