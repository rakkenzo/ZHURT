package kz.zhurt.messenger

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kz.zhurt.messenger.data.Backend
import kz.zhurt.messenger.data.Outbox
import kz.zhurt.messenger.data.ZhurtRepository
import kz.zhurt.messenger.ui.DemoApp
import kz.zhurt.messenger.ui.LiveApp
import kz.zhurt.messenger.ui.ZhurtTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ТЗ 8: запрет штатных скриншотов/записи экрана и превью в «недавних»
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val repo = ZhurtApp.repository(this)
        setContent { ZhurtTheme { if (repo != null) LiveApp(repo) else DemoApp() } }
    }
}

/** Один клиент Supabase на процесс. Без ключей в local.properties — демо-режим. */
object ZhurtApp {
    @Volatile private var repo: ZhurtRepository? = null

    fun repository(activity: ComponentActivity): ZhurtRepository? {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) return null
        return repo ?: synchronized(this) {
            repo ?: ZhurtRepository(
                Backend.create(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
                Outbox(File(activity.filesDir, "outbox.json")),
            ).also { repo = it }
        }
    }
}
