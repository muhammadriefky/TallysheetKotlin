package com.example.handheldapp.repository

import com.example.handheldapp.data.api.AuthApiService
import com.example.handheldapp.data.api.LoginRequest
import com.example.handheldapp.data.local.SessionManager
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.User
import com.example.handheldapp.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val sessionManager: SessionManager
) {

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
     * Login - DIPERBAIKI: Menggunakan parameter nullable agar tidak NPE
     */
    fun login(
        companyCode: String?,
        companyName: String?,
        username: String?,
        password: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())

        // 1. Validasi Komprehensif: Mencegah NullPointerException
        if (companyCode.isNullOrBlank() || username.isNullOrBlank()) {
            emit(Resource.Error("Perusahaan atau User belum dipilih"))
            return@flow
        }

        if (password.isBlank()) {
            emit(Resource.Error("Password tidak boleh kosong"))
            return@flow
        }

        try {
            // 2. Mapping Request ke Laravel
            val request = LoginRequest(
                company_code = companyCode!!, // Aman karena sudah dicek isNullOrBlank
                username = username!!,
                password = password
            )

            val response = authApi.login(request)

            if (response.success && response.data != null) {
                val loginData = response.data

                // 3. Simpan Token & Session
                sessionManager.saveToken(loginData.token)

                // Sesuaikan field berdasarkan model User di Android (usr_loginname / usr_name)
                val finalUsername = loginData.user.usrCode ?: username
                val finalDisplayName = loginData.user.usrName ?: "No Name"

                sessionManager.saveUser(
                    usrLoginname = finalUsername,
                    usrName = finalDisplayName
                )

                // Simpan info company (Gunakan companyCode sebagai fallback jika name null)
                sessionManager.saveCompany(companyCode!!, companyName ?: companyCode!!)

                emit(Resource.Success(true))
            } else {
                emit(Resource.Error(response.message ?: "Login gagal: Data tidak valid"))
            }
        } catch (e: Exception) {
            emit(Resource.Error("Koneksi gagal: ${e.localizedMessage}"))
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