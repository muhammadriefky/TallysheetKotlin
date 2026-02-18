package com.example.handheldapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.User
import com.example.handheldapp.databinding.ActivityLoginBinding
import com.example.handheldapp.utils.Resource
import com.example.handheldapp.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * LoginActivity - 3-Step Wizard
 *
 * STEP 1: Pilih Company (Dropdown)
 * STEP 2: Input Username + Password (Manual) + Login
 * STEP 3: Pilih Branch (Dropdown) → Navigate to MainActivity
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    private var companiesList: List<Company> = emptyList()
    private var branchesList: List<Branch> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()

        // Step 1: Tarik data company pertama kali
        viewModel.loadCompanies()
    }

    private fun setupObservers() {
        // Observe Companies
        viewModel.companies.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    resource.data?.let {
                        companiesList = it
                        setupCompanyDropdown()
                        showStep1()
                    }
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal memuat data perusahaan")
                }
            }
        }

        // Observe Login Result
        viewModel.loginResult.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    // Jika login sukses, ViewModel biasanya langsung memanggil loadBranches()
                    // Kita cukup menunggu observer branches di bawah
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Password salah atau akses ditolak")
                }
            }
        }

        // Observe Branches (Terpanggil setelah login sukses)
        viewModel.branches.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> {
                    showLoading(false)
                    resource.data?.let {
                        branchesList = it
                        if (it.isNotEmpty()) {
                            setupBranchDropdown()
                            showStep3() // Step 3: Pilih Branch
                        } else {
                            showError("User ini tidak memiliki akses ke cabang manapun")
                        }
                    }
                }
                is Resource.Error -> {
                    showLoading(false)
                    showError(resource.message ?: "Gagal memuat daftar cabang")
                }
            }
        }
    }

    private fun setupListeners() {
        // Step 1: Next setelah pilih company
        binding.btnNextStep1.setOnClickListener {
            val pos = binding.spinnerCompany.selectedItemPosition
            if (pos != -1 && pos < companiesList.size) {
                viewModel.selectCompany(companiesList[pos])
                showStep2() // Langsung ke input username + password
            }
        }

        // Step 2: Login dengan username + password yang diketik manual
        binding.btnLogin.setOnClickListener {
            val username = binding.edtUsername.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            when {
                username.isEmpty() -> {
                    binding.edtUsername.error = "Masukkan username"
                    binding.edtUsername.requestFocus()
                }
                password.isEmpty() -> {
                    binding.edtPassword.error = "Masukkan password"
                    binding.edtPassword.requestFocus()
                }
                else -> {
                    // Login dengan username + password
                    viewModel.loginWithCredentials(username, password)
                }
            }
        }

        // Step 3: Pilih branch dan navigate ke MainActivity
        binding.btnNextStep3.setOnClickListener {
            val pos = binding.spinnerBranch.selectedItemPosition
            if (pos != -1 && pos < branchesList.size) {
                viewModel.selectBranch(branchesList[pos])
                navigateToMain()
            }
        }

        // Navigasi Back
        binding.btnBackStep2.setOnClickListener { showStep1() }
        binding.btnBackStep3.setOnClickListener { showStep2() }
    }

    private fun setupCompanyDropdown() {
        // Menggunakan getDisplayText() dari model Company yang sudah direvisi
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, companiesList.map { it.getDisplayText() })
        binding.spinnerCompany.adapter = adapter
    }

    private fun setupBranchDropdown() {
        // Menggunakan getDisplayText() dari model Branch (cabCode - cabName)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, branchesList.map { it.getDisplayText() })
        binding.spinnerBranch.adapter = adapter
    }

    // Fungsi Visibility - 3 Steps
    private fun showStep1() {
        toggleCards(step = 1)
    }

    private fun showStep2() {
        toggleCards(step = 2)
        binding.tvSelectedCompany.text = viewModel.getSelectedCompanyName()
    }

    private fun showStep3() {
        toggleCards(step = 3)
    }

    private fun toggleCards(step: Int) {
        binding.cardStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        binding.cardStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        binding.cardStep3.visibility = if (step == 3) View.VISIBLE else View.GONE
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }
}
