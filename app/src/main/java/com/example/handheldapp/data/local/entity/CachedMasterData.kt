package com.example.handheldapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached Company - untuk login offline saat maintenance
 */
@Entity(tableName = "cached_companies")
data class CachedCompany(
    @PrimaryKey
    val comCode: String,
    val comName: String = "",  // Default empty jika API tidak return
    val lastSyncAt: Long = System.currentTimeMillis()
)

/**
 * Cached Branch - untuk login offline saat maintenance
 */
@Entity(tableName = "cached_branches")
data class CachedBranch(
    @PrimaryKey
    val cabCode: String,
    val cabName: String,
    val comCode: String,
    val userId: String,
    val lastSyncAt: Long = System.currentTimeMillis()
)

/**
 * Cached User - untuk auth offline saat maintenance
 */
@Entity(tableName = "cached_users")
data class CachedUser(
    @PrimaryKey
    val userId: String,
    val userName: String,
    val comCode: String,
    val hashedPassword: String? = null, // Optional: untuk offline auth
    val lastSyncAt: Long = System.currentTimeMillis()
)
