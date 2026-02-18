package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk User (ms_user)
 * Disesuaikan dengan kolom: usr_loginname, usr_name, usr_grpcode
 */
@Parcelize
data class User(
    @SerializedName("usr_loginname") // Pastikan ini sesuai dengan JSON Laravel
    val usrCode: String?, // Ubah jadi nullable agar tidak crash saat parsing

    @SerializedName("usr_name")
    val usrName: String?,

    @SerializedName("usr_grpcode")
    val usrGrpcode: String? = null

) : Parcelable {
    fun getDisplayText(): String = "$usrCode"
}
