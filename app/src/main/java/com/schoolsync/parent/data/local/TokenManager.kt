package com.schoolsync.parent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.schoolsync.parent.data.model.User
import com.schoolsync.parent.data.model.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "schoolsync_prefs")

/**
 * DataStore-based secure storage for device ID and cached user profile.
 * Auth state is determined by FirebaseAuth.currentUser.
 * Single source of truth for user profile data.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // ── Keys ─────────────────────────────────────────────────────────────
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")

        // User profile fields
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val USER_ROLE = stringPreferencesKey("user_role")
        val SCHOOL_ID = stringPreferencesKey("school_id")
        val SCHOOL_DISPLAY_NAME = stringPreferencesKey("school_display_name")
        val PROFILE_PIC = stringPreferencesKey("profile_pic")
        val CLASS_NAME = stringPreferencesKey("class_name")
        val SECTION = stringPreferencesKey("section")
        val ROLL_NO = stringPreferencesKey("roll_no")
        val FATHER_NAME = stringPreferencesKey("father_name")
        val MOTHER_NAME = stringPreferencesKey("mother_name")
        val DOB = stringPreferencesKey("dob")
        val GENDER = stringPreferencesKey("gender")
        val ADMISSION_DATE = stringPreferencesKey("admission_date")
        val PARENT_DB_KEY = stringPreferencesKey("parent_db_key")
        val SESSION = stringPreferencesKey("session")
        val SCHOOL_CODE = stringPreferencesKey("school_code")  // Firebase school key
        val THEME_MODE = stringPreferencesKey("theme_mode")  // "system", "light", "dark"
        // Phase A — persisted across app restarts so the force-change
        // gate survives a cold-restart of the app while the flag is still
        // true on the Firestore students doc.
        val MUST_CHANGE_PASSWORD = androidx.datastore.preferences.core.booleanPreferencesKey("must_change_password")
    }

    // ── Device ID Flow ──────────────────────────────────────────────────
    val deviceId: Flow<String?> = dataStore.data.map { it[Keys.DEVICE_ID] }

    // ── User Profile Flow ────────────────────────────────────────────────
    val user: Flow<User> = dataStore.data.map { prefs ->
        User(
            userId = prefs[Keys.USER_ID] ?: "",
            name = prefs[Keys.USER_NAME] ?: "",
            email = prefs[Keys.USER_EMAIL] ?: "",
            phone = prefs[Keys.USER_PHONE] ?: "",
            role = prefs[Keys.USER_ROLE] ?: "",
            schoolId = prefs[Keys.SCHOOL_ID] ?: "",
            schoolDisplayName = prefs[Keys.SCHOOL_DISPLAY_NAME] ?: "",
            profilePic = prefs[Keys.PROFILE_PIC] ?: "",
            className = prefs[Keys.CLASS_NAME] ?: "",
            section = prefs[Keys.SECTION] ?: "",
            rollNo = prefs[Keys.ROLL_NO] ?: "",
            fatherName = prefs[Keys.FATHER_NAME] ?: "",
            motherName = prefs[Keys.MOTHER_NAME] ?: "",
            dob = prefs[Keys.DOB] ?: "",
            gender = prefs[Keys.GENDER] ?: "",
            admissionDate = prefs[Keys.ADMISSION_DATE] ?: "",
            parentDbKey = prefs[Keys.PARENT_DB_KEY] ?: "",
            session = prefs[Keys.SESSION] ?: "",
            schoolCode = prefs[Keys.SCHOOL_CODE] ?: "",
            mustChangePassword = prefs[Keys.MUST_CHANGE_PASSWORD] ?: false,
        )
    }

    /** Flow that emits true if user is logged in (Firebase Auth has a current user + userId saved) */
    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        FirebaseAuth.getInstance().currentUser != null && !prefs[Keys.USER_ID].isNullOrBlank()
    }

    // ── Save Methods ─────────────────────────────────────────────────────

    /** Save device ID (generated once on first launch) */
    suspend fun saveDeviceId(deviceId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = deviceId
        }
    }

    /** Save user profile from login response DTO */
    suspend fun saveUser(userDto: UserDto) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = userDto.userId
            prefs[Keys.USER_NAME] = userDto.name
            prefs[Keys.USER_EMAIL] = userDto.email ?: ""
            prefs[Keys.USER_PHONE] = userDto.phone ?: ""
            prefs[Keys.USER_ROLE] = userDto.role
            prefs[Keys.SCHOOL_ID] = userDto.schoolId
            prefs[Keys.SCHOOL_DISPLAY_NAME] = userDto.schoolDisplayName ?: ""
            prefs[Keys.PROFILE_PIC] = userDto.profilePic ?: ""
            prefs[Keys.CLASS_NAME] = userDto.className ?: ""
            prefs[Keys.SECTION] = userDto.section ?: ""
            prefs[Keys.ROLL_NO] = userDto.rollNo ?: ""
            prefs[Keys.FATHER_NAME] = userDto.fatherName ?: ""
            prefs[Keys.MOTHER_NAME] = userDto.motherName ?: ""
            prefs[Keys.DOB] = userDto.dob ?: ""
            prefs[Keys.GENDER] = userDto.gender ?: ""
            prefs[Keys.ADMISSION_DATE] = userDto.admissionDate ?: ""
            prefs[Keys.PARENT_DB_KEY] = userDto.parentDbKey ?: ""
            prefs[Keys.SESSION] = userDto.session ?: ""
        }
    }

    /** Save user profile directly from a User object (used by new Firebase Auth login flow) */
    suspend fun saveUserDirect(user: User) {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.userId
            prefs[Keys.USER_NAME] = user.name
            prefs[Keys.USER_EMAIL] = user.email
            prefs[Keys.USER_PHONE] = user.phone
            prefs[Keys.USER_ROLE] = user.role
            prefs[Keys.SCHOOL_ID] = user.schoolId
            prefs[Keys.SCHOOL_DISPLAY_NAME] = user.schoolDisplayName
            prefs[Keys.PROFILE_PIC] = user.profilePic
            prefs[Keys.CLASS_NAME] = user.className
            prefs[Keys.SECTION] = user.section
            prefs[Keys.ROLL_NO] = user.rollNo
            prefs[Keys.FATHER_NAME] = user.fatherName
            prefs[Keys.MOTHER_NAME] = user.motherName
            prefs[Keys.DOB] = user.dob
            prefs[Keys.GENDER] = user.gender
            prefs[Keys.ADMISSION_DATE] = user.admissionDate
            prefs[Keys.PARENT_DB_KEY] = user.parentDbKey
            prefs[Keys.SESSION] = user.session
            prefs[Keys.SCHOOL_CODE] = user.schoolCode
            prefs[Keys.MUST_CHANGE_PASSWORD] = user.mustChangePassword
        }
    }

    /** Save school info (schoolCode, displayName, session) */
    suspend fun saveSchoolInfo(schoolCode: String, schoolDisplayName: String, session: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHOOL_CODE] = schoolCode
            prefs[Keys.SCHOOL_DISPLAY_NAME] = schoolDisplayName
            prefs[Keys.SESSION] = session
        }
    }

    /** Save the resolved Firebase school code (from Indexes/School_codes lookup) */
    suspend fun saveSchoolCode(schoolCode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHOOL_CODE] = schoolCode
        }
    }

    /** Update session if it changes */
    suspend fun saveSession(session: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SESSION] = session
        }
    }

    // ── Theme ─────────────────────────────────────────────────────────────

    /** Theme mode: "system", "light", or "dark" */
    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }

    suspend fun saveThemeMode(mode: String) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    // ── Clear ────────────────────────────────────────────────────────────

    /** Clear all stored data (on logout) */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

}
