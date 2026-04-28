package com.example.handheldapp.repository

import android.util.Log
import com.example.handheldapp.data.api.AuthApiService
import com.example.handheldapp.data.api.LoginRequest
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.local.dao.CachedUserDao
import com.example.handheldapp.data.local.entity.CachedUser
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.User
import com.example.handheldapp.utils.PasswordHasher
import com.example.handheldapp.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val sessionManager: SessionManager,
    private val cachedUserDao: CachedUserDao
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    /**
     * Get Companies
     */
    fun getCompanies(): Flow<Resource<List<Company>>> = flow {
        emit(Resource.Loading())
        try {
            val response = authApi.getCompanies()
            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil data perusahaan"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    /**
     * Get Users by Company
     */
    fun getUsersByCompany(companyCode: String?): Flow<Resource<List<User>>> = flow {
        if (companyCode.isNullOrBlank()) {
            emit(Resource.Error("Pilih perusahaan terlebih dahulu"))
            return@flow
        }

        emit(Resource.Loading())
        try {
            val response = authApi.getUsers(companyCode = companyCode)
            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil data user"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    /**
     * Login - With OFFLINE SUPPORT
     * Updated: Using email for login (wdms_users table)
     * ★ SECURITY FIX: branchCode wajib dikirim untuk validasi di backend
     *
     * Priority:
     * 1. Try online login first
     * 2. On network error (timeout, no internet), fallback to cached credentials
     * 3. On auth error (wrong password), don't fallback - return error
     */
    fun login(
        companyCode: String?,
        companyName: String?,
        email: String?,  // Changed from username to email
        password: String,
        branchCode: String?  // ★ WAJIB: Branch yang dipilih user
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())

        // 1. Validasi Komprehensif: Mencegah NullPointerException
        if (companyCode.isNullOrBlank() || email.isNullOrBlank()) {
            emit(Resource.Error("Perusahaan atau Email belum diisi"))
            return@flow
        }

        if (branchCode.isNullOrBlank()) {
            emit(Resource.Error("Branch belum dipilih"))
            return@flow
        }

        if (password.isBlank()) {
            emit(Resource.Error("Password tidak boleh kosong"))
            return@flow
        }

        try {
            // 2. TRY ONLINE LOGIN FIRST
            Log.d(TAG, "🔐 Attempting online login for $email at branch $branchCode")

            val request = LoginRequest(
                email = email!!,  // API expects 'username' but we send email
                password = password,
                companyCode = companyCode!!,  // ★ Kirim company_code ke API
                branchCode = branchCode!!  // ★ Kirim branch_code ke API
            )

            val response = authApi.login(request)

            if (response.success && response.data != null) {
                val loginData = response.data

                // 3. Simpan Token & Session
                sessionManager.saveToken(loginData.token)

                val finalUsername = loginData.user.usrCode ?: email
                val finalDisplayName = loginData.user.usrName ?: "No Name"
                val finalEmail = loginData.user.email ?: email

                sessionManager.saveUser(
                    usrLoginname = finalUsername,
                    usrName = finalDisplayName
                )

                sessionManager.saveCompany(companyCode!!, companyName ?: companyCode!!)

                // 4. CACHE CREDENTIALS untuk offline login
                cacheUserCredentials(
                    userId = finalEmail,  // Use email as userId
                    userName = finalDisplayName,
                    compCode = companyCode!!,
                    password = password
                )

                Log.d(TAG, "✅ Online login SUCCESS for $finalEmail")
                emit(Resource.Success(true))
            } else {
                // Auth error (wrong password) - don't fallback to cache
                Log.w(TAG, "❌ Online login FAILED: ${response.message}")
                emit(Resource.Error(response.message ?: "Login gagal: Data tidak valid"))
            }
        } catch (e: Exception) {
            // Network error - try offline login
            val isNetworkError = e is SocketTimeoutException ||
                    e is UnknownHostException ||
                    e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    e.message?.contains("failed to connect", ignoreCase = true) == true

            if (isNetworkError) {
                Log.w(TAG, "🔌 Network error, trying offline login: ${e.message}")

                // Try offline authentication
                val offlineResult = tryOfflineLogin(companyCode!!, email!!, password, companyName)
                emit(offlineResult)
            } else {
                Log.e(TAG, "❌ Login error (non-network): ${e.message}")
                emit(Resource.Error("Koneksi gagal: ${e.localizedMessage}"))
            }
        }
    }

    /**
     * Try offline authentication using cached credentials
     */
    private suspend fun tryOfflineLogin(
        companyCode: String,
        username: String,
        password: String,
        companyName: String?
    ): Resource<Boolean> {
        Log.d(TAG, "📴 Attempting offline login for $username@$companyCode")

        // Get cached user
        val cachedUser = cachedUserDao.getUserForAuth(companyCode, username)

        if (cachedUser == null) {
            Log.w(TAG, "❌ No cached credentials for $username@$companyCode")
            return Resource.Error("Server tidak tersedia dan belum pernah login sebelumnya. Silakan coba lagi saat online.")
        }

        if (cachedUser.hashedPassword.isNullOrEmpty()) {
            Log.w(TAG, "❌ Cached user found but no password stored")
            return Resource.Error("Server tidak tersedia. Login online diperlukan untuk pertama kali.")
        }

        // Verify password
        val isPasswordValid = PasswordHasher.verifyPassword(password, cachedUser.userId, cachedUser.hashedPassword!!)

        if (!isPasswordValid) {
            Log.w(TAG, "❌ Offline login: Password mismatch")
            return Resource.Error("Password salah")
        }

        // Password valid - create offline session
        Log.d(TAG, "✅ Offline login SUCCESS for ${cachedUser.userId}")

        // Save session (tanpa token karena offline)
        sessionManager.saveUser(
            usrLoginname = cachedUser.userId,
            usrName = cachedUser.userName
        )
        sessionManager.saveCompany(companyCode, companyName ?: companyCode)

        // Note: Tidak ada token untuk offline login
        // App perlu handle case ini di API calls

        return Resource.Success(true)
    }

    /**
     * Cache user credentials setelah login sukses
     */
    private suspend fun cacheUserCredentials(
        userId: String,
        userName: String,
        compCode: String,
        password: String
    ) {
        try {
            val hashedPassword = PasswordHasher.hashPassword(password, userId)

            val cachedUser = CachedUser(
                userId = userId,
                userName = userName,
                comCode = compCode,
                hashedPassword = hashedPassword,
                lastSyncAt = System.currentTimeMillis()
            )

            cachedUserDao.insert(cachedUser)
            Log.d(TAG, "💾 Cached credentials for $userId")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cache credentials: ${e.message}")
            // Don't fail login if caching fails
        }
    }

    /**
     * Get Branches by User Code
     */
    fun getBranchesByUser(usrCode: String?): Flow<Resource<List<Branch>>> = flow {
        if (usrCode.isNullOrBlank()) {
            emit(Resource.Error("User Code tidak valid"))
            return@flow
        }

        emit(Resource.Loading())
        try {
            val response = authApi.getBranches(usrCode = usrCode!!)
            if (response.success && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Gagal mengambil data cabang"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Terjadi kesalahan jaringan"))
        }
    }

    suspend fun saveBranchSelection(branchCode: String, branchName: String) {
        sessionManager.saveBranch(branchCode, branchName)
    }

    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: Exception) {
            // Tetap hapus session lokal
        } finally {
            sessionManager.clearSession()
        }
    }

    fun isLoggedIn(): Flow<Boolean> = sessionManager.isLoggedIn()
}