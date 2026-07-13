package dev.placeholder.llmd

object LlmdNativeServer {
    init {
        System.loadLibrary("llmd_desktop")
    }

    external fun startServer(): Boolean
    external fun stopServer()
}
