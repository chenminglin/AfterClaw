package com.bethena.andclawapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 宿主页面的背景渐变画刷
 */
fun hostBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color(0xFF08111F),
            Color(0xFF0E223B),
            Color(0xFF16365B),
        )
    )
}

/**
 * 状态卡片：展示标题、详情以及可选的进度条
 */
@Composable
fun StatusCard(
    title: String,
    detail: String,
    accent: Color,
    progressLabel: String? = null,
    progress: Float? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1AF8FAFC)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFE2E8F0),
            )
            progressLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBFDBFE),
                    fontFamily = FontFamily.Monospace,
                )
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * 初始化步骤卡片：展示步骤序号、标题、状态徽章和详情
 */
@Composable
fun InitStepCard(step: InitStepUiModel) {
    val s = LocalAppStrings.current
    val (accent, badgeText, badgeColor) =
        when (step.status) {
            InitStepStatus.Pending -> Triple(Color(0xFF64748B), s.pending, Color(0xFF94A3B8))
            InitStepStatus.InProgress -> Triple(Color(0xFF38BDF8), s.inProgress, Color(0xFF7DD3FC))
            InitStepStatus.Completed -> Triple(Color(0xFF34D399), s.completed, Color(0xFF6EE7B7))
            InitStepStatus.Failed -> Triple(Color(0xFFF97316), s.failed, Color(0xFFFDA4AF))
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(34.dp)
                    .background(accent.copy(alpha = 0.18f), RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = step.number.toString(),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF8FAFC),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = step.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE2E8F0),
                )
            }
        }
    }
}

/**
 * 操作按钮行：支持主操作按钮和可选的副操作按钮
 */
@Composable
fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onPrimary,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = primaryLabel,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = secondaryLabel,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 日志控制台卡片：带标题的滚动日志展示区域
 */
@Composable
fun LogConsoleCard(
    title: String,
    accent: Color,
    lines: List<String>,
    emptyMessage: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (lines.isEmpty()) {
                Text(
                    text = emptyMessage,
                    color = Color(0xFFBFDBFE),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                SelectionContainer(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(lines) { line ->
                            Text(
                                text = line,
                                color = Color(0xFFE2E8F0),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 访问控制卡片：展示 Gateway 地址、Token 以及可复制的访问链接
 */
@Composable
fun AccessCard(
    title: String,
    accent: Color,
    gatewayAddress: String,
    gatewayToken: String,
    accessLink: String,
) {
    val s = LocalAppStrings.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = s.gateway,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = gatewayAddress,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = s.token,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = gatewayToken,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = s.link,
                        color = Color(0xFFE2E8F0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = accessLink,
                        color = Color(0xFFBFDBFE),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(accessLink))
                    Toast.makeText(context, s.linkCopied, Toast.LENGTH_SHORT).show()
                },
            ) {
                Text(
                    text = s.copyLink,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 详情卡片：展示带标题的多行详情文字
 */
@Composable
fun DetailCard(
    title: String,
    accent: Color,
    lines: List<String>,
    monospace: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x120B1120)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(12.dp)
                        .background(accent, RoundedCornerShape(999.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            lines.forEach { line ->
                Text(
                    text = line,
                    color = Color(0xFFE2E8F0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                )
            }
        }
    }
}
