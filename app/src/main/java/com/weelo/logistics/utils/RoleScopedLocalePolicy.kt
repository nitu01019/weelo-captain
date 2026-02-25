package com.weelo.logistics.utils

import android.content.SharedPreferences

enum class LocaleOwnerRole {
    DRIVER,
    TRANSPORTER,
    NONE
}

object RoleScopedLocalePolicy {
    private const val KEY_LOCALE_OWNER_ROLE = "ui_locale_owner_role"
    private const val KEY_LOCALE_OWNER_USER_ID = "ui_locale_owner_user_id"
    private const val KEY_DRIVER_PREFERRED_LANGUAGE = "driver_preferred_language"
    private const val KEY_LEGACY_PREFERRED_LANGUAGE = "preferred_language"
    private const val DEFAULT_LANGUAGE = "en"

    fun resolveStartupLocale(prefs: SharedPreferences): String {
        return when (ownerRole(prefs)) {
            LocaleOwnerRole.DRIVER -> {
                prefs.getString(KEY_DRIVER_PREFERRED_LANGUAGE, null)
                    ?.takeIf { it.isNotBlank() }
                    ?: prefs.getString(KEY_LEGACY_PREFERRED_LANGUAGE, null)
                        ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_LANGUAGE
            }
            LocaleOwnerRole.TRANSPORTER,
            LocaleOwnerRole.NONE -> DEFAULT_LANGUAGE
        }
    }

    fun markDriverLocale(
        prefs: SharedPreferences,
        userId: String?,
        languageCode: String
    ) {
        val normalized = languageCode.trim().lowercase().ifBlank { DEFAULT_LANGUAGE }
        prefs.edit()
            .putString(KEY_LOCALE_OWNER_ROLE, LocaleOwnerRole.DRIVER.name)
            .putString(KEY_LOCALE_OWNER_USER_ID, userId)
            .putString(KEY_DRIVER_PREFERRED_LANGUAGE, normalized)
            .putString(KEY_LEGACY_PREFERRED_LANGUAGE, normalized)
            .commit()
    }

    fun markTransporterNoLocale(
        prefs: SharedPreferences,
        userId: String?
    ) {
        prefs.edit()
            .putString(KEY_LOCALE_OWNER_ROLE, LocaleOwnerRole.TRANSPORTER.name)
            .putString(KEY_LOCALE_OWNER_USER_ID, userId)
            .commit()
    }

    fun clearActiveLocaleScope(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_LOCALE_OWNER_ROLE)
            .remove(KEY_LOCALE_OWNER_USER_ID)
            .remove(KEY_DRIVER_PREFERRED_LANGUAGE)
            .remove(KEY_LEGACY_PREFERRED_LANGUAGE)
            .apply()
    }

    private fun ownerRole(prefs: SharedPreferences): LocaleOwnerRole {
        val raw = prefs.getString(KEY_LOCALE_OWNER_ROLE, null)
        return when (raw?.uppercase()) {
            LocaleOwnerRole.DRIVER.name -> LocaleOwnerRole.DRIVER
            LocaleOwnerRole.TRANSPORTER.name -> LocaleOwnerRole.TRANSPORTER
            else -> LocaleOwnerRole.NONE
        }
    }
}
