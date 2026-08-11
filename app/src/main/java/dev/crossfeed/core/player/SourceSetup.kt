package dev.crossfeed.core.player

object SourceSetup {

    fun install() {
        Sources.register(LocalSource)
    }
}
