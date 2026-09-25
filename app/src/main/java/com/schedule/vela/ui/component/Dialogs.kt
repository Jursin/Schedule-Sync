package com.schedule.vela.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.schedule.vela.ui.theme.WindowBlurEffect
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

// 通用确认对话框：[content] 用于进度、输入框等自定义正文，未传 [message] 时正文即由它提供。
// [cancelText] 为 null 时只显示确认按钮。
@Composable
fun ConfirmDialog(
    title: String,
    appBlur: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    cancelText: String? = "取消",
    destructive: Boolean = false,
    message: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
        content = {
            WindowBlurEffect(useBlur = appBlur)
            val dismiss = LocalDismissState.current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (message != null) Text(text = message)
                content?.invoke(this)
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (cancelText != null) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = { dismiss?.invoke() },
                            text = cancelText,
                        )
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onConfirm,
                        text = confirmText,
                        colors =
                            if (destructive) {
                                ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.textButtonColorsPrimary()
                            },
                    )
                }
            }
        },
    )
}
