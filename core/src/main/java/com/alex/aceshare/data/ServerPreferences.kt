package com.alex.aceshare.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aceshare_prefs")

class ServerPreferences(private val context: Context) {
    private val hostKey = stringPreferencesKey("last_host")
    private val portKey = intPreferencesKey("last_port")
    private val nameKey = stringPreferencesKey("last_instance_name")
    private val autoEnterKey = booleanPreferencesKey("auto_enter")

    val lastServer: Flow<DiscoveredServer?> = context.dataStore.data.map { prefs ->
        val host = prefs[hostKey] ?: return@map null
        val port = prefs[portKey] ?: return@map null
        val name = prefs[nameKey] ?: "AceShare"
        DiscoveredServer(instanceName = name, host = host, port = port)
    }

    val autoEnter: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoEnterKey] ?: true
    }

    suspend fun saveServer(server: DiscoveredServer) {
        context.dataStore.edit { prefs ->
            prefs[hostKey] = server.host
            prefs[portKey] = server.port
            prefs[nameKey] = server.instanceName
        }
    }

    suspend fun setAutoEnter(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoEnterKey] = enabled
        }
    }
}
