package com.example.handheldapp.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Data class untuk Scan Item (wdms_tr_tallysheet_det)
 */
@Parcelize
data class ScanItem(
    @SerializedName("id") val id: Int,
    @SerializedName("skuOrder") val skuOrder: String,
    @SerializedName("productName") val productName: String?,
    @SerializedName("qty") val qty: Int,
    @SerializedName("qtyKarton") val qtyKarton: Int?,
    @SerializedName("qtyPcs") val qtyPcs: Int?,
    @SerializedName("createdAt") val createdAt: String?
) : Parcelable {
    @Parcelize
    data class BarcodeProduct(
        @SerializedName("sku") val sku: String,
        @SerializedName("product_name") val productName: String,
        @SerializedName("conversion") val conversion: String? = null,
        @SerializedName("pcs_per_karton") val pcsPerKarton: Int = 1
    ) : Parcelable

    data class BarcodeResponse(
        val success: Boolean,
        val data: List<BarcodeProduct>,
        val count: Int = 0,
        val message: String = ""
    )

    data class SubmitResponse(
        val success: Boolean,
        val message: String
    )
}
