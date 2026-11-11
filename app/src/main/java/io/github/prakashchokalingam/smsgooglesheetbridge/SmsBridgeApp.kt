package io.github.prakashchokalingam.smsgooglesheetbridge

import android.app.Application
import io.github.prakashchokalingam.smsgooglesheetbridge.work.WorkScheduler

class SmsBridgeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkScheduler.schedule(this)
    }
}
