package com.example.handheldapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.handheldapp.data.local.entity.CachedBranch
import com.example.handheldapp.data.local.entity.CachedCompany
import com.example.handheldapp.data.local.entity.CachedUser

/**
 * DAO untuk cached companies
 */
@Dao
interface CachedCompanyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(companies: List<CachedCompany>)

    @Query("SELECT * FROM cached_companies ORDER BY comName ASC")
    suspend fun getAllCompanies(): List<CachedCompany>

    @Query("SELECT * FROM cached_companies WHERE comCode = :compCode LIMIT 1")
    suspend fun getCompanyByCode(compCode: String): CachedCompany?

    @Query("DELETE FROM cached_companies")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cached_companies")
    suspend fun getCount(): Int
}

/**
 * DAO untuk cached branches
 */
@Dao
interface CachedBranchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(branches: List<CachedBranch>)

    @Query("SELECT * FROM cached_branches WHERE comCode = :compCode ORDER BY cabName ASC")
    suspend fun getBranchesByCompany(compCode: String): List<CachedBranch>

    @Query("SELECT * FROM cached_branches WHERE userId = :userId ORDER BY cabName ASC")
    suspend fun getBranchesByUser(userId: String): List<CachedBranch>

    @Query("SELECT * FROM cached_branches WHERE cabCode = :cabCode LIMIT 1")
    suspend fun getBranchByCode(cabCode: String): CachedBranch?

    @Query("DELETE FROM cached_branches WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)

    @Query("DELETE FROM cached_branches")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cached_branches")
    suspend fun getCount(): Int
}

/**
 * DAO untuk cached users
 */
@Dao
interface CachedUserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<CachedUser>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: CachedUser)

    @Query("SELECT * FROM cached_users WHERE comCode = :compCode ORDER BY userName ASC")
    suspend fun getUsersByCompany(compCode: String): List<CachedUser>

    @Query("SELECT * FROM cached_users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): CachedUser?

    /**
     * Get user for offline authentication
     * Match by company + userId (case-insensitive)
     */
    @Query("SELECT * FROM cached_users WHERE comCode = :compCode AND LOWER(userId) = LOWER(:userId) LIMIT 1")
    suspend fun getUserForAuth(compCode: String, userId: String): CachedUser?

    /**
     * Update hashed password for user (after successful online login)
     */
    @Query("UPDATE cached_users SET hashedPassword = :hashedPassword, lastSyncAt = :syncTime WHERE userId = :userId")
    suspend fun updatePassword(userId: String, hashedPassword: String, syncTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM cached_users WHERE comCode = :compCode")
    suspend fun deleteByCompany(compCode: String)

    @Query("DELETE FROM cached_users")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cached_users")
    suspend fun getCount(): Int
}
