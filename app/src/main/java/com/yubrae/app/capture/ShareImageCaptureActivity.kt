package com.yubrae.app.capture

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.yubrae.app.MainActivity
import com.yubrae.app.core.contract.IntentConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 系统图片分享收单入口（处理 `ACTION_SEND` 图片分享 Intent）：
 * - 从 EXTRA_STREAM 安全流式读取并缩放（最大边 1600px，JPEG 88 压缩）写入私有目录 receipts/；
 * - 携带 EXTRA_RECEIPT_IMAGE_PATH 跳转 MainActivity 触发智能识图记账，随后立即 finish()。
 */
class ShareImageCaptureActivity : Activity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incoming = intent
        if (incoming == null ||
            (incoming.action != Intent.ACTION_SEND && incoming.action != Intent.ACTION_SEND_MULTIPLE)
        ) {
            finish()
            return
        }

        val imageUri = extractImageUri(incoming)
        if (imageUri == null) {
            Toast.makeText(applicationContext, "未检测到分享的图片内容", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        activityScope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                runCatching { copyAndResizeReceiptImage(imageUri) }.getOrNull()
            }

            if (savedFile != null && savedFile.exists() && savedFile.length() > 0L) {
                runCatching {
                    startActivity(
                        Intent(this@ShareImageCaptureActivity, MainActivity::class.java).apply {
                            putExtra(IntentConstants.EXTRA_RECEIPT_IMAGE_PATH, savedFile.absolutePath)
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                    )
                }.onFailure {
                    Toast.makeText(applicationContext, "打开余白识图界面失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext, "无法读取分享的小票图片", Toast.LENGTH_SHORT).show()
            }

            if (!isFinishing) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun extractImageUri(intent: Intent): Uri? {
        if (intent.action == Intent.ACTION_SEND) {
            val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (streamUri != null) return streamUri
        } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uriList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            val first = uriList?.firstOrNull()
            if (first != null) return first
        }
        return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun copyAndResizeReceiptImage(uri: Uri): File? {
        val receiptsDir = File(filesDir, RECEIPTS_DIR_NAME).apply { mkdirs() }
        val destFile = File(receiptsDir, "receipt_${System.currentTimeMillis()}.jpg")

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }

        val sampleSize = calculateInSampleSize(
            rawWidth = boundsOpts.outWidth,
            rawHeight = boundsOpts.outHeight,
            maxSidePx = MAX_SIDE_PX
        )
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }

        if (decoded != null) {
            var scaledBitmap: Bitmap? = null
            var opaqueBitmap: Bitmap? = null
            try {
                val w = decoded.width
                val h = decoded.height
                val maxDim = maxOf(w, h)
                val targetBitmap = if (maxDim > MAX_SIDE_PX && w > 0 && h > 0) {
                    val scale = MAX_SIDE_PX.toFloat() / maxDim.toFloat()
                    val targetW = (w * scale).toInt().coerceAtLeast(1)
                    val targetH = (h * scale).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                        if (it !== decoded) scaledBitmap = it
                    }
                } else {
                    decoded
                }

                val exportBitmap = if (targetBitmap.hasAlpha()) {
                    Bitmap.createBitmap(
                        targetBitmap.width,
                        targetBitmap.height,
                        Bitmap.Config.ARGB_8888
                    ).also { whiteBg ->
                        opaqueBitmap = whiteBg
                        val canvas = Canvas(whiteBg)
                        canvas.drawColor(Color.WHITE)
                        canvas.drawBitmap(targetBitmap, 0f, 0f, null)
                    }
                } else {
                    targetBitmap
                }

                val compressed = FileOutputStream(destFile).use { out ->
                    exportBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                if (!compressed) {
                    runCatching { destFile.delete() }
                    return null
                }
                return destFile
            } finally {
                opaqueBitmap?.recycle()
                scaledBitmap?.recycle()
                decoded.recycle()
            }
        }

        // 无法直接解码 Bitmap 时退回流式拷贝
        val copiedBytes = contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: 0L
        if (copiedBytes <= 0L) {
            runCatching { destFile.delete() }
            return null
        }
        return destFile
    }

    private fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        maxSidePx: Int
    ): Int {
        if (rawWidth <= 0 || rawHeight <= 0) return 1
        var inSampleSize = 1
        if (rawWidth > maxSidePx || rawHeight > maxSidePx) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while ((halfWidth / inSampleSize) >= maxSidePx ||
                (halfHeight / inSampleSize) >= maxSidePx
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val RECEIPTS_DIR_NAME = "receipts"
        private const val MAX_SIDE_PX = 1600
        private const val JPEG_QUALITY = 88
    }
}
