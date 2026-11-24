package com.yubrae.app.ui.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yubrae.app.ui.components.YubraeBrandMark
import com.yubrae.app.ui.theme.KairoTheme

/**
 * 余白应用锁（指纹 / 面容 / 系统锁屏密码）工具。
 */
object BiometricLockHelper {

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val authenticators = allowedAuthenticatorsForSdk()
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "验证身份解锁余白",
        subtitle: String = "使用指纹、面容或设备密码解锁",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val message = errString.toString().trim().ifEmpty {
                    "身份验证未完成 ($errorCode)"
                }
                onFailure(message)
            }
        }

        try {
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfoBuilder.setAllowedAuthenticators(allowedAuthenticatorsForSdk())
            } else {
                promptInfoBuilder
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                    .setNegativeButtonText("使用系统锁取消")
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onFailure(e.message ?: "无法启动生物识别验证")
        }
    }

    private fun allowedAuthenticatorsForSdk(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
    }
}

fun canAuthenticate(context: Context): Boolean = BiometricLockHelper.canAuthenticate(context)

fun authenticate(
    activity: FragmentActivity,
    title: String = "验证身份解锁余白",
    subtitle: String = "使用指纹、面容或设备密码解锁",
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) = BiometricLockHelper.authenticate(
    activity = activity,
    title = title,
    subtitle = subtitle,
    onSuccess = onSuccess,
    onFailure = onFailure
)

/**
 * 余白 · YUBRAE 隐私锁屏全屏遮罩：进入时自动发起一次身份认证。
 */
@Composable
fun AppLockOverlay(
    onRequestUnlock: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onRequestUnlock()
    }

    val isDark = KairoTheme.semantic.isDark
    val primaryColor = MaterialTheme.colorScheme.primary
    val topGlow = if (isDark) {
        primaryColor.copy(alpha = 0.14f)
    } else {
        primaryColor.copy(alpha = 0.09f)
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            topGlow,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                YubraeBrandMark(size = 56.dp)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "余白",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onRequestUnlock,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "解锁",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
