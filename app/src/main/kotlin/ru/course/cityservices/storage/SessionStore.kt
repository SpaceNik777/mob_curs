package ru.course.cityservices.storage

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionStore by preferencesDataStore(name = "session")

data class SessionData(val username: String, val userId: Long, val displayName: String)

// Хранилище сессии на DataStore. Единственный компонент проекта, у которого
// нормальная ответственность :)
class SessionStore(private val context: Context) {

    suspend fun save(username: String, userId: Long, displayName: String) {
        context.sessionStore.edit { prefs ->
            prefs[USER_ID] = userId
            prefs[USERNAME] = username
            prefs[DISPLAY_NAME] = displayName
        }
    }

    suspend fun load(): SessionData? {
        val prefs = context.sessionStore.data.first()
        val id = prefs[USER_ID] ?: return null
        val name = prefs[USERNAME] ?: return null

        return SessionData(username = name, userId = id, displayName = prefs[DISPLAY_NAME] ?: name)
    }

    suspend fun clear() {
        context.sessionStore.edit(transform = MutablePreferences::clear)
    }

    private companion object {

        val USER_ID = longPreferencesKey(name = "user_id")

        val USERNAME = stringPreferencesKey(name = "username")

        val DISPLAY_NAME = stringPreferencesKey(name = "display_name")
    }
}
