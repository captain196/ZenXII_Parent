package com.schoolsync.parent

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.schoolsync.parent.data.local.TokenManager
import com.schoolsync.parent.ui.navigation.AppNavGraph
import com.schoolsync.parent.ui.theme.LocalAppColors
import com.schoolsync.parent.ui.theme.SchoolSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by tokenManager.themeMode.collectAsState(initial = "system")
            val systemDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark  // "system" follows OS
            }

            SchoolSyncTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalAppColors.current.bgStart
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
