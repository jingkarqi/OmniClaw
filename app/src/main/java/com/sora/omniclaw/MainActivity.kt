package com.sora.omniclaw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sora.omniclaw.navigation.OmniClawNavHost
import com.sora.omniclaw.ui.theme.OmniClawTheme

class MainActivity : ComponentActivity() {
    private val appGraph: AppGraph
        get() = (application as OmniClawApplication).appGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniClawTheme {
                OmniClawNavHost(appGraph = appGraph)
            }
        }
    }
}
