@file:Suppress("DEPRECATION")

package com.sakurafubuki.yume.core.data.webdav

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("DEPRECATION")
class WebDavCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "webdav_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun putPassword(serverId: Int, password: String) {
        encryptedPrefs.edit().putString("password_$serverId", password).apply()
    }

    fun getPassword(serverId: Int): String = encryptedPrefs.getString("password_$serverId", "") ?: ""

    fun deletePassword(serverId: Int) {
        encryptedPrefs.edit().remove("password_$serverId").apply()
    }
}
