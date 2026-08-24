package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureCredentialManager(context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "rcos_secure_keystore_prefs_v1",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SecureCredentialManager", "Failed to initialize EncryptedSharedPreferences, fallback to secure private prefs", e)
            context.getSharedPreferences("rcos_secure_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    private val insecurePrefs: SharedPreferences =
        context.getSharedPreferences("rcos_user_prefs", Context.MODE_PRIVATE)

    init {
        migrateInsecurePreferences()
        seedInitialDefaultVaultIfNeeded()
    }

    private fun migrateInsecurePreferences() {
        try {
            val sensitiveKeys = listOf(
                "logged_in_email",
                "profile_full_name",
                "profile_title",
                "profile_email",
                "profile_phone",
                "profile_google_email",
                "profile_ms_email",
                "profile_org_name",
                "profile_timezone"
            )

            val editor = encryptedPrefs.edit()
            val insecureEditor = insecurePrefs.edit()
            var migrated = false

            for (key in sensitiveKeys) {
                if (insecurePrefs.contains(key)) {
                    val value = insecurePrefs.getString(key, null)
                    if (value != null) {
                        editor.putString(key, value)
                        insecureEditor.remove(key)
                        migrated = true
                    }
                }
            }

            if (migrated) {
                editor.apply()
                insecureEditor.apply()
                Log.d("SecureCredentialManager", "Successfully migrated sensitive preferences to Android Keystore EncryptedSharedPreferences")
            }
        } catch (e: Exception) {
            Log.e("SecureCredentialManager", "Error during preferences migration", e)
        }
    }

    private fun seedInitialDefaultVaultIfNeeded() {
        if (getVaultKeys().isEmpty()) {
            saveVaultItem(
                id = "gemini_api_key",
                name = "Gemini Pro Multi-Modal API Key",
                secretValue = "AIzaSy_KeystoreProtected_GeminiToken_2026",
                category = "AI Service Token"
            )
            saveVaultItem(
                id = "google_workspace_oauth",
                name = "Google Workspace Service OAuth Secret",
                secretValue = "GOWS_sec_994827103847_rcos_enterprise",
                category = "OAuth2 Client Secret"
            )
            saveVaultItem(
                id = "microsoft_azure_token",
                name = "Microsoft 365 Azure AD App Key",
                secretValue = "MS365_azure_secret_882910394726102",
                category = "Enterprise Auth Token"
            )
        }
    }

    fun saveString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return encryptedPrefs.getString(key, defaultValue) ?: defaultValue
    }

    fun getNullableString(key: String): String? {
        return if (encryptedPrefs.contains(key)) encryptedPrefs.getString(key, null) else null
    }

    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        insecurePrefs.edit().clear().apply()
    }

    // Vault Credentials Management (Android Keystore Encrypted)
    fun saveVaultItem(id: String, name: String, secretValue: String, category: String = "API Key") {
        saveString("vault_name_$id", name)
        saveString("vault_secret_$id", secretValue)
        saveString("vault_category_$id", category)

        val keys = getVaultKeys().toMutableSet()
        keys.add(id)
        saveString("vault_keys_list", keys.joinToString(","))
    }

    fun getVaultKeys(): List<String> {
        val raw = getString("vault_keys_list", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun getVaultItem(id: String): VaultItem? {
        val name = getString("vault_name_$id") ?: return null
        val secret = getString("vault_secret_$id") ?: ""
        val category = getString("vault_category_$id") ?: "API Key"
        return VaultItem(id, name, secret, category)
    }

    fun deleteVaultItem(id: String) {
        remove("vault_name_$id")
        remove("vault_secret_$id")
        remove("vault_category_$id")

        val keys = getVaultKeys().filter { it != id }
        saveString("vault_keys_list", keys.joinToString(","))
    }

    fun getAllVaultItems(): List<VaultItem> {
        return getVaultKeys().mapNotNull { getVaultItem(it) }
    }
}

data class VaultItem(
    val id: String,
    val name: String,
    val secretValue: String,
    val category: String
)
