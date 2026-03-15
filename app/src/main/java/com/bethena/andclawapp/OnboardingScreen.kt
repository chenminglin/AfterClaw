package com.bethena.andclawapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val controller = context.hostController()
    val state = rememberOnboardingScreenState(controller)
    val hostState by controller.state.collectAsState()
    val s = LocalAppStrings.current

    LaunchedEffect(Unit) {
        state.start()
    }

    if (state.isDone) {
        LaunchedEffect(Unit) {
            onFinished()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(hostBackgroundBrush())
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = s.onboardingTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFF8FAFC),
                fontWeight = FontWeight.Bold,
            )

            if (state.isLoading && state.currentStep == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (state.error != null) {
                DetailCard(
                    title = "Error",
                    accent = Color(0xFFF97316),
                    lines = listOf(state.error!!),
                )
                ActionRow(
                    primaryLabel = "Retry",
                    onPrimary = { state.start() },
                    secondaryLabel = s.resetOnboarding,
                    onSecondary = { state.reset() }
                )
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = s.back)
                }
            } else {
                state.currentStep?.let { step ->
                    // 如果检测到了 OAuth URL 且当前是 note 或是 text 提示粘贴 URL 的步骤，显示该链接
                    val showOAuthLink = hostState.detectedOAuthUrl != null && 
                                       (step.type == "note" || (step.type == "text" && step.message?.contains("URL", ignoreCase = true) == true))

                    if (showOAuthLink) {
                        DetailCard(
                            title = "OAuth URL Detected",
                            accent = Color(0xFF38BDF8),
                            lines = listOf("请点击下方链接进行认证：", hostState.detectedOAuthUrl!!)
                        )
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        FilledTonalButton(
                            onClick = { uriHandler.openUri(hostState.detectedOAuthUrl!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("打开认证页面")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    WizardStepView(
                        step = step,
                        isLoading = state.isLoading,
                        onNext = { state.next(it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { state.reset() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        Text(
                            text = s.resetOnboarding,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun WizardStepView(
    step: WizardStep,
    isLoading: Boolean,
    onNext: (Any?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        step.title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.SemiBold
            )
        }

        step.message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFCBD5E1)
            )
        }

        when (step.type) {
            "note" -> {
                ActionRow(
                    primaryLabel = "Continue",
                    onPrimary = { onNext(null) }
                )
            }
            "confirm" -> {
                ActionRow(
                    primaryLabel = "Yes",
                    onPrimary = { onNext(true) },
                    secondaryLabel = "No",
                    onSecondary = { onNext(false) }
                )
            }
            "text" -> {
                var textValue by remember(step.id) { mutableStateOf((step.initialValue as? String) ?: "") }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { step.placeholder?.let { Text(it) } },
                    enabled = !isLoading,
                    singleLine = true
                )
                ActionRow(
                    primaryLabel = "Next",
                    onPrimary = { onNext(textValue) }
                )
            }
            "select" -> {
                var selectedValue by remember(step.id) { mutableStateOf(step.initialValue) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    step.options.forEach { option ->
                        val isSelected = option.value == selectedValue
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0x3338BDF8) else Color(0x120B1120)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) { selectedValue = option.value }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedValue = option.value },
                                    enabled = !isLoading
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFFE2E8F0)
                                    )
                                    option.hint?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ActionRow(
                    primaryLabel = "Confirm",
                    onPrimary = { onNext(selectedValue) }
                )
            }
            "multiselect" -> {
                var selectedValues by remember(step.id) { 
                    mutableStateOf((step.initialValue as? List<*>)?.toSet() ?: emptySet<Any?>()) 
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    step.options.forEach { option ->
                        val isSelected = selectedValues.contains(option.value)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0x3338BDF8) else Color(0x120B1120)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLoading) { 
                                    selectedValues = if (isSelected) {
                                        selectedValues - option.value
                                    } else {
                                        selectedValues + option.value
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { 
                                        selectedValues = if (isSelected) {
                                            selectedValues - option.value
                                        } else {
                                            selectedValues + option.value
                                        }
                                    },
                                    enabled = !isLoading
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFFE2E8F0)
                                    )
                                    option.hint?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                ActionRow(
                    primaryLabel = "Confirm",
                    onPrimary = { onNext(selectedValues.toList()) }
                )
            }
            else -> {
                Text("Unsupported step type: ${step.type}", color = Color.Red)
                ActionRow(
                    primaryLabel = "Skip",
                    onPrimary = { onNext(null) }
                )
            }
        }
    }
}
