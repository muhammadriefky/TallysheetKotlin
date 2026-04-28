package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk User (wdms_users table)
 * Disesuaikan dengan kolom: usr_fullname, email, usr_rolecode
 */
@Parcelize
data class User(
    @SerializedName("username")  // API returns 'username' which is usr_fullname
    val usrCode: String?, // Username/fullname for display

    @SerializedName("email")
    val email: String?,   // Email - digunakan untuk login

    @SerializedName("name")
    val usrName: String?, // Display name

    @SerializedName("role")  // API returns 'role' which is usr_rolecode
    val role: String? = null,  // Admin_IT, Kepala_Gudang, Admin_Gudang

    @SerializedName("company_code")
    val companyCode: String? = null,

    @SerializedName("branch_code")
    val branchCode: String? = null

) : Parcelable {
    fun getDisplayText(): String = email ?: usrCode ?: "No Email"
}
