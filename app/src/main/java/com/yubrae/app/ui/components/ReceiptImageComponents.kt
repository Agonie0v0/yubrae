package com.yubrae.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal object ReceiptImageUtils {
    private const val MAX_EDGE_PX = 1600

    private fun receiptsDir(context: Context): File =
        File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }

    private fun computeInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var inSampleSize = 1
        val longest = maxOf(width, height)
        while (longest / inSampleSize > maxEdge) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return runCatching {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled !== bitmap) bitmap.recycle()
            scaled
        }.getOrDefault(bitmap)
    }

    suspend fun savePickedImageToReceipts(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val dir = receiptsDir(context)
        val destFile = File(dir, "receipt_${System.currentTimeMillis()}.jpg")

        val decoded = runCatching {
            val orientation = resolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
                }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                }?.let { bmp ->
                    scaleBitmapDown(rotateIfNeeded(bmp, orientation), MAX_EDGE_PX)
                }
            } else {
                null
            }
        }.getOrNull()

        if (decoded != null) {
            return@withContext runCatching {
                FileOutputStream(destFile).use { out ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                decoded.recycle()
                destFile.absolutePath
            }.getOrNull()
        }

        // 若位图解码不可用，回退流式复制原始字节
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
                destFile.absolutePath
            }
        }.getOrNull()
    }

    suspend fun saveBitmapToReceipts(context: Context, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val destFile = File(receiptsDir(context), "receipt_cam_${System.currentTimeMillis()}.jpg")
            val longest = maxOf(bitmap.width, bitmap.height)
            val toSave = if (longest > MAX_EDGE_PX) {
                val ratio = MAX_EDGE_PX.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            FileOutputStream(destFile).use { out ->
                toSave.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (toSave !== bitmap) toSave.recycle()
            destFile.absolutePath
        }.getOrNull()
    }

    suspend fun loadThumbnailBitmap(path: String, maxSizePx: Int = 600): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || !file.isFile) return@withContext null
        runCatching {
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxSizePx)
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@withContext null
            rotateIfNeeded(bmp, orientation)
        }.getOrNull()
    }
}

@Composable
internal fun ReceiptThumbnailImage(
    path: String,
    modifier: Modifier = Modifier,
    maxSizePx: Int = 480,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(path, maxSizePx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, maxSizePx) {
        bitmap = ReceiptImageUtils.loadThumbnailBitmap(path, maxSizePx)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "小票图片",
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun ReceiptZoomPreviewDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var bitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imagePath) {
        bitmap = ReceiptImageUtils.loadThumbnailBitmap(imagePath, maxSizePx = 1800)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss)
                .padding(16.dp)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "小票原图预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "无法加载图片或文件已被清理",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 4.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭预览",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
