package com.harroyuz.ddrchartviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.harroyuz.ddrchartviewer.app.AppViewModel
import com.harroyuz.ddrchartviewer.ui.AppScreen
import com.harroyuz.ddrchartviewer.ui.BjmLoginActivity
import com.harroyuz.ddrchartviewer.ui.DdrTheme

class MainActivity : ComponentActivity() {
    private val model by viewModels<AppViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            val state by model.state.collectAsState()
            val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) model.syncScores()
            }
            DdrTheme { AppScreen(state, model) { login.launch(Intent(this, BjmLoginActivity::class.java)) } }
        }
    }
}
