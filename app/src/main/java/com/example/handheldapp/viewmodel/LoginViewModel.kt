package com.example.handheldapp.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.handheldapp.data.model.Branch
import com.example.handheldapp.data.model.Company
import com.example.handheldapp.data.model.User
import com.example.handheldapp.repository.AuthRepository
import com.example.handheldapp.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
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

    // --- LOGIC METHODS ---

    /**
     * STEP 1: Load Companies saat aplikasi dibuka
     */
    fun loadCompanies() {
        viewModelScope.launch {
            authRepository.getCompanies().collect { resource ->
                _companies.value = resource
            }
        }
    }

    /**
     * STEP 2: Pilih Company (tidak perlu load users lagi untuk new flow)
     */
    fun selectCompany(company: Company) {
        _selectedCompany.value = company
    }

    /**
     * STEP 3: Proses Login
     */
    fun login(password: String) {
        val company = _selectedCompany.value
        val user = _selectedUser.value

        if (company == null || user == null) {
            _loginResult.value = Resource.Error("Pilih Perusahaan dan User terlebih dahulu")
            return
        }

        // user.usrCode adalah usr_loginname dari SQL Server
        val username = user.usrCode

        viewModelScope.launch {
            authRepository.login(
                companyCode = company.comCode,
                companyName = company.comName,
                username = username,
                password = password
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
     * STEP 3 (NEW): Proses Login dengan Username dan Password Manual
     */
    fun loginWithCredentials(username: String, password: String) {
        val company = _selectedCompany.value

        if (company == null) {
            _loginResult.value = Resource.Error("Pilih Perusahaan terlebih dahulu")
            return
        }

        if (username.isBlank()) {
            _loginResult.value = Resource.Error("Username tidak boleh kosong")
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
                username = username,
                password = password
            ).collect { resource ->
                _loginResult.value = resource

                // Jika login berhasil, otomatis panggil loadBranches
                // Kita perlu set selectedUser dengan username yang diinput
                if (resource is Resource.Success && resource.data == true) {
                    // Set selectedUser dengan username yang digunakan login
                    _selectedUser.value = User(usrCode = username, usrName = username)
                    loadBranches()
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
     * STEP 4: Load Branches berdasarkan User yang sedang dipilih (usr_loginname)
     */
    private fun loadBranches() {
        val userCode = _selectedUser.value?.usrCode ?: return

        viewModelScope.launch {
            authRepository.getBranchesByUser(userCode).collect { resource ->
                _branches.value = resource
            }
        }
    }

    /**
     * STEP 5: Simpan Cabang yang dipilih dan selesaikan sesi
     */
    fun selectBranch(branch: Branch) {
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

    fun resetSelection() {
        _selectedCompany.value = null
        _selectedUser.value = null
        _users.value = Resource.Success(emptyList())
        _branches.value = Resource.Success(emptyList())
    }
}