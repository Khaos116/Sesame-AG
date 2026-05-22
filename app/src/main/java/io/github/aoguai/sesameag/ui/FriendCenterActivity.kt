package io.github.aoguai.sesameag.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aoguai.sesameag.ui.screen.FriendCenterScreen
import io.github.aoguai.sesameag.ui.theme.AppTheme
import io.github.aoguai.sesameag.ui.theme.ThemeManager

class FriendCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userId = intent.getStringExtra("userId").orEmpty()
        val userName = intent.getStringExtra("userName").orEmpty()
        setContent {
            val isDynamicColor by ThemeManager.isDynamicColor.collectAsStateWithLifecycle()
            AppTheme(dynamicColor = isDynamicColor) {
                FriendCenterScreen(
                    userId = userId,
                    userName = userName,
                    onBack = { finish() }
                )
            }
        }
    }
}

