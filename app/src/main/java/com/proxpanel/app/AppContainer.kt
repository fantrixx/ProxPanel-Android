package com.proxpanel.app

import android.content.Context
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.CredentialStore
import com.proxpanel.app.data.PrefsStore
import com.proxpanel.app.data.SessionStore
import com.proxpanel.app.jobs.JobTracker
import com.proxpanel.app.util.L10n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore = SessionStore(appContext)
    val credentialStore = CredentialStore(appContext)
    val prefsStore = PrefsStore(appContext)
    val api = ApiClient(sessionStore)
    val l10n = L10n(appContext)
    val jobTracker = JobTracker(api, appScope, l10n)
}
