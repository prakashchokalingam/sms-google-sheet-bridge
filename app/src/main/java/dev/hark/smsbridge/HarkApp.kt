package dev.hark.smsbridge

import android.app.Application
import dev.hark.smsbridge.work.WorkScheduler

class HarkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        WorkScheduler.schedule(this)
    }
}
