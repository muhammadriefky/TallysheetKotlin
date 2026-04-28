package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk Company
 * Disesuaikan dengan alias di Laravel: cab_compcode as com_code
 */
@Parcelize
data class Company(
    @SerializedName("com_code")
    val comCode: String,

    @SerializedName("com_name")
    val comName: String? = null,  // Nullable karena API tidak selalu return field ini

    // Field di bawah ini akan bernilai null karena tidak ada di alias SELECT Laravel
    @SerializedName("com_address")
    val comAddress: String? = null,

    @SerializedName("com_phone")
    val comPhone: String? = null,

    @SerializedName("com_active")
    val comActive: Int = 1
) : Parcelable {

    /**
     * Get company name dengan fallback ke comCode jika null
     */
    fun getCompanyName(): String = comName ?: comCode

    /**
     * Digunakan oleh ArrayAdapter Spinner untuk menampilkan teks
     */
    override fun toString(): String {
        return comCode
    }

    /**
     * Display text untuk dropdown
     */
    fun getDisplayText(): String = comCode // Menampilkan TGM, TGU, dll
}