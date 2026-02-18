package com.example.handheldapp.data.model

/**
 * Response dari login API
 */
data class LoginResponse(
    val token: String,
    val user: User,
    val branches: List<Branch> = emptyList()
)
