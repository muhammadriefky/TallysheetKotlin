package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk Branch/Cabang (ms_cabang)
 * Disesuaikan dengan query: SELECT cab_code, cab_desc, cab_areacode, cab_compcode
 */
@Parcelize
data class Branch(
    @SerializedName("cab_code")
    val cabCode: String,

    // PERBAIKAN: Laravel mengirimkan 'cab_desc' untuk nama cabang
    @SerializedName("cab_desc")
    val cabName: String,

    // Tambahkan Areacode karena ini kunci relasi ke ms_user_d
    @SerializedName("cab_areacode")
    val cabAreaCode: String? = null,

    // PERBAIKAN: Laravel mengirimkan 'cab_compcode' (bukan com_code)
    @SerializedName("cab_compcode")
    val comCode: String? = null,

    // Tambahan field opsional jika ingin ditampilkan di detail
    @SerializedName("cab_add")
    val cabAddress: String? = null,

    @SerializedName("cab_add_city")
    val cabCity: String? = null
) : Parcelable {

    /**
     * Display text untuk dropdown agar lebih informatif
     */
    override fun toString(): String {
        return "$cabCode - $cabName"
    }

    fun getDisplayText(): String = "$cabCode"
}