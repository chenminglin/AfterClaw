package com.bethena.andclawapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bethena.andclawapp.ui.theme.Android_clawTheme

/**
 * 应用主入口 Activity
 * 
 * 职责：
 * 1. 初始化 Activity 并配置全屏（EdgeToEdge）。
 * 2. 设置 Compose 视图内容。
 * 3. 作为 UI 层的根容器，挂载 [HostRootScreen]。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 开启边缘到边缘支持，使 UI 内容可以延伸到系统状态栏和导航栏下方
        enableEdgeToEdge()
        
        setContent {
            // 应用自定义的 Compose 主题
            Android_clawTheme {
                // 挂载宿主根屏幕，该屏幕负责页面的路由分发（欢迎页、初始化流程、主控台、配置等）
                HostRootScreen()
            }
        }
    }
}
