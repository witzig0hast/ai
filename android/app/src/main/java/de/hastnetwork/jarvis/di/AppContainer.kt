package de.hastnetwork.jarvis.di

import android.content.Context
import de.hastnetwork.jarvis.data.local.AppSettings
import de.hastnetwork.jarvis.data.local.SettingsDataStore
import de.hastnetwork.jarvis.data.remote.AuthInterceptor
import de.hastnetwork.jarvis.data.remote.ChatSseClient
import de.hastnetwork.jarvis.data.remote.DynamicBaseUrlInterceptor
import de.hastnetwork.jarvis.data.remote.EventsSocket
import de.hastnetwork.jarvis.data.remote.JarvisApi
import de.hastnetwork.jarvis.data.remote.VoiceSocket
import de.hastnetwork.jarvis.data.repository.AgentRepository
import de.hastnetwork.jarvis.data.repository.BriefingRepository
import de.hastnetwork.jarvis.data.repository.ConversationRepository
import de.hastnetwork.jarvis.data.repository.ReminderRepository
import de.hastnetwork.jarvis.data.repository.StatusRepository
import de.hastnetwork.jarvis.data.repository.VoiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Minimal hand-rolled service locator (no Hilt/Dagger - see task brief:
 * avoids kapt/ksp build fragility we can't verify in this sandbox).
 *
 * One instance is created in [de.hastnetwork.jarvis.JarvisApp] and handed
 * to Activities/ViewModels via `(application as JarvisApp).container`.
 */
class AppContainer(private val appContext: Context) {

    // Retrofit needs *a* valid base URL at construction time; the real host
    // is applied per-request by [DynamicBaseUrlInterceptor] once Settings
    // has loaded (or changes later), so this value is never actually used
    // to reach the network.
    private val placeholderBaseUrl = "http://jarvis.local/"

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsDataStore = SettingsDataStore(appContext)

    // Cached, synchronously-readable snapshot of Settings for interceptors
    // and the WebSocket factory (both need the current base URL/token
    // outside of a suspend context).
    private val _currentSettings = MutableStateFlow(AppSettings())
    val currentSettings: StateFlow<AppSettings> = _currentSettings.asStateFlow()

    private val settingsProvider: () -> AppSettings = { _currentSettings.value }

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DynamicBaseUrlInterceptor(settingsProvider))
        .addInterceptor(AuthInterceptor(settingsProvider))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // SSE / long-lived streams must not time out on read.
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS) // Keeps the /ws/voice connection alive.
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(placeholderBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json; charset=UTF-8".toMediaType())
        )
        .build()

    val api: JarvisApi = retrofit.create(JarvisApi::class.java)

    private val chatSseClient = ChatSseClient(okHttpClient, settingsProvider, json)

    val agentRepository = AgentRepository(api)
    val conversationRepository = ConversationRepository(api, chatSseClient)
    val statusRepository = StatusRepository(api)
    val reminderRepository = ReminderRepository(api)
    val briefingRepository = BriefingRepository(api)

    init {
        applicationScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _currentSettings.value = settings
            }
        }
    }

    /**
     * Creates a fresh [VoiceRepository] backed by a new [VoiceSocket]. A new
     * one is created each time the Voice screen is entered rather than
     * reused, since one socket = one `/ws/voice` session.
     */
    fun createVoiceRepository(): VoiceRepository {
        val voiceSocket = VoiceSocket(okHttpClient, settingsProvider, json)
        return VoiceRepository(voiceSocket)
    }

    /**
     * Creates a new [EventsSocket] for the proactive `/ws/events` push
     * channel (architecture.md §11). Unlike voice sockets, this is meant to
     * live as long as [de.hastnetwork.jarvis.JarvisEventsService] does, not
     * per-screen - the service owns starting/stopping it.
     */
    fun createEventsSocket(): EventsSocket = EventsSocket(okHttpClient, settingsProvider, json)
}
