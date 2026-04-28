package com.example.handheldapp.utils

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.handheldapp.R

/**
 * CustomDialogHelper
 *
 * Custom dialog helper selaras dengan tema Login biru (#1565C0 / #2196F3).
 * Semua dialog menggunakan gradient header + white card body.
 *
 * Warna tema:
 *  - Biru  → Update, Password, Download (brand color = sama dengan login)
 *  - Merah → Critical Maintenance
 *  - Oranye → Maintenance Warning
 *  - Kuning → Scheduled Maintenance
 *  - Hijau  → Update Optional
 */
object CustomDialogHelper {

    // =========================================================
    //  1. CRITICAL MAINTENANCE BLOCKER
    // =========================================================

    fun showCriticalMaintenance(
        context: Context,
        message: String? = null,
        estimatedEnd: String? = null,
        onRetry: () -> Unit,
        onClose: () -> Unit
    ): Dialog {
        val dialog = buildBaseDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_critical_maintenance, null)

        message?.let { view.findViewById<TextView>(R.id.tvMessage).text = it }

        val rowEstimasi = view.findViewById<LinearLayout>(R.id.rowEstimasi)
        if (!estimatedEnd.isNullOrEmpty()) {
            rowEstimasi.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvEstimasi).text = "Estimasi selesai: $estimatedEnd"
        } else {
            rowEstimasi.visibility = View.GONE
        }

        view.findViewById<Button>(R.id.btnRetry).setOnClickListener {
            dialog.dismiss()
            onRetry()
        }
        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dialog.dismiss()
            onClose()
        }

        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()
        animateDialogIn(view)
        return dialog
    }

    // =========================================================
    //  2. MAINTENANCE WARNING (non-blocking)
    // =========================================================

    fun showMaintenanceWarning(
        context: Context,
        message: String? = null,
        onOk: () -> Unit = {}
    ): Dialog {
        val dialog = buildBaseDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_maintenance_warning, null)

        message?.let { view.findViewById<TextView>(R.id.tvMessage).text = it }

        view.findViewById<Button>(R.id.btnOk).setOnClickListener {
            dialog.dismiss()
            onOk()
        }

        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()
        animateDialogIn(view)
        return dialog
    }

    // =========================================================
    //  3. SCHEDULED MAINTENANCE ALERT
    // =========================================================

    data class ScheduledMaintenanceInfo(
        val title: String = "Maintenance Dijadwalkan",
        val message: String? = null,
        val scheduleDate: String? = null,
        val scheduleTime: String? = null,
        val duration: String? = null,
        val timeUntilStart: String? = null
    )

    fun showScheduledMaintenance(
        context: Context,
        info: ScheduledMaintenanceInfo,
        onOk: () -> Unit = {}
    ): Dialog {
        val dialog = buildBaseDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_scheduled_maintenance, null)

        view.findViewById<TextView>(R.id.tvTitle).text = info.title
        info.message?.let { view.findViewById<TextView>(R.id.tvMessage).text = it }
        info.scheduleDate?.let { view.findViewById<TextView>(R.id.tvScheduleDate).text = it }
        info.scheduleTime?.let { view.findViewById<TextView>(R.id.tvScheduleTime).text = it }

        val rowDuration = view.findViewById<LinearLayout>(R.id.rowDuration)
        if (!info.duration.isNullOrEmpty()) {
            rowDuration.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvDuration).text = "Durasi: ${info.duration}"
        } else {
            rowDuration.visibility = View.GONE
        }

        info.timeUntilStart?.let {
            view.findViewById<TextView>(R.id.tvTimeUntil).text = "Dimulai $it"
        }

        view.findViewById<Button>(R.id.btnOk).setOnClickListener {
            dialog.dismiss()
            onOk()
        }

        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()
        animateDialogIn(view)
        return dialog
    }

    // =========================================================
    //  4. UPDATE DIALOG (required & optional)
    // =========================================================

    data class UpdateConfig(
        val mustUpdate: Boolean,
        val version: String? = null,
        val message: String? = null,
        val fileInfo: String? = null,
        val hasDirectDownload: Boolean = true
    )

    sealed class UpdateAction {
        object Download  : UpdateAction()
        object Later     : UpdateAction()
        object PlayStore : UpdateAction()
    }

    fun showUpdate(
        context: Context,
        config: UpdateConfig,
        onAction: (UpdateAction) -> Unit
    ): Dialog {
        val dialog = buildBaseDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_update, null)

        if (!config.mustUpdate) {
            // Ganti header ke hijau untuk update opsional
            view.findViewById<LinearLayout>(R.id.dialogHeader)
                .setBackgroundResource(R.drawable.bg_dialog_header_green)
            view.findViewById<TextView>(R.id.tvBadgeEmoji).text = "✨"
            view.findViewById<TextView>(R.id.tvSubtitleChip).text = "UPDATE OPSIONAL"
            view.findViewById<TextView>(R.id.tvTitle).text = "Update Baru Tersedia"
            view.findViewById<TextView>(R.id.tvVersionBadge).apply {
                setBackgroundResource(R.drawable.bg_version_badge_green)
                setTextColor(context.getColor(R.color.dlg_green_dark))
            }
            view.findViewById<Button>(R.id.btnUpdate)
                .setBackgroundResource(R.drawable.bg_btn_green)
        }

        config.version?.let { view.findViewById<TextView>(R.id.tvVersionBadge).text = it }
        config.message?.let { view.findViewById<TextView>(R.id.tvMessage).text = it }
        config.fileInfo?.let { view.findViewById<TextView>(R.id.tvFileInfo).text = it }

        val btnLater = view.findViewById<Button>(R.id.btnLater)
        if (!config.mustUpdate) {
            btnLater.visibility = View.VISIBLE
        }

        view.findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            dialog.dismiss()
            onAction(UpdateAction.Download)
        }
        btnLater.setOnClickListener {
            dialog.dismiss()
            onAction(UpdateAction.Later)
        }

        dialog.setContentView(view)
        dialog.setCancelable(!config.mustUpdate)
        dialog.show()
        animateDialogIn(view)
        return dialog
    }

    // =========================================================
    //  5. PASSWORD REQUIREMENTS
    // =========================================================

    fun showPasswordRequirements(
        context: Context,
        password: String,
        onOk: () -> Unit = {}
    ): Dialog {
        val dialog = buildBaseDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_password_requirements, null)

        setCheckState(context, view, R.id.checkLength,   R.id.labelLength,   password.length >= 8)
        setCheckState(context, view, R.id.checkUppercase, R.id.labelUppercase, password.any { it.isUpperCase() })
        setCheckState(context, view, R.id.checkDigit,    R.id.labelDigit,    password.any { it.isDigit() })

        view.findViewById<Button>(R.id.btnOk).setOnClickListener {
            dialog.dismiss()
            onOk()
        }

        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.show()
        animateDialogIn(view)
        return dialog
    }

    private fun setCheckState(
        context: Context,
        root: View,
        checkId: Int,
        labelId: Int,
        passed: Boolean
    ) {
        val checkView = root.findViewById<TextView>(checkId)
        val labelView = root.findViewById<TextView>(labelId)
        if (passed) {
            checkView.setBackgroundResource(R.drawable.bg_check_pass)
            checkView.setTextColor(context.getColor(R.color.dlg_check_pass_text))
            checkView.text = "✓"
            labelView.setTextColor(context.getColor(R.color.dlg_text_primary))
        } else {
            checkView.setBackgroundResource(R.drawable.bg_check_fail)
            checkView.setTextColor(context.getColor(R.color.dlg_check_fail_text))
            checkView.text = "✗"
            labelView.setTextColor(context.getColor(R.color.dlg_text_muted))
        }
    }

    // =========================================================
    //  6. DOWNLOAD PROGRESS DIALOG
    // =========================================================

    class DownloadProgressDialog(
        private val dialog: Dialog,
        private val tvTitle: TextView,
        private val tvDownloadedSize: TextView,
        private val tvPercent: TextView,
        private val progressBar: ProgressBar,
        private val tvSpeedEta: TextView
    ) {
        fun updateProgress(
            percent: Int,
            downloaded: String,
            total: String,
            speedEta: String? = null
        ) {
            tvDownloadedSize.text = "$downloaded / $total"
            tvPercent.text = "$percent%"
            speedEta?.let { tvSpeedEta.text = "⚡ $it" }
            ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, percent).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        fun setStatus(status: String) { tvSpeedEta.text = status }
        fun setTitle(title: String)   { tvTitle.text = title }
        fun dismiss() { if (dialog.isShowing) dialog.dismiss() }
        fun isShowing() = dialog.isShowing
    }

    fun showDownloadProgress(
        context: Context,
        version: String,
        onCancel: () -> Unit
    ): DownloadProgressDialog {
        val dialog = buildBaseDialog(context)
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_download_progress, null)

        view.findViewById<TextView>(R.id.tvTitle).text = "Downloading v$version..."

        val progressDialog = DownloadProgressDialog(
            dialog           = dialog,
            tvTitle          = view.findViewById(R.id.tvTitle),
            tvDownloadedSize = view.findViewById(R.id.tvDownloadedSize),
            tvPercent        = view.findViewById(R.id.tvPercent),
            progressBar      = view.findViewById(R.id.progressBar),
            tvSpeedEta       = view.findViewById(R.id.tvSpeedEta)
        )

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
            onCancel()
        }

        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.show()
        animateDialogIn(view)
        return progressDialog
    }

    // =========================================================
    //  PRIVATE HELPERS
    // =========================================================

    /** Dialog dengan background transparan agar shape XML kita yang tampil */
    private fun buildBaseDialog(context: Context): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes?.dimAmount = 0.55f
        }
        return dialog
    }

    /** Slide-up + fade-in saat dialog muncul */
    private fun animateDialogIn(view: View) {
        view.alpha = 0f
        view.translationY = 32f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(260)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }
}
