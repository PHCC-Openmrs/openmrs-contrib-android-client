package com.openmrs.android_sdk.library.api.repository

import com.openmrs.android_sdk.library.api.RestApi
import com.openmrs.android_sdk.library.api.RestServiceBuilder
import com.openmrs.android_sdk.library.databases.AppDatabaseHelper.createObservableIO
import com.openmrs.android_sdk.library.models.Session
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.Callable

@Singleton
class LoginRepository @Inject constructor() : BaseRepository() {

    /**
     * Gets an authenticated session by username and password.
     *
     * @param username authenticated username
     * @param password authenticated password
     * @return observable session
     */
    fun getSession(username: String, password: String): Observable<Session> {
        return createObservableIO(Callable {
            restApi = RestServiceBuilder.createService(RestApi::class.java, username, password)
            val response = restApi.getSession().execute()
            if (response.isSuccessful && response.body() != null) {
                // There can be more than one Set-Cookie header (e.g. CSRFGuard adds its own), and
                // a repeat login against an already-live server session may come back with no
                // Set-Cookie at all - in both cases fall back to the sessionId Gson already
                // deserialized from the response body instead of crashing on a bad assumption.
                val cookieSessionId = response.headers().values("Set-Cookie")
                        .firstOrNull { it.startsWith("JSESSIONID=") }
                        ?.substringAfter("=")
                        ?.substringBefore(";")
                response.body()?.sessionId = cookieSessionId ?: response.body()?.sessionId
                return@Callable response.body()!!
            } else {
                throw Exception("Error fetching session: ${response.message()}")
            }
        })
    }
}
