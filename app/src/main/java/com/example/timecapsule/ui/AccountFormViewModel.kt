package com.example.timecapsule.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timecapsule.domain.result.OperationResult
import kotlinx.coroutines.launch

/** Owns one in-memory submission per account form across view recreation. */
abstract class AccountFormViewModel : ViewModel() {
    private val _result = MutableLiveData<OperationResult<String>?>()
    val result: LiveData<OperationResult<String>?> = _result

    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy

    /** Claim the form synchronously before launching work, including after a view recreation. */
    protected fun submit(operation: suspend () -> OperationResult<String>) {
        if (_busy.value == true) return
        _busy.value = true
        _result.value = null
        viewModelScope.launch {
            val result = try {
                operation()
            } finally {
                _busy.value = false
            }
            _result.value = result
        }
    }

    /** Clear a consumed result. The running operation retains its own progress state. */
    fun clearState() {
        _result.value = null
    }
}
