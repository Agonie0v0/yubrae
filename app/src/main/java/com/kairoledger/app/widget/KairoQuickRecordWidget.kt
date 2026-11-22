package com.kairoledger.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairoledger.app.MainActivity
import com.kairoledger.app.core.contract.IntentConstants

private enum class QuickWidgetIconKind {
    ADD,
    AI_VOICE,
    ASSETS,
    STATS
}

/**
 * 棱账 4x1 快捷工具条桌面小组件：
 * 提供「记一笔」「语音/AI记」「资产账户」「收支统计」4 个一键直达入口。
 */
class KairoQuickRecordWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quickRecordIntent = Intent(context, MainActivity::class.java).apply {
            action = IntentConstants.ACTION_ADD_RECORD
            putExtra(IntentConstants.EXTRA_OPEN_QUICK_RECORD, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val smartInputIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_SMART_INPUT
            putExtra(IntentConstants.EXTRA_OPEN_SMART_INPUT, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val assetsIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_ASSETS_TAB
            putExtra(IntentConstants.EXTRA_INITIAL_TAB, 1)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val statsIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_STATS_TAB
            putExtra(IntentConstants.EXTRA_INITIAL_TAB, 2)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val addIcon = createQuickIconBitmap(QuickWidgetIconKind.ADD)
        val aiIcon = createQuickIconBitmap(QuickWidgetIconKind.AI_VOICE)
        val assetsIcon = createQuickIconBitmap(QuickWidgetIconKind.ASSETS)
        val statsIcon = createQuickIconBitmap(QuickWidgetIconKind.STATS)

        provideContent {
            GlanceTheme {
                QuickRecordWidgetContent(
                    quickRecordIntent = quickRecordIntent,
                    smartInputIntent = smartInputIntent,
                    assetsIntent = assetsIntent,
                    statsIntent = statsIntent,
                    addIcon = addIcon,
                    aiIcon = aiIcon,
                    assetsIcon = assetsIcon,
                    statsIcon = statsIcon
                )
            }
        }
    }

    companion object {
        private const val ACTION_WIDGET_SMART_INPUT = "com.kairoledger.app.ACTION_WIDGET_SMART_INPUT"
        private const val ACTION_WIDGET_ASSETS_TAB = "com.kairoledger.app.ACTION_WIDGET_ASSETS_TAB"
        private const val ACTION_WIDGET_STATS_TAB = "com.kairoledger.app.ACTION_WIDGET_STATS_TAB"
    }
}

@Composable
private fun QuickRecordWidgetContent(
    quickRecordIntent: Intent,
    smartInputIntent: Intent,
    assetsIntent: Intent,
    statsIntent: Intent,
    addIcon: Bitmap,
    aiIcon: Bitmap,
    assetsIcon: Bitmap,
    statsIcon: Bitmap
) {
    val widgetBg = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFF181F1C)
    )
    val primaryCapsuleBg = ColorProvider(
        day = Color(0xFF2AA876),
        night = Color(0xFF1E8E62)
    )
    val primaryIconBg = ColorProvider(
        day = Color(0xFF228E63),
        night = Color(0xFF16724E)
    )
    val primaryTextColor = ColorProvider(
        day = Color(0xFFFFFFFF),
        night = Color(0xFFFFFFFF)
    )
    val secondaryCapsuleBg = ColorProvider(
        day = Color(0xFFF1F7F4),
        night = Color(0xFF212B26)
    )
    val secondaryTextColor = ColorProvider(
        day = Color(0xFF1A211E),
        night = Color(0xFFE1E8E4)
    )
    val mintBadgeBg = ColorProvider(
        day = Color(0xFF2AA876),
        night = Color(0xFF2AA876)
    )
    val assetsBadgeBg = ColorProvider(
        day = Color(0xFF3B82F6),
        night = Color(0xFF3B82F6)
    )
    val statsBadgeBg = ColorProvider(
        day = Color(0xFFE89B2D),
        night = Color(0xFFE89B2D)
    )

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(widgetBg)
            .cornerRadius(20.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickWidgetActionTile(
            label = "记一笔",
            iconBitmap = addIcon,
            capsuleBg = primaryCapsuleBg,
            iconBadgeBg = primaryIconBg,
            textColor = primaryTextColor,
            isBold = true,
            intent = quickRecordIntent,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        QuickWidgetActionTile(
            label = "语音/AI记",
            iconBitmap = aiIcon,
            capsuleBg = secondaryCapsuleBg,
            iconBadgeBg = mintBadgeBg,
            textColor = secondaryTextColor,
            isBold = false,
            intent = smartInputIntent,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        QuickWidgetActionTile(
            label = "资产账户",
            iconBitmap = assetsIcon,
            capsuleBg = secondaryCapsuleBg,
            iconBadgeBg = assetsBadgeBg,
            textColor = secondaryTextColor,
            isBold = false,
            intent = assetsIntent,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        QuickWidgetActionTile(
            label = "收支统计",
            iconBitmap = statsIcon,
            capsuleBg = secondaryCapsuleBg,
            iconBadgeBg = statsBadgeBg,
            textColor = secondaryTextColor,
            isBold = false,
            intent = statsIntent,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight()
        )
    }
}

@Composable
private fun QuickWidgetActionTile(
    label: String,
    iconBitmap: Bitmap,
    capsuleBg: androidx.glance.unit.ColorProvider,
    iconBadgeBg: androidx.glance.unit.ColorProvider,
    textColor: androidx.glance.unit.ColorProvider,
    isBold: Boolean,
    intent: Intent,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .background(capsuleBg)
            .cornerRadius(14.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clickable(actionStartActivity(intent)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(24.dp)
                .background(iconBadgeBg)
                .cornerRadius(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(iconBitmap),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(14.dp)
            )
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = label,
            style = TextStyle(
                color = textColor,
                fontSize = 10.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

private fun createQuickIconBitmap(kind: QuickWidgetIconKind): Bitmap {
    val size = 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    when (kind) {
        QuickWidgetIconKind.ADD -> {
            canvas.drawLine(36f, 16f, 36f, 56f, strokePaint)
            canvas.drawLine(16f, 36f, 56f, 36f, strokePaint)
        }
        QuickWidgetIconKind.AI_VOICE -> {
            // 四角星芒 + 语音小圆点，一眼识别 AI / 语音速记
            val star = Path().apply {
                moveTo(32f, 12f)
                quadTo(32f, 32f, 52f, 32f)
                quadTo(32f, 32f, 32f, 52f)
                quadTo(32f, 32f, 12f, 32f)
                quadTo(32f, 32f, 32f, 12f)
                close()
            }
            canvas.drawPath(star, fillPaint)
            canvas.drawCircle(54f, 52f, 6f, fillPaint)
            canvas.drawCircle(54f, 18f, 4f, fillPaint)
        }
        QuickWidgetIconKind.ASSETS -> {
            val walletRect = RectF(13f, 20f, 59f, 54f)
            strokePaint.strokeWidth = 6f
            canvas.drawRoundRect(walletRect, 10f, 10f, strokePaint)
            val flapRect = RectF(41f, 31f, 59f, 43f)
            canvas.drawRoundRect(flapRect, 5f, 5f, strokePaint)
            canvas.drawCircle(48f, 37f, 3f, fillPaint)
        }
        QuickWidgetIconKind.STATS -> {
            strokePaint.strokeWidth = 7f
            canvas.drawLine(20f, 54f, 20f, 38f, strokePaint)
            canvas.drawLine(36f, 54f, 36f, 20f, strokePaint)
            canvas.drawLine(52f, 54f, 52f, 30f, strokePaint)
        }
    }
    return bitmap
}

class KairoQuickRecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairoQuickRecordWidget()
}
