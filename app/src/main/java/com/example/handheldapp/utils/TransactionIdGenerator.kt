package com.example.handheldapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generator untuk rec_trans_id
 * Format BARU: {MAC_ADDRESS}|{BARCODE}-{UNIT}{QTY_PADDED}
 * Contoh: EE:5D:99:92:FE:B4|3202012120242122221005000000000196-PCS000000000196
 *         C2:4B:22:6D:7F:0D|3202011920240623230650000000000532-PCS000000000532
 */
object TransactionIdGenerator {

    /**
     * Generate transaction ID untuk scan dengan barcode
     * Format: {MAC_ADDRESS}|{BARCODE}-{UNIT}{QTY_PADDED}
     *
     * @param context Android context
     * @param barcode Barcode yang di-scan
     * @param qtyKarton Jumlah karton
     * @param qtyPcs Jumlah PCS
     * @param pcsPerKarton Konversi pcs per karton
     * @return rec_trans_id string
     */
    @SuppressLint("HardwareIds")
    fun generate(context: Context, barcode: String?, qtyKarton: Int, qtyPcs: Int, pcsPerKarton: Int = 1): String {
        val deviceId = getDeviceId(context)

        // Gunakan barcode jika ada, atau generate timestamp part sebagai fallback
        val barcodeOrTimestamp = if (!barcode.isNullOrEmpty()) barcode else generateTimestampPart()

        // Hitung total PCS
        val totalPcs = (qtyKarton * pcsPerKarton) + qtyPcs
        val pcsPadded = totalPcs.toString().padStart(12, '0')

        // Tentukan unit berdasarkan qty - selalu gunakan PCS dengan total qty
        val unit = "PCS"

        return "$deviceId|$barcodeOrTimestamp-$unit$pcsPadded"
    }

    /**
     * Legacy generate tanpa barcode (backward compatibility)
     */
    @SuppressLint("HardwareIds")
    fun generateLegacy(context: Context, qtyKarton: Int, qtyPcs: Int, pcsPerKarton: Int = 1): String {
        return generate(context, null, qtyKarton, qtyPcs, pcsPerKarton)
    }

    /**
     * Generate transaction ID dengan detail karton dan pcs terpisah
     * Format: {DEVICE_ID}|{TIMESTAMP}-KRT{QTY_KRT}-PCS{QTY_PCS}
     */
    fun generateDetailed(context: Context, qtyKarton: Int, qtyPcs: Int): String {
        val deviceId = getDeviceId(context)
        val timestamp = generateTimestampPart()
        val kartonPadded = qtyKarton.toString().padStart(12, '0')
        val pcsPadded = qtyPcs.toString().padStart(12, '0')

        return if (qtyKarton > 0 && qtyPcs > 0) {
            // Both karton and pcs
            "$deviceId|$timestamp-KRT$kartonPadded-PCS$pcsPadded"
        } else if (qtyKarton > 0) {
            // Only karton
            "$deviceId|$timestamp-KRT$kartonPadded"
        } else {
            // Only pcs
            "$deviceId|$timestamp-PCS$pcsPadded"
        }
    }

    /**
     * Get device identifier (MAC Address style atau Android ID)
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceId(context: Context): String {
        return try {
            // Try to get WiFi MAC Address (deprecated but still works on some devices)
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val macAddress = wifiManager?.connectionInfo?.macAddress

            if (!macAddress.isNullOrEmpty() && macAddress != "02:00:00:00:00:00") {
                macAddress.uppercase()
            } else {
                // Fallback: Generate pseudo MAC from Android ID
                generatePseudoMac(context)
            }
        } catch (e: Exception) {
            generatePseudoMac(context)
        }
    }

    /**
     * Generate pseudo MAC address dari Android ID
     * Format: XX:XX:XX:XX:XX:XX
     */
    @SuppressLint("HardwareIds")
    private fun generatePseudoMac(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN${System.currentTimeMillis()}"

        // Hash dan format sebagai MAC address
        val hash = androidId.hashCode().toLong().and(0xFFFFFFFFFFFFL)
            .toString(16).uppercase().padStart(12, '0')
        return hash.take(12).chunked(2).joinToString(":")
    }

    /**
     * Generate timestamp part
     * Format: YYMMDDHHmmssSSS + random 5 digit + padding
     */
    private fun generateTimestampPart(): String {
        val dateFormat = SimpleDateFormat("yyMMddHHmmssSSS", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val random = (10000..99999).random().toString()
        val padding = "0".repeat(10) // Padding untuk total panjang yang konsisten

        return "$timestamp$random$padding"
    }

    /**
     * Parse existing trans_id to get components
     */
    fun parse(transId: String): TransIdComponents? {
        return try {
            val parts = transId.split("|")
            if (parts.size != 2) return null

            val deviceId = parts[0]
            val dataPart = parts[1]

            // Extract unit and qty
            val krtMatch = Regex("-KRT(\\d+)").find(dataPart)
            val pcsMatch = Regex("-PCS(\\d+)").find(dataPart)

            val qtyKarton = krtMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val qtyPcs = pcsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            TransIdComponents(deviceId, qtyKarton, qtyPcs)
        } catch (e: Exception) {
            null
        }
    }

    data class TransIdComponents(
        val deviceId: String,
        val qtyKarton: Int,
        val qtyPcs: Int
    )
}
