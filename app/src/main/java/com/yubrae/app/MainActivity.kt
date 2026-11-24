package com.yubrae.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.yubrae.app.core.contract.IntentConstants
import com.yubrae.app.ui.navigation.KairoAppRoot

class MainActivity : FragmentActivity() {

    private var launchOpenQuickRecord by mutableStateOf(false)
    private var launchEditTransactionId by mutableStateOf<Long?>(null)
    private var launchInitialTab by mutableStateOf<Int?>(null)
    private var launchOpenSmartInput by mutableStateOf(false)
    private var launchReceiptImagePath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseLaunchIntent(intent)

        val container = (applicationContext as KairoApplication).container
        setContent {
            KairoAppRoot(
                container = container,
                initialOpenQuickRecord = launchOpenQuickRecord,
                initialEditTransactionId = launchEditTransactionId,
                initialTab = launchInitialTab,
                initialOpenSmartInput = launchOpenSmartInput,
                initialReceiptImagePath = launchReceiptImagePath,
                onConsumeLaunchArgs = {
                    launchOpenQuickRecord = false
                    launchEditTransactionId = null
                    launchInitialTab = null
                    launchOpenSmartInput = false
                    launchReceiptImagePath = null
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseLaunchIntent(intent)
    }

    private fun parseLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val editId = intent.getLongExtra(IntentConstants.EXTRA_EDIT_TRANSACTION_ID, -1L)
        if (editId > 0L) {
            launchEditTransactionId = editId
        }
        val openQuick = intent.getBooleanExtra(IntentConstants.EXTRA_OPEN_QUICK_RECORD, false) ||
            intent.action == IntentConstants.ACTION_ADD_RECORD
        if (openQuick) {
            launchOpenQuickRecord = true
        }
        if (intent.hasExtra(IntentConstants.EXTRA_INITIAL_TAB)) {
            launchInitialTab = intent.getIntExtra(IntentConstants.EXTRA_INITIAL_TAB, 0)
        }
        if (intent.getBooleanExtra(IntentConstants.EXTRA_OPEN_SMART_INPUT, false)) {
            launchOpenSmartInput = true
        }
        val receiptPath = intent.getStringExtra(IntentConstants.EXTRA_RECEIPT_IMAGE_PATH)
        if (!receiptPath.isNullOrBlank()) {
            launchReceiptImagePath = receiptPath
        }
    }
}
