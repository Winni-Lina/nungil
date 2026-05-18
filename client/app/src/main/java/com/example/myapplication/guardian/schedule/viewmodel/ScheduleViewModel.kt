package com.example.myapplication.guardian.schedule.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.model.Schedule

class ScheduleViewModel : ViewModel() {

    private val repository = ScheduleRepository()

    private val _schedules = MutableLiveData<List<Schedule>>()
    val schedules: LiveData<List<Schedule>> = _schedules

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _deleteSuccess = MutableLiveData<Boolean>()
    val deleteSuccess: LiveData<Boolean> = _deleteSuccess

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess

    fun loadSchedules() {
        repository.getSchedules { result ->
            when (result) {
                is ApiResult.Success -> _schedules.postValue(result.data)
                is ApiResult.Error   -> _error.postValue(result.message)
            }
        }
    }

    fun deleteSchedule(scheduleId: Int) {
        repository.deleteSchedule(scheduleId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    _deleteSuccess.postValue(true)
                    loadSchedules()
                }
                is ApiResult.Error -> {
                    _error.postValue("삭제 실패: ${result.message}")
                    _deleteSuccess.postValue(false)
                }
            }
        }
    }

    // SM-002: 일정 시간 수정
    fun updateScheduleTime(scheduleId: Int, date: String, time: String) {
        repository.updateScheduleTime(scheduleId, date, time) { result ->
            when (result) {
                is ApiResult.Success -> {
                    _updateSuccess.postValue(true)
                    loadSchedules()
                }
                is ApiResult.Error -> {
                    _error.postValue("수정 실패: ${result.message}")
                    _updateSuccess.postValue(false)
                }
            }
        }
    }
}
