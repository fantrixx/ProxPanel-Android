package com.proxpanel.app

import android.content.Context
import com.proxpanel.app.data.ApiClient
import com.proxpanel.app.data.CredentialStore
import com.proxpanel.app.data.SessionStore
import com.proxpanel.app.jobs.JobTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore = SessionStore(appContext)
    val credentialStore = CredentialStore(appContext)
    val api = ApiClient(sessionStore)
    val jobTracker = JobTracker(api, appScope)
}
