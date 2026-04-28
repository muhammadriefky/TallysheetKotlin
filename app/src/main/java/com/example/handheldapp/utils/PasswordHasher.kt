package com.example.handheldapp.utils

import java.security.MessageDigest

/**
 * Utility untuk hashing password untuk offline authentication
 *
 * SECURITY NOTE:
 * - Ini hanya untuk fallback saat server tidak tersedia
 * - Password tidak pernah disimpan plain text
 * - Hash dengan salt berdasarkan userId untuk keamanan tambahan
 */
object PasswordHasher {

    private const val SALT_PREFIX = "handheld_app_v1_"

    /**
     * Hash password dengan SHA-256 + salt
     * @param password Plain text password
     * @param userId User ID sebagai salt tambahan
     * @return Hashed password (hex string)
     */
    fun hashPassword(password: String, userId: String): String {
        val saltedPassword = "$SALT_PREFIX${userId.lowercase()}_$password"
        return sha256(saltedPassword)
    }

    /**
     * Verify password against stored hash
     * @param password Plain text password to verify
     * @param userId User ID (used as salt)
     * @param storedHash Hash yang tersimpan di database
     * @return true jika match, false jika tidak
     */
    fun verifyPassword(password: String, userId: String, storedHash: String): Boolean {
        val computedHash = hashPassword(password, userId)
        return computedHash.equals(storedHash, ignoreCase = true)
    }

    /**
     * SHA-256 hash
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
