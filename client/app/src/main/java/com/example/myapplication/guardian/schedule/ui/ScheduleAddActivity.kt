package com.example.myapplication.guardian.schedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.model.Task
import com.google.android.material.textfield.TextInputEditText
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
    private val stepEditTexts = mutableListOf<EditText>()

    private var selectedDate   = ""
    private var selectedHour   = 9
    private var selectedMinute = 0
    private var selectedTaskId = -1

    private lateinit var tvPrefilledDate: TextView
    private lateinit var tvPrefilledTime: TextView
    private lateinit var spinnerTask: Spinner
    private lateinit var etLocation: TextInputEditText
    private lateinit var etNote: TextInputEditText
    private lateinit var pbStepsLoading: ProgressBar
    private lateinit var tvStepTimeout: TextView
    private lateinit var llSteps: LinearLayout

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

        val cal = Calendar.getInstance()
        selectedDate   = intent.getStringExtra(EXTRA_DATE)   ?: filterDateFmt.format(cal.time)
        selectedHour   = intent.getIntExtra(EXTRA_HOUR,   cal.get(Calendar.HOUR_OF_DAY))
        selectedMinute = intent.getIntExtra(EXTRA_MINUTE, 0)

        tvPrefilledDate  = findViewById(R.id.tvPrefilledDate)
        tvPrefilledTime  = findViewById(R.id.tvPrefilledTime)
        spinnerTask      = findViewById(R.id.spinnerTask)
        etLocation       = findViewById(R.id.etLocation)
        etNote           = findViewById(R.id.etNote)
        pbStepsLoading   = findViewById(R.id.pbStepsLoading)
        tvStepTimeout    = findViewById(R.id.tvStepTimeout)
        llSteps          = findViewById(R.id.llSteps)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        refreshDateTimeDisplay()

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangeDate)
            .setOnClickListener { showDatePicker() }

        tvPrefilledTime.setOnClickListener { showTimePicker() }

        val btnSave = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        setupTaskSpinner()
        setupLocationPicker()
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

    private fun showTimePicker() {
        TimePickerDialog(this, { _, hour, minute ->
            selectedHour   = hour
            selectedMinute = minute
            refreshDateTimeDisplay()
        }, selectedHour, selectedMinute, true).show()
    }

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
                            // 과업 변경 시 기존 생성 단계 초기화
                            stepEditTexts.clear()
                            llSteps.removeAllViews()
                            llSteps.visibility = View.GONE
                            tvStepTimeout.visibility = View.GONE
                            showGenerateButton()
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

    /** llSteps 영역에 "AI 단계 생성" 버튼 표시 */
    private fun showGenerateButton() {
        llSteps.removeAllViews()
        llSteps.visibility = View.VISIBLE
        llSteps.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "🤖 AI 맞춤 단계 만들기"
            setOnClickListener { generateAiSteps() }
        })
    }

    /** AI에게 맞춤 단계 생성 요청 */
    private fun generateAiSteps() {
        if (selectedTaskId == -1) {
            Toast.makeText(this, "먼저 활동을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val location = etLocation.text?.toString()?.trim() ?: ""
        val note     = etNote.text?.toString()?.trim() ?: ""

        llSteps.removeAllViews()
        llSteps.visibility       = View.VISIBLE
        tvStepTimeout.visibility = View.GONE
        pbStepsLoading.visibility = View.VISIBLE
        llSteps.addView(TextView(this).apply {
            text = "AI가 맞춤 단계를 만들고 있어요..."
            textSize = 13f
            setTextColor(0xFF444444.toInt())
            setPadding(0, 8, 0, 8)
        })

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, 30_000)

        repository.generateSteps(selectedTaskId, location, note) { result ->
            handler.removeCallbacks(timeoutRunnable)
            runOnUiThread {
                pbStepsLoading.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> renderEditableSteps(result.data)
                    is ApiResult.Error   -> {
                        Toast.makeText(this, "단계 생성 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                        // 실패해도 직접 입력할 수 있게 빈 단계 1개 제공
                        renderEditableSteps(listOf(""))
                    }
                }
            }
        }
    }

    /** 생성된 단계를 편집 가능한 EditText 리스트로 표시 */
    private fun renderEditableSteps(steps: List<String>) {
        stepEditTexts.clear()
        llSteps.removeAllViews()
        llSteps.visibility = View.VISIBLE

        llSteps.addView(TextView(this).apply {
            text = "✏️ 단계를 확인하고 수정하세요"
            textSize = 13f
            setTextColor(0xFF1A73E8.toInt())
            setPadding(0, 0, 0, 8)
        })

        steps.forEach { addStepRow(it) }

        // 단계 추가 버튼 (이후 새 행은 이 버튼 앞에 삽입됨)
        llSteps.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "+ 단계 추가"
            setOnClickListener { addStepRow("") }
        })

        // 다시 생성 버튼
        llSteps.addView(com.google.android.material.button.MaterialButton(this).apply {
            text = "🔄 다시 생성"
            setOnClickListener { generateAiSteps() }
        })
    }

    /** 단계 하나에 대한 행(EditText+삭제버튼) 추가. 액션 버튼 앞에 삽입한다. */
    private fun addStepRow(initial: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 4)
        }
        val et = EditText(this).apply {
            setText(initial)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnDelete = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            background = null
            setOnClickListener {
                stepEditTexts.remove(et)
                llSteps.removeView(row)
            }
        }
        row.addView(et)
        row.addView(btnDelete)
        stepEditTexts.add(et)

        // 액션 버튼(+단계 추가, 다시 생성)이 있으면 그 앞에, 없으면 맨 끝에 삽입
        var insertIndex = llSteps.childCount
        for (i in 0 until llSteps.childCount) {
            if (llSteps.getChildAt(i) is com.google.android.material.button.MaterialButton) {
                insertIndex = i
                break
            }
        }
        llSteps.addView(row, insertIndex)
    }

    /** 편집된 단계 텍스트 수집 (빈 항목 제외) */
    private fun collectSteps(): List<String> =
        stepEditTexts.map { it.text.toString().trim() }.filter { it.isNotEmpty() }

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
            val steps     = collectSteps()
            val stepInfo  = if (steps.isEmpty()) "단계 없음" else "${steps.size}단계"

            AlertDialog.Builder(this)
                .setTitle("일정 저장 확인")
                .setMessage("📋 $taskName\n🕐 $selectedDate $timeStr\n📍 $locationLabel\n📝 $stepInfo\n\n이 일정을 저장하고 알림을 설정할까요?")
                .setPositiveButton("확인") { _, _ ->
                    btnSave.isEnabled = false
                    repository.addSchedule(selectedTaskId, selectedDate, timeStr, location, note, steps) { result ->
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
        super.onDestroy()
    }
}
