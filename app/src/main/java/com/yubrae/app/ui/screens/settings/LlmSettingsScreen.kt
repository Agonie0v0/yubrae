package com.yubrae.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NetworkCheck
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yubrae.app.ui.components.CompactSwitchRow
import com.yubrae.app.ui.theme.KairoTheme
import com.yubrae.app.ui.viewmodel.KairoMainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LlmSettingsScreen(
    viewModel: KairoMainViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val testResult by viewModel.llmTestResult.collectAsStateWithLifecycle()
    val semantic = KairoTheme.semantic

    var llmEnabled by remember(settings.llmEnabled) { mutableStateOf(settings.llmEnabled) }
    var baseUrl by remember(settings.llmBaseUrl) { mutableStateOf(settings.llmBaseUrl) }
    var apiKey by remember(settings.llmApiKey) { mutableStateOf(settings.llmApiKey) }
    var model by remember(settings.llmModel) { mutableStateOf(settings.llmModel) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 大模型") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                CompactSwitchRow(
                    title = "启用 AI 兜底解析",
                    checked = llmEnabled,
                    onCheckedChange = { llmEnabled = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("预设服务商", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = baseUrl.contains("deepseek", ignoreCase = true),
                        onClick = {
                            baseUrl = "https://api.deepseek.com/v1"
                            model = "deepseek-chat"
                        },
                        label = { Text("DeepSeek") }
                    )
                    FilterChip(
                        selected = baseUrl.contains("openai.com", ignoreCase = true),
                        onClick = {
                            baseUrl = "https://api.openai.com/v1"
                            model = "gpt-4o-mini"
                        },
                        label = { Text("OpenAI") }
                    )
                    FilterChip(
                        selected = baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true),
                        onClick = {
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                            model = "gemini-2.0-flash"
                        },
                        label = { Text("Gemini") }
                    )
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "显示/隐藏密钥"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                placeholder = { Text("gpt-4o-mini / deepseek-chat / gemini-2.0-flash") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.updateSettings {
                            it.copy(
                                llmEnabled = llmEnabled,
                                llmBaseUrl = baseUrl.trim(),
                                llmApiKey = apiKey.trim(),
                                llmModel = model.trim()
                            )
                        }
                        viewModel.showMessage("AI 配置已保存")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存配置")
                }

                Button(
                    onClick = {
                        val updated = settings.copy(
                            llmEnabled = llmEnabled,
                            llmBaseUrl = baseUrl.trim(),
                            llmApiKey = apiKey.trim(),
                            llmModel = model.trim()
                        )
                        viewModel.saveAndTestLlm(updated)
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试中...")
                    } else {
                        Icon(Icons.Rounded.NetworkCheck, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试连通性")
                    }
                }
            }

            testResult?.let { result ->
                val ok = result.isSuccess
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (ok) semantic.incomeContainer.copy(alpha = 0.6f) else semantic.expenseContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, if (ok) semantic.income else semantic.expense),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.fold(
                            onSuccess = { "连通性测试通过：$it" },
                            onFailure = { "测试失败：${it.message ?: "请求异常"}" }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ok) semantic.income else semantic.expense,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
