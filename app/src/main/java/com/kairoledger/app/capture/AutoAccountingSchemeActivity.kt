package com.kairoledger.app.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.kairoledger.app.MainActivity
import com.kairoledger.app.core.contract.IntentConstants
import com.kairoledger.app.core.model.MoneyAmount
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.ParseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * URI Scheme 外部记账入口：
 * 支持 `kairoledger://addbill?money=15.00&currency=USD&type=EXPENSE&category=数字订阅&account=美元账户&remark=OpenAI`
 */
class AutoAccountingSchemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val params = ExternalBillProtocolParser.fromUri(uri)
        if (params == null) {
            Toast.makeText(applicationContext, "账单参数不完整，已打开快捷记账", Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    putExtra(IntentConstants.EXTRA_OPEN_QUICK_RECORD, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            )
            finish()
            return
        }

        val appContext = applicationContext
        lifecycleScope.launch {
            val orchestrator = CaptureRuntimeHelper.orchestratorOrNull(appContext)
            if (orchestrator == null) {
                Toast.makeText(appContext, "棱账引擎尚未就绪，请稍后重试", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                orchestrator.handleExternalBillRecord(
                    channel = CaptureChannel.AUTO_ACCOUNTING,
                    sourcePackage = uri.scheme ?: "kairoledger",
                    type = params.type,
                    origAmountMicros = params.origAmountMicros,
                    origCurrency = params.currency,
                    accountNameOrHint = params.accountNameOrHint,
                    categoryName = params.categoryName,
                    merchant = params.merchant,
                    note = params.note,
                    timestamp = params.timestamp,
                    showFloatingPrompt = params.showFloatingPrompt
                )
            }

            val toastText = when (result.status) {
                ParseStatus.MATCHED_LOCAL, ParseStatus.MATCHED_AI, ParseStatus.MERGED_DEDUP -> {
                    val draft = result.draft
                    val formatted = if (draft != null) {
                        MoneyAmount.format(draft.origAmountMicros, draft.origCurrency, showCurrencyCode = true)
                    } else ""
                    "棱账已自动入账 $formatted"
                }
                else -> "自动记账未执行：${result.message}"
            }
            Toast.makeText(appContext, toastText.trim(), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
