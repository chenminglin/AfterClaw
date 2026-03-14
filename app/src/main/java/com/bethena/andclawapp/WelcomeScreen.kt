package com.bethena.andclawapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 欢迎页面：应用启动后的初始引导页
 */
@Composable
fun WelcomeScreen(
    onStart: () -> Unit, // 点击“开始初始化”的回调
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(hostBackgroundBrush())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "AfterClaw",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "点击开始后，应用才会启动前台服务并初始化 OpenClaw 运行环境。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFBFDBFE),
                )
            }

            StatusCard(
                title = "准备开始",
                detail = "初始化会按顺序完成设备检查、运行环境准备、OpenClaw 安装和本地网关启动。",
                accent = Color(0xFF38BDF8),
            )

            DetailCard(
                title = "启动后会执行",
                accent = Color(0xFFF59E0B),
                lines = listOf(
                    "1. 检查设备环境",
                    "2. 准备 Ubuntu 与 Node 运行环境",
                    "3. 安装或修复 OpenClaw",
                    "4. 启动本地 Gateway 并生成访问地址",
                ),
            )

            FilledTonalButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "开始初始化",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
