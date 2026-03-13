package com.bethena.andclawapp

import android.app.Application
import android.content.Context
import com.bethena.andclawapp.host.AndClawHostController

class AndClawApp : Application() {
    val hostController: AndClawHostController by lazy {
        AndClawHostController(this)
    }
}

fun Context.hostController(): AndClawHostController {
    return (applicationContext as AndClawApp).hostController
}
