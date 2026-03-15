package com.bethena.andclawapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.bethena.andclawapp.host.AndClawHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class WizardStepOption(
    val value: Any?,
    val label: String,
    val hint: String? = null,
)

data class WizardStep(
    val id: String,
    val type: String,
    val title: String? = null,
    val message: String? = null,
    val options: List<WizardStepOption> = emptyList(),
    val initialValue: Any? = null,
    val placeholder: String? = null,
    val sensitive: Boolean = false,
)

class OnboardingScreenState(
    private val hostController: AndClawHostController,
    private val scope: CoroutineScope,
) {
    var sessionId by mutableStateOf<String?>(null)
    var currentStep by mutableStateOf<WizardStep?>(null)
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var isDone by mutableStateOf(false)

    fun start() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            error = null
            try {
                val result = hostController.callGatewayRpc("wizard.start", JSONObject().apply {
                    put("mode", "local")
                })
                sessionId = result.optString("sessionId")
                isDone = result.optBoolean("done", false)
                if (!isDone) {
                    currentStep = result.optJSONObject("step")?.toWizardStep()
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to start onboarding"
            } finally {
                isLoading = false
            }
        }
    }

    fun next(answerValue: Any?) {
        val sid = sessionId ?: return
        val stepId = currentStep?.id ?: return
        if (isLoading) return

        scope.launch {
            isLoading = true
            error = null
            try {
                val result = hostController.callGatewayRpc("wizard.next", JSONObject().apply {
                    put("sessionId", sid)
                    put("answer", JSONObject().apply {
                        put("stepId", stepId)
                        put("value", answerValue ?: JSONObject.NULL)
                    })
                })
                isDone = result.optBoolean("done", false)
                if (!isDone) {
                    currentStep = result.optJSONObject("step")?.toWizardStep()
                } else {
                    currentStep = null
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("wizard not found", ignoreCase = true)) {
                    // 如果会话丢失，尝试重新启动向导
                    sessionId = null
                    currentStep = null
                    start()
                } else {
                    error = msg.ifBlank { "Failed to submit answer" }
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun cancel() {
        val sid = sessionId ?: return
        scope.launch {
            try {
                hostController.callGatewayRpc("wizard.cancel", JSONObject().apply {
                    put("sessionId", sid)
                })
            } catch (e: Exception) {
                // ignore
            }
            sessionId = null
            currentStep = null
            hostController.clearDetectedOAuthUrl()
        }
    }

    fun reset() {
        cancel()
        hostController.resetOpenClawConfig()
        start()
    }
}

private fun JSONObject.toWizardStep(): WizardStep {
    val optionsArray = optJSONArray("options")
    val options = mutableListOf<WizardStepOption>()
    if (optionsArray != null) {
        for (i in 0 until optionsArray.length()) {
            val opt = optionsArray.optJSONObject(i) ?: continue
            options.add(
                WizardStepOption(
                    value = opt.opt("value"),
                    label = opt.optString("label"),
                    hint = opt.optString("hint").takeIf { it.isNotBlank() }
                )
            )
        }
    }

    return WizardStep(
        id = optString("id"),
        type = optString("type"),
        title = optString("title").takeIf { it.isNotBlank() },
        message = optString("message").takeIf { it.isNotBlank() },
        options = options,
        initialValue = opt("initialValue"),
        placeholder = optString("placeholder").takeIf { it.isNotBlank() },
        sensitive = optBoolean("sensitive", false)
    )
}

@Composable
fun rememberOnboardingScreenState(
    hostController: AndClawHostController,
    scope: CoroutineScope = rememberCoroutineScope(),
): OnboardingScreenState {
    return remember(hostController, scope) {
        OnboardingScreenState(hostController, scope)
    }
}
