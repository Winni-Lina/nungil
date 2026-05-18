package com.example.myapplication.guardian.schedule.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.model.Task
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleAddActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DATE   = "extra_date"
        const val EXTRA_HOUR   = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
    }

    private val repository = ScheduleRepository()
    private val taskList   = mutableListOf<Task>()

    // ScheduleFragment에서 넘어온 날짜/시간
    private var selectedDate   = ""
    private var selectedHour   = 9
    private var selectedMinute = 0
    private var selectedTaskId = -1

    private lateinit var tvPrefilledDate: TextView
    private lateinit var tvPrefilledTime: TextView
    private lateinit var spinnerTask: Spinner
    private lateinit var etLocation: TextInputEditText
    private lateinit var etNote: TextInputEditText
    private lateinit var btnRecord: ImageButton
    private lateinit var tvAnalyzeResult: TextView
    private lateinit var pbStepsLoading: ProgressBar
    private lateinit var tvStepTimeout: TextView
    private lateinit var llSteps: LinearLayout

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        pbStepsLoading.visibility = View.GONE
        tvStepTimeout.visibility  = View.VISIBLE
    }

    private val displayDateFmt = SimpleDateFormat("M월 d일 (EEE)", Locale.KOREAN)
    private val filterDateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_add)

        // Intent extras 수신
        val cal = Calendar.getInstance()
        selectedDate   = intent.getStringExtra(EXTRA_DATE)   ?: filterDateFmt.format(cal.time)
        selectedHour   = intent.getIntExtra(EXTRA_HOUR,   cal.get(Calendar.HOUR_OF_DAY))
        selectedMinute = intent.getIntExtra(EXTRA_MINUTE, 0)

        tvPrefilledDate  = findViewById(R.id.tvPrefilledDate)
        tvPrefilledTime  = findViewById(R.id.tvPrefilledTime)
        spinnerTask      = findViewById(R.id.spinnerTask)
        etLocation       = findViewById(R.id.etLocation)
        etNote           = findViewById(R.id.etNote)
        btnRecord        = findViewById(R.id.btnRecord)
        tvAnalyzeResult  = findViewById(R.id.tvAnalyzeResult)
        pbStepsLoading   = findViewById(R.id.pbStepsLoading)
        tvStepTimeout    = findViewById(R.id.tvStepTimeout)
        llSteps          = findViewById(R.id.llSteps)

        // 뒤로 버튼
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // 날짜/시간 표시
        refreshDateTimeDisplay()

        // 날짜 변경 버튼
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangeDate)
            .setOnClickListener { showDatePicker() }

        // SC-003: 시간 변경 (tvPrefilledTime 클릭)
        tvPrefilledTime.setOnClickListener { showTimePicker() }

        // 저장 버튼
        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        setupTaskSpinner()
        setupLocationPicker()
        setupRecordButton()
        setupSaveButton(btnSave)
    }

    private fun refreshDateTimeDisplay() {
        try {
            val parsed = filterDateFmt.parse(selectedDate)
            tvPrefilledDate.text = if (parsed != null) displayDateFmt.format(parsed) else selectedDate
        } catch (_: Exception) {
            tvPrefilledDate.text = selectedDate
        }
        tvPrefilledTime.text = "%02d:%02d".format(selectedHour, selectedMinute)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
            refreshDateTimeDisplay()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    // SC-003: 시간 선택
    private fun showTimePicker() {
        TimePickerDialog(this, { _, hour, minute ->
            selectedHour   = hour
            selectedMinute = minute
            refreshDateTimeDisplay()
        }, selectedHour, selectedMinute, true).show()
    }

    // SC-004: 장소 선택 드롭다운
    private val locationOptions = arrayOf("세탁실", "부엌", "거실", "화장실", "방", "기타")

    private fun setupLocationPicker() {
        etLocation.isFocusable = false
        etLocation.isClickable = true
        etLocation.setOnClickListener { showLocationDialog() }
    }

    private fun showLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("장소 선택")
            .setItems(locationOptions) { _, which ->
                if (locationOptions[which] == "기타") {
                    etLocation.isFocusableInTouchMode = true
                    etLocation.setText("")
                    etLocation.requestFocus()
                } else {
                    etLocation.isFocusable = false
                    etLocation.setText(locationOptions[which])
                }
            }
            .show()
    }

    // SC-002: 과업 스피너 + 단계 조회
    private fun setupTaskSpinner() {
        repository.getWhitelistTasks { result ->
            when (result) {
                is ApiResult.Success -> runOnUiThread {
                    taskList.addAll(result.data)
                    spinnerTask.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_item,
                        taskList.map { it.taskName }
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                    spinnerTask.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            selectedTaskId = taskList[pos].taskId
                            loadTaskSteps(selectedTaskId)
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                }
                is ApiResult.Error -> runOnUiThread {
                    Toast.makeText(this, "과업 목록을 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTaskSteps(taskId: Int) {
        llSteps.visibility = View.GONE
        llSteps.removeAllViews()
        tvStepTimeout.visibility  = View.GONE
        pbStepsLoading.visibility = View.VISIBLE

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 10_000)

        repository.getTaskSteps(taskId) { result ->
            handler.removeCallbacks(timeoutRunnable)
            runOnUiThread {
                pbStepsLoading.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        if (result.data.isEmpty()) return@runOnUiThread
                        llSteps.visibility = View.VISIBLE
                        result.data.forEachIndexed { i, step ->
                            val tv = TextView(this).apply {
                                text      = "${i + 1}. $step"
                                textSize  = 12f
                                setTextColor(0xFF444444.toInt())
                                setPadding(0, 6, 0, 6)
                            }
                            llSteps.addView(tv)
                        }
                    }
                    is ApiResult.Error -> { /* 단계 없어도 등록 가능 */ }
                }
            }
        }
    }

    // SC-005: 녹음 → 분석
    private fun setupRecordButton() {
        btnRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
                return@setOnClickListener
            }
            if (isRecording) stopRecordingAndAnalyze() else startRecording()
        }
    }

    private fun startRecording() {
        recordingFile = File(cacheDir, "note_${System.currentTimeMillis()}.m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(recordingFile!!.absolutePath)
            prepare(); start()
        }
        isRecording = true
        btnRecord.setImageResource(android.R.drawable.ic_media_pause)
        Toast.makeText(this, "녹음 중... 다시 누르면 완료", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecordingAndAnalyze() {
        mediaRecorder?.apply { stop(); release() }
        mediaRecorder = null; isRecording = false
        btnRecord.setImageResource(android.R.drawable.ic_btn_speak_now)

        val file = recordingFile ?: return
        tvAnalyzeResult.text = "분석 중..."; tvAnalyzeResult.visibility = View.VISIBLE

        ApiClient.postAudioFile("/v1/nungil/analyze", file) { result ->
            runOnUiThread {
                when (result) {
                    is ApiResult.Success -> {
                        try {
                            val json = JSONObject(result.data)
                            val text = json.optString("result", json.optString("text", result.data))
                            tvAnalyzeResult.text = "📝 $text"
                            etNote.setText(text)
                        } catch (_: Exception) { tvAnalyzeResult.text = result.data }
                    }
                    is ApiResult.Error -> tvAnalyzeResult.text = "분석 실패: ${result.message}"
                }
            }
        }
    }

    private fun setupSaveButton(btnSave: com.google.android.material.button.MaterialButton) {
        btnSave.setOnClickListener {
            if (selectedTaskId == -1) {
                Toast.makeText(this, "과업을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val timeStr   = "%02d:%02d".format(selectedHour, selectedMinute)
            val location  = etLocation.text?.toString()?.trim() ?: ""
            val note      = etNote.text?.toString()?.trim() ?: ""
            val taskName  = taskList.firstOrNull { it.taskId == selectedTaskId }?.taskName ?: "과업"
            val locationLabel = if (location.isEmpty()) "장소 미지정" else location

            // SC-008: 저장 확인 다이얼로그
            AlertDialog.Builder(this)
                .setTitle("일정 저장 확인")
                .setMessage("📋 $taskName\n🕐 $selectedDate $timeStr\n📍 $locationLabel\n\n이 일정을 저장하고 알림을 설정할까요?")
                .setPositiveButton("확인") { _, _ ->
                    btnSave.isEnabled = false
                    repository.addSchedule(selectedTaskId, selectedDate, timeStr, location, note) { result ->
                        runOnUiThread {
                            btnSave.isEnabled = true
                            when (result) {
                                is ApiResult.Success -> {
                                    Toast.makeText(this, "일정이 등록됐어요!", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                                is ApiResult.Error -> Toast.makeText(this, "등록 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        mediaRecorder?.release()
        super.onDestroy()
    }
}
