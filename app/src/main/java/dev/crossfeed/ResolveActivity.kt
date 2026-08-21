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
                ResolveOverlay(
                    url = url,
                    shared = intent?.action == Intent.ACTION_SEND,
                    onDone = { dismiss() },
                )
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

    /**
     * Another app can start this activity with any url at all, and resolving one means fetching it.
     * Only the services crossfeed actually knows how to read are followed.
     */
    private fun urlFrom(intent: Intent?): String? {
        if (intent == null) return null
        val candidate = intent.data?.toString()
            ?: LinkParser.firstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            ?: return null
        return candidate.takeIf { LinkParser.isKnown(it) }
    }
}
