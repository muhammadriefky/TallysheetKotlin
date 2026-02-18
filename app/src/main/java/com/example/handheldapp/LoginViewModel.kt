//package com.example.handheldapp.viewmodel
//
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.example.handheldapp.data.model.Branch
//import com.example.handheldapp.data.model.Company
//import com.example.handheldapp.data.model.User
//import com.example.handheldapp.repository.AuthRepository
//import com.example.handheldapp.utils.Resource
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//@HiltViewModel
//class LoginViewModel @Inject constructor(
//    private val authRepository: AuthRepository
//) : ViewModel() {
//
//    // Step 1: Companies
//    private val _companies = MutableLiveData<Resource<List<Company>>>()
//    val companies: LiveData<Resource<List<Company>>> = _companies
//
//    // Step 2: Users
//    private val _users = MutableLiveData<Resource<List<User>>>()
//    val users: LiveData<Resource<List<User>>> = _users
//
//    // Step 3: Login (Ganti ke Boolean agar sinkron dengan Repository)
//    private val _loginResult = MutableLiveData<Resource<Boolean>>()
//    val loginResult: LiveData<Resource<Boolean>> = _loginResult
//
//    // Step 4: Branches
//    private val _branches = MutableLiveData<Resource<List<Branch>>>()
//    val branches: LiveData<Resource<List<Branch>>> = _branches
//
//    // Selected data (Gunakan nullable untuk keamanan)
//    private val _selectedCompany = MutableLiveData<Company?>()
//    val selectedCompany: LiveData<Company?> = _selectedCompany
//
//    private val _selectedUser = MutableLiveData<User?>()
//    val selectedUser: LiveData<User?> = _selectedUser
//
//    // STEP 1: Load Companies
//    fun loadCompanies() {
//        viewModelScope.launch {
//            authRepository.getCompanies().collect { resource ->
//                _companies.value = resource
//            }
//        }
//    }
//
//    // STEP 2: Load Users by Company
//    fun loadUsers(company: Company) {
//        _selectedCompany.value = company
//        viewModelScope.launch {
//            authRepository.getUsersByCompany(company.comCode).collect { resource ->
//                _users.value = resource
//            }
//        }
//    }
//
//    // STEP 3: Login
//    fun login(user: User, password: String) {
//        _selectedUser.value = user
//        val company = _selectedCompany.value
//
//        if (company == null) {
//            _loginResult.value = Resource.Error("Perusahaan belum dipilih")
//            return
//        }
//
//        // FIX: Pastikan usrCode tidak null sebelum dikirim ke repository
//        val username = user.usrCode ?: ""
//
//        viewModelScope.launch {
//            authRepository.login(
//                companyCode = company.comCode,
//                companyName = company.comName,
//                username = username,
//                password = password
//            ).collect { resource ->
//                _loginResult.value = resource
//
//                // Jika login sukses, otomatis panggil loadBranches
//                if (resource is Resource.Success && resource.data == true) {
//                    loadBranches()
//                }
//            }
//        }
//    }
//
//    /**
//     * STEP 4: Load Branches berdasarkan User yang login
//     * FIX: Menggunakan user.usrCode (loginname) sesuai revisi Laravel
//     */
//    private fun loadBranches() {
//        val user = _selectedUser.value ?: return
//        val userCode = user.usrCode
//
//        if (userCode.isNullOrBlank()) {
//            _branches.value = Resource.Error("Sesi user tidak valid")
//            return
//        }
//
//        viewModelScope.launch {
//            // FIX: Gunakan !! karena sudah dicek isNullOrBlank
//            authRepository.getBranchesByUser(userCode!!).collect { resource ->
//                _branches.value = resource
//            }
//        }
//    }
//
//    // STEP 5: Save selected branch
//    fun selectBranch(branch: Branch, onComplete: () -> Unit) {
//        viewModelScope.launch {
//            authRepository.saveBranchSelection(
//                branchCode = branch.cabCode,
//                branchName = branch.cabName
//            )
//            onComplete()
//        }
//    }
//
//    // UI Helpers
//    fun getSelectedCompanyName(): String = _selectedCompany.value?.comCode ?: ""
//    fun getSelectedUserName(): String = _selectedUser.value?.usrName ?: ""
//
//    fun resetSelection() {
//        _selectedCompany.value = null
//        _selectedUser.value = null
//        _users.value = Resource.Success(emptyList())
//    }
//}