package dev.crossfeed

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.crossfeed.core.LinkParser
import dev.crossfeed.core.Prefs
import dev.crossfeed.ui.ResolveOverlay
import dev.crossfeed.ui.theme.CrossfeedTheme

class ResolveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blurBehind()
        dev.crossfeed.ui.theme.ThemeSeed.pkg = Prefs(this).primary.pkg
        val url = urlFrom(intent)
        if (url == null) {
            finish()
            return
        }
        setContent {
            CrossfeedTheme {
                ResolveOverlay(url = url, onDone = { dismiss() })
            }
        }
    }

    private fun blurBehind() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (windowManager.isCrossWindowBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 48 }
        }
    }

    private fun dismiss() {
        finish()
        overridePendingTransition(0, 0)
    }

    private fun urlFrom(intent: Intent?): String? {
        if (intent == null) return null
        intent.data?.toString()?.let { return it }
        return LinkParser.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}
