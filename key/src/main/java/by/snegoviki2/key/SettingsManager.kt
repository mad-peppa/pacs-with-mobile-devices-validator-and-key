package by.snegoviki2.key

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

class SettingsManager(private val context: Context) {
    companion object {
        private val EMPLOYEE_ID_KEY = stringPreferencesKey("employee_id")
    }

    suspend fun setEmployeeId(employeeId: String) {
        context.dataStore.edit { preferences ->
            preferences[EMPLOYEE_ID_KEY] = employeeId
        }
    }

    suspend fun getEmployeeId(): String {
        val preferences = context.dataStore.data
            .map { it[EMPLOYEE_ID_KEY] ?: "" }
        return preferences.first()
    }


    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}