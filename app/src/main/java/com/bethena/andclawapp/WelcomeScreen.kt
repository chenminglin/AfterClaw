package com.bethena.andclawapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    val s = LocalAppStrings.current
    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(hostBackgroundBrush())
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = s.appTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = s.welcomeDesc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFBFDBFE),
                )
            }

            StatusCard(
                title = s.readyToStartTitle,
                detail = s.readyToStartDesc,
                accent = Color(0xFF38BDF8),
            )

            DetailCard(
                title = s.whatHappensTitle,
                accent = Color(0xFFF59E0B),
                lines = s.steps,
            )

            FilledTonalButton(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = s.startInit,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 语言切换按钮
        TextButton(
            onClick = { LanguageManager.toggle() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(innerPadding)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Text(
                text = s.languageToggle,
                color = Color(0xFFD6EEFF),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
}
