package com.afmc.find.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.afmc.find.tv.ui.AfmcTheme
import com.afmc.find.tv.ui.TvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AfmcTheme {
                TvApp()
            }
        }
    }
}
