package com.yubrae.app.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yubrae.app.MainActivity
import com.yubrae.app.core.contract.CaptureInput
import com.yubrae.app.core.contract.IntentConstants
import com.yubrae.app.core.model.MoneyAmount
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.ParseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 系统分享文本直接记账入口（支持 ACTION_SEND text/plain）：
 * 用户在其他 App 选中消费文本并分享到「余白」，自动解析入账并弹 Toast 提示。
 */
class ShareTextCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (sharedText.isEmpty()) {
            Toast.makeText(applicationContext, "未检测到分享文本内容", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sourceRef = referrer?.host ?: "share"
        val appContext = applicationContext

        lifecycleScope.launch {
            val engine = CaptureRuntimeHelper.captureEngineOrNull(appContext)
            if (engine == null) {
                Toast.makeText(appContext, "余白引擎初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                withTimeoutOrNull(8_000L) {
                    engine.handleIncomingCapture(
                        input = CaptureInput(
                            channel = CaptureChannel.SHARE,
                            sourcePackage = sourceRef,
                            title = sharedSubject.ifBlank { "文本分享" },
                            content = sharedText,
                            timestamp = System.currentTimeMillis()
                        ),
                        showFloatingPrompt = true
                    )
                }
            }

            if (result != null && result.transactionId != null &&
                (result.status == ParseStatus.MATCHED_LOCAL ||
                    result.status == ParseStatus.MATCHED_AI ||
                    result.status == ParseStatus.MERGED_DEDUP)
            ) {
                val draft = result.draft
                val amountText = if (draft != null) {
                    MoneyAmount.format(draft.origAmountMicros, draft.origCurrency, showCurrencyCode = true)
                } else ""
                val prefix = if (result.mergedIntoExisting) "已去重融合：" else "余白已自动入账："
                Toast.makeText(appContext, "$prefix$amountText ${draft?.merchant.orEmpty()}".trim(), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(appContext, "未能直接识别该账单文本，已打开快捷记账", Toast.LENGTH_SHORT).show()
                startActivity(
                    Intent(this@ShareTextCaptureActivity, MainActivity::class.java).apply {
                        putExtra(IntentConstants.EXTRA_OPEN_QUICK_RECORD, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
            finish()
        }
    }
}
