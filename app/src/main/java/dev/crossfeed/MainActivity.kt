package dev.crossfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.ListeningService
import dev.crossfeed.core.widget.CrossfeedWidget
import dev.crossfeed.core.player.SourceSetup
import dev.crossfeed.ui.CrossfeedApp
import dev.crossfeed.ui.theme.CrossfeedTheme
import dev.crossfeed.ui.theme.ThemeSeed

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeSeed.pkg = Prefs(this).primary.pkg
        SourceSetup.install()
        if (ListeningService.enabled(this)) ListeningService.rebind(this)
        // whatever the widget is showing is at most half an hour old, so opening the app
        // is the moment to put it right
        CrossfeedWidget.refresh(this)
        Thread { HistoryDb.get(applicationContext).runPendingMaintenance() }.start()
        setContent {
            CrossfeedTheme {
                CrossfeedApp()
            }
        }
    }


}
