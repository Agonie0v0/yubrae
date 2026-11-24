package com.yubrae.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.ui.components.CompactSwitchRow
import com.yubrae.app.ui.components.ConfirmActionDialog
import com.yubrae.app.ui.components.EmptyStateCard
import com.yubrae.app.ui.components.KairoFormats
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.theme.tabular
import com.yubrae.app.ui.viewmodel.KairoMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAndAliasesScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val aliases by viewModel.accountAliases.collectAsStateWithLifecycle()
    val rules by viewModel.parseRules.collectAsStateWithLifecycle()
    val accounts by viewModel.activeAccounts.collectAsStateWithLifecycle()
    val sandboxState by viewModel.sandboxState.collectAsStateWithLifecycle()

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingAlias by remember { mutableStateOf<AccountAliasEntity?>(null) }
    var showCreateAlias by remember { mutableStateOf(false) }
    var deleteAliasCandidate by remember { mutableStateOf<AccountAliasEntity?>(null) }

    var editingRule by remember { mutableStateOf<ParseRuleEntity?>(null) }
    var showCreateRule by remember { mutableStateOf(false) }
    var deleteRuleCandidate by remember { mutableStateOf<ParseRuleEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("指纹与规则") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                1 -> FloatingActionButton(onClick = { showCreateAlias = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新增指纹")
                }
                2 -> FloatingActionButton(onClick = { showCreateRule = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "新增规则")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("文本沙盒") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("账户指纹 (${aliases.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("正则规则 (${rules.size})") }
                )
            }

            when (selectedTab) {
                0 -> SandboxTestTab(
                    sandboxState = sandboxState,
                    accountMap = accountMap,
                    onRunTest = { title, content, pkg, allowLlm ->
                        viewModel.runSandboxTest(title, content, pkg, allowLlm)
                    }
                )
                1 -> AliasesListTab(
                    aliases = aliases,
                    accountMap = accountMap,
                    onToggleEnabled = { alias, enabled ->
                        viewModel.saveAlias(alias.copy(enabled = enabled))
                    },
                    onCreateAlias = { showCreateAlias = true },
                    onEdit = { editingAlias = it },
                    onDelete = { deleteAliasCandidate = it }
                )
                else -> RulesListTab(
                    rules = rules,
                    onToggleEnabled = { rule, enabled ->
                        viewModel.saveRule(rule.copy(enabled = enabled))
                    },
                    onCreateRule = { showCreateRule = true },
                    onEdit = { editingRule = it },
                    onDelete = { deleteRuleCandidate = it }
                )
            }
        }
    }

    if (showCreateAlias || editingAlias != null) {
        AliasEditorDialog(
            existing = editingAlias,
            accounts = accounts,
            onSave = { alias ->
                viewModel.saveAlias(alias) {
                    showCreateAlias = false
                    editingAlias = null
                }
            },
            onDismiss = {
                showCreateAlias = false
                editingAlias = null
            }
        )
    }

    deleteAliasCandidate?.let { alias ->
        ConfirmActionDialog(
            title = "删除账户指纹？",
            message = "删除模式「${alias.pattern}」后，自动记账将不再按此条件路由账户。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteAlias(alias.id)
                deleteAliasCandidate = null
            },
            onDismiss = { deleteAliasCandidate = null }
        )
    }

    if (showCreateRule || editingRule != null) {
        RuleEditorDialog(
            existing = editingRule,
            onSave = { rule ->
                viewModel.saveRule(rule) {
                    showCreateRule = false
                    editingRule = null
                }
            },
            onDismiss = {
                showCreateRule = false
                editingRule = null
            }
        )
    }

    deleteRuleCandidate?.let { rule ->
        ConfirmActionDialog(
            title = "删除正则规则「${rule.name}」？",
            message = "删除后可能影响对应渠道的本地正则提取能力。",
            confirmText = "删除",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteRule(rule.id)
                deleteRuleCandidate = null
            },
            onDismiss = { deleteRuleCandidate = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SandboxTestTab(
    sandboxState: com.yubrae.app.ui.viewmodel.SandboxTestUiState,
    accountMap: Map<Long, AccountEntity>,
    onRunTest: (title: String, content: String, sourcePackage: String, allowLlm: Boolean) -> Unit
) {
    val semantic = KairoTheme.semantic
    var sourcePackage by remember { mutableStateOf("*") }
    var title by remember { mutableStateOf("银行消费通知") }
    var content by remember { mutableStateOf("您尾号6688信用卡于22日消费USD 15.00，当前可用额度人民币48200元。") }
    var allowLlm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = false,
                onClick = {
                    sourcePackage = "*"
                    title = "外币消费短信"
                    content = "您尾号6688信用卡消费USD 19.99，商户Hetzner Online。"
                },
                label = { Text("外币卡") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    sourcePackage = "com.tencent.mm"
                    title = "微信支付"
                    content = "向星巴克付款32.50元"
                },
                label = { Text("微信付款") }
            )
            FilterChip(
                selected = false,
                onClick = {
                    sourcePackage = "com.wise.android"
                    title = "Wise"
                    content = "You spent €24.80 at Amazon EU"
                },
                label = { Text("Wise 欧元") }
            )
        }

        OutlinedTextField(
            value = sourcePackage,
            onValueChange = { sourcePackage = it },
            label = { Text("来源包名") },
            placeholder = { Text("* 表示通用") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("通知标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("通知/短信正文") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        CompactSwitchRow(
            title = "允许 AI 兜底",
            checked = allowLlm,
            onCheckedChange = { allowLlm = it }
        )

        Button(
            onClick = { onRunTest(title, content, sourcePackage, allowLlm) },
            enabled = !sandboxState.isRunning && content.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (sandboxState.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("解析中...")
            } else {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("运行解析测试")
            }
        }

        if (sandboxState.hasResult) {
            val draft = sandboxState.draft
            if (draft != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = semantic.incomeContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, semantic.income.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "解析成功 (${draft.parserUsed} · 置信度 ${(draft.confidence * 100).toInt()}%)",
                            style = MaterialTheme.typography.titleSmall,
                            color = semantic.income,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "类型：${KairoFormats.transactionTypeLabel(draft.type)} · 金额：${MoneyAmount.format(draft.origAmountMicros, draft.origCurrency, showCurrencyCode = true)}",
                            style = MaterialTheme.typography.bodyMedium.tabular()
                        )
                        val acctName = draft.accountId?.let { accountMap[it]?.name }
                        Text(
                            text = "路由账户：${acctName ?: "未匹配"} ${if (draft.accountHint.isNotBlank()) "(线索: ${draft.accountHint})" else ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "分类：${draft.categoryName.ifBlank { "未匹配" }} · 商户：${draft.merchant.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = semantic.expenseContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, semantic.expense.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = sandboxState.errorMessage ?: "未识别为动账消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = semantic.expense,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AliasesListTab(
    aliases: List<AccountAliasEntity>,
    accountMap: Map<Long, AccountEntity>,
    onToggleEnabled: (AccountAliasEntity, Boolean) -> Unit,
    onCreateAlias: () -> Unit,
    onEdit: (AccountAliasEntity) -> Unit,
    onDelete: (AccountAliasEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (aliases.isEmpty()) {
            item(key = "empty_aliases") {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Rule,
                    title = "暂无账户指纹",
                    actionLabel = "新增指纹",
                    onAction = onCreateAlias
                )
            }
        } else {
            items(aliases, key = { it.id }) { alias ->
                val accName = accountMap[alias.accountId]?.name ?: "账户 #${alias.accountId}"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = alias.matchType,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = accName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Text(
                                text = "模式：${alias.pattern}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                            Text(
                                text = "优先级 ${alias.priority}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alias.enabled,
                            onCheckedChange = { onToggleEnabled(alias, it) }
                        )
                        IconButton(onClick = { onEdit(alias) }) {
                            Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDelete(alias) }) {
                            Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RulesListTab(
    rules: List<ParseRuleEntity>,
    onToggleEnabled: (ParseRuleEntity, Boolean) -> Unit,
    onCreateRule: () -> Unit,
    onEdit: (ParseRuleEntity) -> Unit,
    onDelete: (ParseRuleEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (rules.isEmpty()) {
            item(key = "empty_rules") {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.Rule,
                    title = "暂无规则",
                    actionLabel = "新增规则",
                    onAction = onCreateRule
                )
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(rule.name, style = MaterialTheme.typography.titleSmall)
                                    if (rule.isBuiltIn) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "内置",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${KairoFormats.transactionTypeLabel(rule.transactionType)} · ${rule.sourcePackage} · 优先级 ${rule.priority}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { onToggleEnabled(rule, it) }
                            )
                        }
                        Text(
                            text = rule.regexPattern,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onEdit(rule) }) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("编辑")
                            }
                            TextButton(onClick = { onDelete(rule) }) {
                                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasEditorDialog(
    existing: AccountAliasEntity?,
    accounts: List<AccountEntity>,
    onSave: (AccountAliasEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAccountId by remember { mutableLongStateOf(existing?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var matchType by remember { mutableStateOf(existing?.matchType ?: AliasMatchType.CARD_LAST4) }
    var pattern by remember { mutableStateOf(existing?.pattern ?: "") }
    var priorityInput by remember { mutableStateOf((existing?.priority ?: 70).toString()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增账户指纹" else "编辑账户指纹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("目标账户：", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(accounts, key = { it.id }) { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text("${acc.name}(${acc.currencyCode})") }
                        )
                    }
                }
                Text("匹配方式：", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        listOf(
                            AliasMatchType.CARD_LAST4 to "卡尾号",
                            AliasMatchType.PACKAGE to "包名",
                            AliasMatchType.KEYWORD to "关键词",
                            AliasMatchType.REGEX to "正则"
                        )
                    ) { (type, label) ->
                        FilterChip(
                            selected = matchType == type,
                            onClick = { matchType = type },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配模式") },
                    placeholder = { Text("如 6688 或 com.tencent.mm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = priorityInput,
                    onValueChange = { priorityInput = it },
                    label = { Text("优先级") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                CompactSwitchRow(
                    title = "启用",
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        selectedAccountId == 0L -> errorMsg = "请选择目标账户"
                        pattern.trim().isEmpty() -> errorMsg = "请输入匹配模式"
                        else -> onSave(
                            AccountAliasEntity(
                                id = existing?.id ?: 0L,
                                accountId = selectedAccountId,
                                matchType = matchType,
                                pattern = pattern.trim(),
                                priority = priorityInput.toIntOrNull() ?: 50,
                                enabled = enabled
                            )
                        )
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RuleEditorDialog(
    existing: ParseRuleEntity?,
    onSave: (ParseRuleEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sourcePackage by remember { mutableStateOf(existing?.sourcePackage ?: "*") }
    var channelType by remember { mutableStateOf(existing?.channelType ?: "ANY") }
    var txType by remember { mutableStateOf(existing?.transactionType ?: TransactionType.EXPENSE) }
    var regexPattern by remember { mutableStateOf(existing?.regexPattern ?: "") }
    var amountGroup by remember { mutableStateOf((existing?.amountGroup ?: 1).toString()) }
    var currencyGroup by remember { mutableStateOf((existing?.currencyGroup ?: 0).toString()) }
    var defaultCurrency by remember { mutableStateOf(existing?.defaultCurrency ?: "CNY") }
    var accountHintGroup by remember { mutableStateOf((existing?.accountHintGroup ?: 0).toString()) }
    var merchantGroup by remember { mutableStateOf((existing?.merchantGroup ?: 0).toString()) }
    var defaultCategoryName by remember { mutableStateOf(existing?.defaultCategoryName ?: "其他支出") }
    var priority by remember { mutableStateOf((existing?.priority ?: 60).toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新增正则规则" else "编辑正则规则") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("规则名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sourcePackage, onValueChange = { sourcePackage = it }, label = { Text("包名 (* 为任意)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(TransactionType.EXPENSE to "支出", TransactionType.INCOME to "收入").forEach { (type, label) ->
                        FilterChip(selected = txType == type, onClick = { txType = type }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ANY", "NOTIFICATION", "SMS").forEach { ch ->
                        FilterChip(selected = channelType == ch, onClick = { channelType = ch }, label = { Text(ch) })
                    }
                }
                OutlinedTextField(value = regexPattern, onValueChange = { regexPattern = it }, label = { Text("正则表达式") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amountGroup, onValueChange = { amountGroup = it }, label = { Text("金额组号") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = currencyGroup, onValueChange = { currencyGroup = it }, label = { Text("币种组(0无)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = accountHintGroup, onValueChange = { accountHintGroup = it }, label = { Text("卡尾号组") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = merchantGroup, onValueChange = { merchantGroup = it }, label = { Text("商户组") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = defaultCurrency, onValueChange = { defaultCurrency = it.uppercase() }, label = { Text("默认币种") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = priority, onValueChange = { priority = it }, label = { Text("优先级") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = defaultCategoryName, onValueChange = { defaultCategoryName = it }, label = { Text("默认分类名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val compiledOk = runCatching { Regex(regexPattern) }.isSuccess
                    when {
                        name.trim().isEmpty() -> errorMsg = "请输入规则名称"
                        regexPattern.isBlank() || !compiledOk -> errorMsg = "请输入合法的正则表达式"
                        else -> onSave(
                            ParseRuleEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                sourcePackage = sourcePackage.trim().ifEmpty { "*" },
                                channelType = channelType,
                                regexPattern = regexPattern,
                                transactionType = txType,
                                amountGroup = amountGroup.toIntOrNull() ?: 1,
                                currencyGroup = currencyGroup.toIntOrNull() ?: 0,
                                defaultCurrency = defaultCurrency.trim().ifEmpty { "CNY" },
                                accountHintGroup = accountHintGroup.toIntOrNull() ?: 0,
                                merchantGroup = merchantGroup.toIntOrNull() ?: 0,
                                defaultCategoryName = defaultCategoryName.trim().ifEmpty { "其他支出" },
                                priority = priority.toIntOrNull() ?: 60,
                                enabled = existing?.enabled ?: true,
                                isBuiltIn = existing?.isBuiltIn ?: false
                            )
                        )
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
