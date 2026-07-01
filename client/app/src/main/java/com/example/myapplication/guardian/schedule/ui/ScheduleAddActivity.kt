package com.example.myapplication.guardian.schedule.ui

import android.app.DatePickerDialog
import android.graphics.Color
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
import com.google.android.material.switchmaterial.SwitchMaterial
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
    private lateinit var switchRepeat: SwitchMaterial
    private lateinit var llRepeatOptions: LinearLayout
    // 요일 토글버튼 (Calendar.MONDAY=2 ~ Calendar.SUNDAY=1 순서)
    private val dayToggles = mutableMapOf<Int, ToggleButton>() // key = Calendar.DAY_OF_WEEK

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
        switchRepeat     = findViewById(R.id.switchRepeat)
        llRepeatOptions  = findViewById(R.id.llRepeatOptions)

        // 요일 토글 매핑 (Calendar 상수 기준)
        dayToggles[Calendar.MONDAY]    = findViewById(R.id.tbMon)
        dayToggles[Calendar.TUESDAY]   = findViewById(R.id.tbTue)
        dayToggles[Calendar.WEDNESDAY] = findViewById(R.id.tbWed)
        dayToggles[Calendar.THURSDAY]  = findViewById(R.id.tbThu)
        dayToggles[Calendar.FRIDAY]    = findViewById(R.id.tbFri)
        dayToggles[Calendar.SATURDAY]  = findViewById(R.id.tbSat)
        dayToggles[Calendar.SUNDAY]    = findViewById(R.id.tbSun)

        switchRepeat.setOnCheckedChangeListener { _, checked ->
            llRepeatOptions.visibility = if (checked) View.VISIBLE else View.GONE
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        refreshDateTimeDisplay()

        findViewById<View>(R.id.rowDate).setOnClickListener { showDatePicker() }
        findViewById<View>(R.id.rowTime).setOnClickListener { showTimePicker() }

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
        val timeCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
        }
        // 오전/오후 h:mm (예: 오후 2:30) — 보호자 친숙
        tvPrefilledTime.text = SimpleDateFormat("a h:mm", Locale.KOREAN).format(timeCal.time)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
            refreshDateTimeDisplay()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker() {
        val view = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val pickerAmPm   = view.findViewById<NumberPicker>(R.id.pickerAmPm)
        val pickerHour   = view.findViewById<NumberPicker>(R.id.pickerHour)
        val pickerMinute = view.findViewById<NumberPicker>(R.id.pickerMinute)

        pickerAmPm.minValue = 0
        pickerAmPm.maxValue = 1
        pickerAmPm.displayedValues = arrayOf("오전", "오후")

        pickerHour.minValue = 1
        pickerHour.maxValue = 12

        pickerMinute.minValue = 0
        pickerMinute.maxValue = 59
        pickerMinute.setFormatter { "%02d".format(it) }

        val isPm = selectedHour >= 12
        pickerAmPm.value = if (isPm) 1 else 0
        pickerHour.value = when {
            selectedHour == 0 -> 12
            selectedHour > 12 -> selectedHour - 12
            else              -> selectedHour
        }
        pickerMinute.value = selectedMinute

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        view.findViewById<View>(R.id.btnCancelTime).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.btnConfirmTime).setOnClickListener {
            val pm  = pickerAmPm.value == 1
            val h12 = pickerHour.value
            selectedHour = when {
                !pm && h12 == 12 -> 0
                pm && h12 != 12  -> h12 + 12
                else             -> h12
            }
            selectedMinute = pickerMinute.value
            refreshDateTimeDisplay()
            dialog.dismiss()
        }
        dialog.show()
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
                    spinnerTask.adapter = object : ArrayAdapter<String>(
                        this, android.R.layout.simple_spinner_item,
                        taskList.map { it.taskName }
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                            val v = super.getView(position, convertView, parent)
                            (v as? TextView)?.setTextColor(Color.BLACK)
                            return v
                        }
                        override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                            val v = super.getDropDownView(position, convertView, parent)
                            (v as? TextView)?.setTextColor(Color.BLACK)
                            return v
                        }
                    }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

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
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
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
            val steps = collectSteps()
            if (steps.isEmpty()) {
                Toast.makeText(this, "AI 맞춤 단계를 설정하세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val timeStr   = "%02d:%02d".format(selectedHour, selectedMinute)
            val location  = etLocation.text?.toString()?.trim() ?: ""
            val note      = etNote.text?.toString()?.trim() ?: ""
            val taskName  = taskList.firstOrNull { it.taskId == selectedTaskId }?.taskName ?: "과업"
            val locationLabel = if (location.isEmpty()) "장소 미지정" else location
            val stepInfo  = "${steps.size}단계"

            AlertDialog.Builder(this)
                .setTitle("일정 저장 확인")
                .setMessage("📋 $taskName\n🕐 $selectedDate $timeStr\n📍 $locationLabel\n📝 $stepInfo\n\n이 일정을 저장하고 알림을 설정할까요?")
                .setPositiveButton("확인") { _, _ ->
                    btnSave.isEnabled = false
                    val isRepeat = switchRepeat.isChecked
                    val selectedDays = if (isRepeat) {
                        dayToggles.filter { it.value.isChecked }.keys.toList()
                    } else emptyList()

                    if (isRepeat && selectedDays.isEmpty()) {
                        Toast.makeText(this, "반복할 요일을 하나 이상 선택해주세요.", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        return@setPositiveButton
                    }

                    if (!isRepeat) {
                        // 단일 일정 등록
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
                    } else {
                        // 반복 일정 등록 (4주 = 28일치, 선택 요일에 해당하는 날짜)
                        val dates = buildRepeatDates(selectedDays, 28)
                        registerRepeatSchedules(dates, timeStr, location, note, steps, btnSave)
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    /**
     * 오늘부터 [daysAhead]일 이내에서 [targetDays] 요일(Calendar.MONDAY 등)에 해당하는 날짜 반환
     */
    private fun buildRepeatDates(targetDays: List<Int>, daysAhead: Int): List<String> {
        val result = mutableListOf<String>()
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0..daysAhead) {
            if (cal.get(Calendar.DAY_OF_WEEK) in targetDays) {
                result.add(fmt.format(cal.time))
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    /**
     * dates 목록 순서대로 순차 등록. 실패 시 Toast만 표시하고 계속 진행.
     */
    private fun registerRepeatSchedules(
        dates: List<String>,
        time: String,
        location: String,
        note: String,
        steps: List<String>,
        btnSave: com.google.android.material.button.MaterialButton
    ) {
        var successCount = 0
        var failCount    = 0
        var pending      = dates.size

        if (pending == 0) {
            btnSave.isEnabled = true
            Toast.makeText(this, "해당 요일이 없어요.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "루틴 등록 중... (총 ${dates.size}회)", Toast.LENGTH_SHORT).show()

        dates.forEach { date ->
            repository.addSchedule(selectedTaskId, date, time, location, note, steps) { result ->
                when (result) {
                    is ApiResult.Success -> successCount++
                    is ApiResult.Error   -> failCount++
                }
                pending--
                if (pending == 0) {
                    runOnUiThread {
                        btnSave.isEnabled = true
                        val msg = if (failCount == 0) "루틴 ${successCount}회 등록 완료!"
                                  else "루틴 등록 완료 (성공 $successCount / 실패 $failCount)"
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        if (successCount > 0) finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
