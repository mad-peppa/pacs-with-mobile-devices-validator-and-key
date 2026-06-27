package by.snegoviki2.validator

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "preferences")

class SettingsManager(private val context: Context) {
    companion object{
        private val VALIDATOR_NAME_KEY = stringPreferencesKey("validator_name")
        private val VALIDATOR_IS_SETUP_KEY = booleanPreferencesKey("validator_is_setup")
    }
    suspend fun setValidatorName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[VALIDATOR_NAME_KEY] = name
            prefs[VALIDATOR_IS_SETUP_KEY] = true
        }
    }
    suspend fun getValidatorName(): String {
        val prefs = context.dataStore.data
        return prefs.first()[VALIDATOR_NAME_KEY] ?: ""
    }
    suspend fun getValidatorIsSetup(): Boolean {
        val prefs = context.dataStore.data
        return prefs.first()[VALIDATOR_IS_SETUP_KEY] ?: false
    }
}