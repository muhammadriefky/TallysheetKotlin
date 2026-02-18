package com.example.handheldapp.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.handheldapp.ui.scan.ScanActivityLogFragment
import com.example.handheldapp.ui.scan.ScanSummaryFragment

class ScanHistoryPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ScanSummaryFragment.newInstance() // Tab 1: Ringkasan
            1 -> ScanActivityLogFragment.newInstance() // Tab 2: Riwayat Lengkap
            else -> ScanSummaryFragment.newInstance()
        }
    }
}
