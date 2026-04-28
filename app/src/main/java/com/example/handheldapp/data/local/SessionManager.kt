package com.example.handheldapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extension untuk create DataStore instance
 * Nama harus unik di seluruh aplikasi
 */
private val Context.warehouseSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "warehouse_session")

/**
 * Session Manager menggunakan DataStore
 * Menyimpan token, user info, company, dan branch
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.warehouseSessionDataStore

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        // usr_fullname - nama lengkap user (renamed from usr_loginname)
        private val USER_LOGINNAME_KEY = stringPreferencesKey("usr_fullname")
        private val USER_NAME_KEY = stringPreferencesKey("usr_name")
        private val USER_ID_KEY = intPreferencesKey("usr_id")

        private val COMPANY_CODE_KEY = stringPreferencesKey("com_code")
        private val COMPANY_NAME_KEY = stringPreferencesKey("com_name")

        private val BRANCH_CODE_KEY = stringPreferencesKey("cab_code")
        private val BRANCH_NAME_KEY = stringPreferencesKey("cab_desc")

        // Gudang/Business code for WMS operations
        private val GUDANG_CODE_KEY = stringPreferencesKey("gudang_code")
    }

    /**
     * Save authentication token
     */
    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    /**
     * Get authentication token
     */
    fun getToken(): Flow<String?> = dataStore.data.map { it[TOKEN_KEY] }

    /**
     * Save user info (Setelah Login Sukses)
     * Kita simpan usr_fullname (nama lengkap) dan display name
     */
    suspend fun saveUser(usrLoginname: String, usrName: String, userId: Int = 0) {
        dataStore.edit { preferences ->
            preferences[USER_LOGINNAME_KEY] = usrLoginname
            preferences[USER_NAME_KEY] = usrName
            preferences[USER_ID_KEY] = userId
        }
    }

    /**
     * Get user login name (ID)
     */
    fun getUserLoginName(): Flow<String?> = dataStore.data.map { it[USER_LOGINNAME_KEY] }

    /**
     * Get user display name
     */
    fun getUserName(): Flow<String?> = dataStore.data.map { it[USER_NAME_KEY] }

    /**
     * Get user ID (integer)
     */
    fun getUserId(): Flow<Int?> = dataStore.data.map { it[USER_ID_KEY] }

    /**
     * Save company info (Dipilih di awal Login)
     */
    suspend fun saveCompany(companyCode: String, companyName: String) {
        dataStore.edit { preferences ->
            preferences[COMPANY_CODE_KEY] = companyCode
            preferences[COMPANY_NAME_KEY] = companyName
        }
    }

    fun getCompanyCode(): Flow<String?> = dataStore.data.map { it[COMPANY_CODE_KEY] }

    /**
     * Save branch info (Dipilih SETELAH Login Berhasil)
     */
    suspend fun saveBranch(branchCode: String, branchName: String) {
        dataStore.edit { preferences ->
            preferences[BRANCH_CODE_KEY] = branchCode
            preferences[BRANCH_NAME_KEY] = branchName
        }
    }

    fun getBranchCode(): Flow<String?> = dataStore.data.map { it[BRANCH_CODE_KEY] }
    fun getBranchName(): Flow<String?> = dataStore.data.map { it[BRANCH_NAME_KEY] }

    /**
     * Save gudang/business code (untuk WMS operations)
     * Biasanya di-set saat login atau memilih branch
     */
    suspend fun saveGudangCode(gudangCode: String) {
        dataStore.edit { preferences ->
            preferences[GUDANG_CODE_KEY] = gudangCode
        }
    }

    fun getGudangCode(): Flow<String?> = dataStore.data.map { it[GUDANG_CODE_KEY] }

    /**
     * Clear all session data (logout)
     */
    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Check if user is fully logged in
     * User dianggap login jika punya Token, punya UserID, dan SUDAH pilih Branch
     */
    fun isLoggedIn(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            !preferences[TOKEN_KEY].isNullOrEmpty() &&
                    !preferences[USER_LOGINNAME_KEY].isNullOrEmpty() &&
                    !preferences[BRANCH_CODE_KEY].isNullOrEmpty()
        }
    }
}