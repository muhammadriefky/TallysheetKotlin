package com.example.handheldapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.User
import com.example.handheldapp.repository.AuthRepository
import com.example.handheldapp.repository.MasterDataCacheRepository
import com.example.handheldapp.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val masterDataCacheRepo: MasterDataCacheRepository  // ✅ INJECT untuk cache support
) : ViewModel() {

    // Step 1: Companies
    private val _companies = MutableLiveData<Resource<List<Company>>>()
    val companies: LiveData<Resource<List<Company>>> = _companies

    // Step 2: Users
    private val _users = MutableLiveData<Resource<List<User>>>()
    val users: LiveData<Resource<List<User>>> = _users

    // Step 3: Login (Boolean karena Result Success/Error ditangani Resource)
    private val _loginResult = MutableLiveData<Resource<Boolean>>()
    val loginResult: LiveData<Resource<Boolean>> = _loginResult

    // Step 4: Branches
    private val _branches = MutableLiveData<Resource<List<Branch>>>()
    val branches: LiveData<Resource<List<Branch>>> = _branches

    // Selected data untuk internal & UI
    private val _selectedCompany = MutableLiveData<Company?>()
    val selectedCompany: LiveData<Company?> = _selectedCompany

    private val _selectedUser = MutableLiveData<User?>()
    val selectedUser: LiveData<User?> = _selectedUser

    // ★ SECURITY FIX: Track selected branch untuk validasi saat login
    private val _selectedBranch = MutableLiveData<Branch?>()
    val selectedBranch: LiveData<Branch?> = _selectedBranch

    // --- LOGIC METHODS ---

    /**
     * STEP 1: Load Companies saat aplikasi dibuka
     * Menggunakan MasterDataCacheRepository untuk offline support
     * Tidak blocking bahkan saat maintenance
     */
    fun loadCompanies() {
        viewModelScope.launch {
            _companies.value = Resource.Loading()

            val result = masterDataCacheRepo.getCompanies()

            _companies.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = {
                    // Tetap allow user untuk continue, hanya log error
                    Resource.Error(it.message ?: "Gagal memuat companies. Menggunakan data cache.")
                }
            )
        }
    }

    /**
     * STEP 2: Pilih Company dan load branches berdasarkan company (SEBELUM login)
     * NEW: Flow Company → Branch → Login
     */
    fun selectCompany(company: Company) {
        _selectedCompany.value = company
        // Auto-load branches setelah pilih company
        loadBranchesByCompany(company.comCode)
    }

    /**
     * STEP 2.5: Load Branches berdasarkan Company (SEBELUM login)
     * NEW: Untuk flow Company → Branch → Login
     */
    private fun loadBranchesByCompany(comCode: String) {
        viewModelScope.launch {
            _branches.value = Resource.Loading()

            val result = masterDataCacheRepo.getBranchesByCompany(comCode)

            _branches.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = {
                    Resource.Error(it.message ?: "Gagal memuat branches. Menggunakan data cache.")
                }
            )
        }
    }

    /**
     * STEP 3: Proses Login (DEPRECATED - Use loginWithCredentials instead)
     * ★ SECURITY FIX: Menambahkan validasi branch
     */
    fun login(password: String) {
        val company = _selectedCompany.value
        val user = _selectedUser.value
        val branch = _selectedBranch.value

        if (company == null || user == null) {
            _loginResult.value = Resource.Error("Pilih Perusahaan dan User terlebih dahulu")
            return
        }

        if (branch == null) {
            _loginResult.value = Resource.Error("Pilih Branch terlebih dahulu")
            return
        }

        // user.email atau fallback ke usrCode
        val email = user.email ?: user.usrCode ?: ""

        viewModelScope.launch {
            authRepository.login(
                companyCode = company.comCode,
                companyName = company.comName,
                email = email,
                password = password,
                branchCode = branch.cabCode  // ★ Kirim branch_code ke API
            ).collect { resource ->
                _loginResult.value = resource

                // Jika login berhasil, otomatis panggil loadBranches
                if (resource is Resource.Success && resource.data == true) {
                    loadBranches()
                }
            }
        }
    }

    /**
     * STEP 4: Proses Login dengan Email dan Password Manual
     * ★ SECURITY FIX: Mengirim branch_code untuk validasi di backend
     *
     * Role Access Policy:
     * - Admin IT & SPV: Bisa pilih branch apa saja (backend skip validasi usr_areacode)
     * - Checker, Admin Gudang, Kepala Gudang: Branch harus sesuai usr_areacode (backend validasi)
     */
    fun loginWithCredentials(email: String, password: String) {
        val company = _selectedCompany.value
        val branch = _selectedBranch.value

        if (company == null) {
            _loginResult.value = Resource.Error("Pilih Perusahaan terlebih dahulu")
            return
        }

        // ★ WAJIB pilih branch untuk semua user
        // Backend akan validasi:
        // - SPV & Admin IT: Branch apa saja OK (skip check usr_areacode)
        // - Checker/Admin Gudang/Kepala: Branch harus sama dengan usr_areacode
        if (branch == null) {
            _loginResult.value = Resource.Error("Pilih branch terlebih dahulu")
            return
        }

        if (email.isBlank()) {
            _loginResult.value = Resource.Error("Email tidak boleh kosong")
            return
        }

        if (password.isBlank()) {
            _loginResult.value = Resource.Error("Password tidak boleh kosong")
            return
        }

        viewModelScope.launch {
            authRepository.login(
                companyCode = company.comCode,
                companyName = company.comName,
                email = email,
                password = password,
                branchCode = branch.cabCode  // ★ Wajib kirim branch_code (tidak pernah null)
            ).collect { resource ->
                _loginResult.value = resource

                // Set selectedUser dengan email yang digunakan login
                if (resource is Resource.Success && resource.data == true) {
                    _selectedUser.value = User(usrCode = email, email = email, usrName = email)
                }
            }
        }
    }

    /**
     * Memilih user secara lokal sebelum login
     */
    fun selectUser(user: User) {
        _selectedUser.value = user
    }

    /**
     * STEP 4: Load Branches berdasarkan User yang sedang dipilih (usr_fullname)
     * Menggunakan MasterDataCacheRepository untuk offline support
     * Tidak blocking bahkan saat maintenance
     */
    private fun loadBranches() {
        val userCode = _selectedUser.value?.usrCode ?: return

        viewModelScope.launch {
            _branches.value = Resource.Loading()

            val result = masterDataCacheRepo.getBranchesByUser(userCode)

            _branches.value = result.fold(
                onSuccess = { Resource.Success(it) },
                onFailure = {
                    // Tetap allow user untuk continue, hanya log error
                    Resource.Error(it.message ?: "Gagal memuat branches. Menggunakan data cache.")
                }
            )
        }
    }

    /**
     * STEP 3: Pilih Branch (WAJIB untuk semua user)
     * ★ SECURITY FIX: Simpan ke _selectedBranch untuk validasi saat login
     * Backend akan validate:
     * - SPV & Admin IT: Boleh pilih branch apa saja (full access)
     * - Checker/Admin Gudang/Kepala: Branch harus sesuai usr_areacode
     */
    fun selectBranch(branch: Branch) {
        _selectedBranch.value = branch  // ★ Simpan untuk validasi login
        viewModelScope.launch {
            authRepository.saveBranchSelection(
                branchCode = branch.cabCode,
                branchName = branch.cabName
            )
        }
    }

    // --- UI HELPERS ---

    fun getSelectedCompanyName(): String = _selectedCompany.value?.comCode ?: ""
    fun getSelectedUserName(): String = _selectedUser.value?.usrName ?: ""
    fun getSelectedBranchCode(): String = _selectedBranch.value?.cabCode ?: ""  // ★ Helper baru

    fun resetSelection() {
        _selectedCompany.value = null
        _selectedUser.value = null
        _selectedBranch.value = null  // ★ Reset branch juga
        _users.value = Resource.Success(emptyList())
        _branches.value = Resource.Success(emptyList())
    }
}