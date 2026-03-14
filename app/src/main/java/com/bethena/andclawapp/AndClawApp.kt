package com.bethena.andclawapp

import android.app.Application
import android.content.Context
import com.bethena.andclawapp.host.AndClawHostController
import com.bethena.andclawapp.host.OpenClawConfigRepository

class AndClawApp : Application() {
    val hostController: AndClawHostController by lazy {
        AndClawHostController(this)
    }

    val openClawConfigRepository: OpenClawConfigRepository by lazy {
        OpenClawConfigRepository(hostController)
    }
}

fun Context.hostController(): AndClawHostController {
    return (applicationContext as AndClawApp).hostController
}

fun Context.openClawConfigRepository(): OpenClawConfigRepository {
    return (applicationContext as AndClawApp).openClawConfigRepository
}
