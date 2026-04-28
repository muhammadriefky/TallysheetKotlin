package com.example.handheldapp.repository

import android.util.Log
import com.example.handheldapp.data.api.AuthApiService
import com.example.handheldapp.data.local.dao.CachedBranchDao
import com.example.handheldapp.data.local.dao.CachedCompanyDao
import com.example.handheldapp.data.local.dao.CachedUserDao
import com.example.handheldapp.data.local.entity.CachedBranch
import com.example.handheldapp.data.local.entity.CachedCompany
import com.example.handheldapp.data.local.entity.CachedUser
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.User
import com.example.handheldapp.utils.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository untuk caching master data (companies, branches, users)
 * Memungkinkan login offline saat maintenance/server down
 */
@Singleton
class MasterDataCacheRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val companyDao: CachedCompanyDao,
    private val branchDao: CachedBranchDao,
    private val userDao: CachedUserDao,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "MasterDataCacheRepo"
    }

    /**
     * Get companies - Try API first, fallback to cache
     */
    suspend fun getCompanies(): Result<List<Company>> {
        return try {
            // Try API
            val response = authApi.getCompanies()
            if (response.success && response.data != null) {
                // Cache for offline use
                val cachedCompanies = response.data.map {
                    CachedCompany(
                        comCode = it.comCode,
                        comName = it.comName ?: it.comCode  // Fallback ke comCode jika comName null
                    )
                }
                companyDao.insertAll(cachedCompanies)

                Log.d(TAG, "✅ Fetched ${response.data.size} companies from API, cached")
                Result.success(response.data)
            } else {
                // API returned error, use cache
                useCachedCompanies()
            }
        } catch (e: Exception) {
            // Network error or timeout, use cache
            Log.e(TAG, "❌ API error: ${e.message}, using cache")
            useCachedCompanies()
        }
    }

    /**
     * Use cached companies when API fails
     */
    private suspend fun useCachedCompanies(): Result<List<Company>> {
        val cached = companyDao.getAllCompanies()
        return if (cached.isNotEmpty()) {
            val companies = cached.map {
                Company(comCode = it.comCode, comName = it.comName)  // compName dari cache
            }
            Log.d(TAG, "📦 Using ${cached.size} cached companies")
            Result.success(companies)
        } else {
            Result.failure(Exception("No cached companies available. Please connect to internet."))
        }
    }

    /**
     * Get users by company - Try API first, fallback to cache
     */
    suspend fun getUsersByCompany(comCode: String): Result<List<User>> {
        return try {
            // Try API
            val response = authApi.getUsers(comCode)
            if (response.success && response.data != null) {
                // Cache for offline use
                val cachedUsers = response.data.map {
                    CachedUser(
                        userId = it.usrCode ?: "",
                        userName = it.usrName ?: "",
                        comCode = comCode
                    )
                }
                userDao.insertAll(cachedUsers)

                Log.d(TAG, "✅ Fetched ${response.data.size} users from API, cached")
                Result.success(response.data)
            } else {
                useCachedUsers(comCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ API error: ${e.message}, using cache")
            useCachedUsers(comCode)
        }
    }

    /**
     * Use cached users when API fails
     */
    private suspend fun useCachedUsers(comCode: String): Result<List<User>> {
        val cached = userDao.getUsersByCompany(comCode)
        return if (cached.isNotEmpty()) {
            val users = cached.map {
                User(
                    usrCode = it.userId,
                    email = it.userId, // Fallback: use userId as email for cached data
                    usrName = it.userName,
                    role = null,
                    companyCode = comCode,
                    branchCode = null
                )
            }
            Log.d(TAG, "📦 Using ${cached.size} cached users")
            Result.success(users)
        } else {
            Result.failure(Exception("No cached users available. Please connect to internet."))
        }
    }

    /**
     * Get branches by company - Try API first, fallback to cache
     * NEW: Untuk flow Company → Branch → Login (BEFORE login)
     */
    suspend fun getBranchesByCompany(comCode: String): Result<List<Branch>> {
        return try {
            // Try API
            val response = authApi.getBranchesByCompany(comCode)
            if (response.success && response.data != null) {
                // Cache for offline use
                val cachedBranches = response.data.map {
                    CachedBranch(
                        cabCode = it.cabCode,
                        cabName = it.cabName,
                        comCode = it.comCode ?: "",
                        userId = ""  // No user context yet
                    )
                }
                branchDao.insertAll(cachedBranches)

                Log.d(TAG, "✅ Fetched ${response.data.size} branches from API, cached")
                Result.success(response.data)
            } else {
                useCachedBranchesByCompany(comCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ API error: ${e.message}, using cache")
            useCachedBranchesByCompany(comCode)
        }
    }

    /**
     * Use cached branches by company when API fails
     */
    private suspend fun useCachedBranchesByCompany(comCode: String): Result<List<Branch>> {
        val cached = branchDao.getBranchesByCompany(comCode)
        return if (cached.isNotEmpty()) {
            val branches = cached.map {
                Branch(
                    cabCode = it.cabCode,
                    cabName = it.cabName,
                    comCode = it.comCode
                )
            }
            Log.d(TAG, "📦 Using ${cached.size} cached branches")
            Result.success(branches)
        } else {
            Result.failure(Exception("No cached branches available. Please connect to internet."))
        }
    }

    /**
     * Get branches by user - Try API first, fallback to cache
     * DEPRECATED: Use getBranchesByCompany instead
     */
    suspend fun getBranchesByUser(usrCode: String): Result<List<Branch>> {
        return try {
            // Try API
            val response = authApi.getBranches(usrCode)
            if (response.success && response.data != null) {
                // Cache for offline use
                val cachedBranches = response.data.map {
                    CachedBranch(
                        cabCode = it.cabCode,
                        cabName = it.cabName,
                        comCode = it.comCode ?: "",
                        userId = usrCode
                    )
                }
                branchDao.insertAll(cachedBranches)

                Log.d(TAG, "✅ Fetched ${response.data.size} branches from API, cached")
                Result.success(response.data)
            } else {
                useCachedBranches(usrCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ API error: ${e.message}, using cache")
            useCachedBranches(usrCode)
        }
    }

    /**
     * Use cached branches when API fails
     */
    private suspend fun useCachedBranches(usrCode: String): Result<List<Branch>> {
        val cached = branchDao.getBranchesByUser(usrCode)
        return if (cached.isNotEmpty()) {
            val branches = cached.map {
                Branch(
                    cabCode = it.cabCode,
                    cabName = it.cabName,
                    comCode = it.comCode
                )
            }
            Log.d(TAG, "📦 Using ${cached.size} cached branches")
            Result.success(branches)
        } else {
            Result.failure(Exception("No cached branches available. Please connect to internet."))
        }
    }

    /**
     * Clear all cached data (for logout, etc)
     */
    suspend fun clearCache() {
        companyDao.deleteAll()
        branchDao.deleteAll()
        userDao.deleteAll()
        Log.d(TAG, "🗑️ All cached master data cleared")
    }


    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats {
        return CacheStats(
            companiesCount = companyDao.getCount(),
            branchesCount = branchDao.getCount(),
            usersCount = userDao.getCount()
        )
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val companiesCount: Int,
    val branchesCount: Int,
    val usersCount: Int
)
