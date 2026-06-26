package com.sparklaw.platen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.profileDataStore by preferencesDataStore(name = "profiles")

private val PROFILES_KEY = stringPreferencesKey("profiles")
private val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")

class ProfileStore(private val context: Context) {

    companion object {
        val DEFAULT_PROFILE = Profile(
            id = "default",
            name = "Default",
            folderUri = null,
            colorMode = ColorMode.BITONAL,
            quality = Quality.STANDARD,
            pageSize = PageSize.FIT,
            ocrEnabled = true,
            autoDetect = false
        )

        private const val LEGACY_PREFS = "platen"
        private const val KEY_TREE = "output_tree_uri"
        private const val KEY_GRAYSCALE = "grayscale_mode"
        private const val KEY_OCR = "ocr_enabled"
        private const val KEY_HIGH_QUALITY = "high_quality"
        private const val KEY_PAGE_SIZE = "page_size"
        private const val KEY_AUTO_DETECT = "auto_detect_page_size"
    }

    val profiles: Flow<List<Profile>> = context.profileDataStore.data.map { prefs ->
        val json = prefs[PROFILES_KEY]
        if (json == null) emptyList() else {
            try {
                Json.decodeFromString<List<Profile>>(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val activeProfileId: Flow<String> = context.profileDataStore.data.map { prefs ->
        prefs[ACTIVE_PROFILE_ID_KEY] ?: DEFAULT_PROFILE.id
    }

    suspend fun initialize() {
        val prefs = context.profileDataStore.data.first()
        if (prefs.contains(PROFILES_KEY)) return

        val migrated = migrateLegacyProfiles()
        if (migrated != null) {
            saveProfiles(migrated)
            setActiveProfile(migrated.first().id)
        } else {
            saveProfiles(listOf(DEFAULT_PROFILE))
            setActiveProfile(DEFAULT_PROFILE.id)
        }
    }

    private fun migrateLegacyProfiles(): List<Profile>? {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val hasAny = prefs.contains(KEY_TREE) ||
            prefs.contains(KEY_GRAYSCALE) ||
            prefs.contains(KEY_OCR) ||
            prefs.contains(KEY_HIGH_QUALITY) ||
            prefs.contains(KEY_PAGE_SIZE) ||
            prefs.contains(KEY_AUTO_DETECT)
        if (!hasAny) return null

        val folderUri = prefs.getString(KEY_TREE, null)
        val colorMode = if (prefs.getBoolean(KEY_GRAYSCALE, false))
            ColorMode.GRAYSCALE else ColorMode.BITONAL
        val ocrEnabled = prefs.getBoolean(KEY_OCR, true)
        val quality = if (prefs.getBoolean(KEY_HIGH_QUALITY, false))
            Quality.HIGH else Quality.STANDARD
        val pageSizeOrdinal = prefs.getInt(KEY_PAGE_SIZE, PageSize.FIT.ordinal)
        val pageSize = PageSize.entries.getOrElse(pageSizeOrdinal) { PageSize.FIT }
        val autoDetect = prefs.getBoolean(KEY_AUTO_DETECT, false)

        return listOf(
            Profile(
                id = "default",
                name = "Default",
                folderUri = folderUri,
                colorMode = colorMode,
                quality = quality,
                pageSize = pageSize,
                ocrEnabled = ocrEnabled,
                autoDetect = autoDetect
            )
        )
    }

    suspend fun saveProfiles(profiles: List<Profile>) {
        context.profileDataStore.edit { prefs ->
            prefs[PROFILES_KEY] = Json.encodeToString(profiles)
        }
    }

    suspend fun setActiveProfile(id: String) {
        context.profileDataStore.edit { prefs ->
            prefs[ACTIVE_PROFILE_ID_KEY] = id
        }
    }

    suspend fun updateProfile(profile: Profile) {
        val current = profiles.first()
        saveProfiles(current.map { if (it.id == profile.id) profile else it })
    }

    suspend fun addProfile(name: String): Profile {
        val current = profiles.first()
        val newProfile = DEFAULT_PROFILE.copy(
            id = UUID.randomUUID().toString(),
            name = name
        )
        saveProfiles(current + newProfile)
        setActiveProfile(newProfile.id)
        return newProfile
    }

    suspend fun renameProfile(id: String, name: String) {
        val current = profiles.first()
        saveProfiles(current.map { if (it.id == id) it.copy(name = name) else it })
    }

    suspend fun deleteProfile(id: String) {
        val current = profiles.first()
        if (current.size <= 1) return
        val updated = current.filter { it.id != id }
        saveProfiles(updated)
        if (activeProfileId.first() == id) {
            setActiveProfile(updated.first().id)
        }
    }
}
