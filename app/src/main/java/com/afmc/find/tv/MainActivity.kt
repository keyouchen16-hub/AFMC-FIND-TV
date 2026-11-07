package com.afmc.find.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.afmc.find.tv.core.Prefs
import com.afmc.find.tv.core.SearchEngine
import com.afmc.find.tv.ui.AfmcTheme
import com.afmc.find.tv.ui.TvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 预热各搜索源的 TCP/TLS 连接，首轮搜索可省掉握手时间。
        CoroutineScope(Dispatchers.IO).launch {
            SearchEngine(Prefs(this@MainActivity)).warmUp()
        }

        setContent {
            AfmcTheme {
                TvApp()
            }
        }
    }
}
