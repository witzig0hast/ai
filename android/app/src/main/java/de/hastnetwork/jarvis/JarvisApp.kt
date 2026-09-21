package de.hastnetwork.jarvis

import android.app.Application
import de.hastnetwork.jarvis.di.AppContainer

/**
 * Application class. Owns the single [AppContainer] service locator instance
 * for the process's lifetime (manual DI - see [AppContainer]'s doc comment
 * for why this project doesn't use Hilt/Dagger).
 */
class JarvisApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
