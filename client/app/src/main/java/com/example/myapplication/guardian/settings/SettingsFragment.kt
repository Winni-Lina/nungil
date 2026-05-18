package com.example.myapplication.guardian.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.onboarding.LoginActivity
import com.example.myapplication.guardian.qr.QrScanActivity
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.model.Task
import org.json.JSONObject

class SettingsFragment : Fragment() {

    private val repository = ScheduleRepository()
    private val taskList = mutableListOf<Task>()
    private lateinit var whitelistAdapter: WhitelistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvName    = view.findViewById<TextView>(R.id.tvGuardianName)
        val tvId      = view.findViewById<TextView>(R.id.tvGuardianId)
        val rv        = view.findViewById<RecyclerView>(R.id.rvWhitelist)
        val pb        = view.findViewById<ProgressBar>(R.id.pbWhitelist)
        val btnLogout = view.findViewById<android.widget.Button>(R.id.btnLogout)
        val btnAdd    = view.findViewById<TextView>(R.id.btnAddTask)

        tvName.text = if (Session.guardianName.isNotEmpty()) Session.guardianName else "보호자"
        tvId.text   = "ID: ${Session.guardianId}"

        whitelistAdapter = WhitelistAdapter(taskList) { task ->
            confirmDeleteTask(task)
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        rv.adapter = whitelistAdapter

        loadWhitelist(pb)

        btnAdd.setOnClickListener { onAddTaskClicked() }

        view.findViewById<android.widget.Button>(R.id.btnLinkUser).setOnClickListener {
            startActivity(Intent(requireContext(), QrScanActivity::class.java))
        }

        view.findViewById<android.widget.Button>(R.id.btnUserInfo).setOnClickListener {
            showUserInfoDialog()
        }

        setupNotificationSettings(view)

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("로그아웃 할까요?")
                .setPositiveButton("로그아웃") { _, _ ->
                    Session.logout()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun loadWhitelist(pb: ProgressBar) {
        pb.visibility = View.VISIBLE
        repository.getWhitelistTasks { result ->
            activity?.runOnUiThread {
                pb.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        taskList.clear()
                        taskList.addAll(result.data)
                        whitelistAdapter.notifyDataSetChanged()
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(context, "과업 목록 로드 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun onAddTaskClicked() {
        ApiClient.get("/v1/guardian/tasks") { result ->
            activity?.runOnUiThread {
                when (result) {
                    is ApiResult.Success -> {
                        try {
                            val arr = JSONObject(result.data).getJSONArray("tasks")
                            val registeredIds = taskList.map { it.taskId }.toSet()
                            val available = mutableListOf<Task>()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val id = obj.getInt("taskId")
                                if (id !in registeredIds) {
                                    available.add(Task(id, obj.getString("taskName")))
                                }
                            }
                            if (available.isEmpty()) {
                                Toast.makeText(context, "추가할 수 있는 과업이 없어요.", Toast.LENGTH_SHORT).show()
                            } else {
                                showTaskSelectDialog(available)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "과업 목록 로드 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is ApiResult.Error -> Toast.makeText(context, "과업 목록 로드 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTaskSelectDialog(available: List<Task>) {
        val names = available.map { it.taskName }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("과업 추가")
            .setItems(names) { _, which -> addTask(available[which]) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun addTask(task: Task) {
        val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/whitelist"
        val body = JSONObject().apply { put("taskId", task.taskId) }.toString()
        ApiClient.post(path, body) { result ->
            activity?.runOnUiThread {
                when (result) {
                    is ApiResult.Success -> {
                        try {
                            val json = JSONObject(result.data)
                            when (json.optString("status")) {
                                "SUCCESS" -> {
                                    taskList.add(task)
                                    whitelistAdapter.notifyItemInserted(taskList.size - 1)
                                    Toast.makeText(context, "'${task.taskName}' 추가됐어요.", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    val msg = when (json.optString("errorCode")) {
                                        "ITEM_EXISTS" -> "이미 등록된 과업이에요."
                                        else          -> json.optString("message", "추가에 실패했어요.")
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "응답 처리 오류", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is ApiResult.Error -> Toast.makeText(context, "추가 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupNotificationSettings(view: View) {
        val switchSchedule = view.findViewById<SwitchCompat>(R.id.switchNotifSchedule)
        val switchRepeat   = view.findViewById<SwitchCompat>(R.id.switchNotifRepeat)
        val switchDnd      = view.findViewById<SwitchCompat>(R.id.switchDnd)
        val tvDndTime      = view.findViewById<TextView>(R.id.tvDndTime)

        switchSchedule.isChecked = Session.notifScheduleEnabled
        switchRepeat.isChecked   = Session.notifRepeatEnabled
        switchDnd.isChecked      = Session.dndEnabled
        tvDndTime.visibility     = if (Session.dndEnabled) View.VISIBLE else View.GONE
        tvDndTime.text           = formatDndTime()

        switchSchedule.setOnCheckedChangeListener { _, checked ->
            Session.notifScheduleEnabled = checked
        }
        switchRepeat.setOnCheckedChangeListener { _, checked ->
            Session.notifRepeatEnabled = checked
        }
        switchDnd.setOnCheckedChangeListener { _, checked ->
            Session.dndEnabled = checked
            tvDndTime.visibility = if (checked) View.VISIBLE else View.GONE
        }
        tvDndTime.setOnClickListener { showDndTimePicker() }
    }

    private fun showDndTimePicker() {
        TimePickerDialog(requireContext(), { _, startH, _ ->
            Session.dndStart = startH
            TimePickerDialog(requireContext(), { _, endH, _ ->
                Session.dndEnd = endH
                view?.findViewById<TextView>(R.id.tvDndTime)?.text = formatDndTime()
            }, Session.dndEnd, 0, true).show()
        }, Session.dndStart, 0, true).show()
    }

    private fun formatDndTime() = "%02d:00 ~ %02d:00".format(Session.dndStart, Session.dndEnd)

    private fun showUserInfoDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }
        val etName = EditText(requireContext()).apply { hint = "이름 (예: 홍길동)" }
        val etPhone = EditText(requireContext()).apply {
            hint = "전화번호 (예: 010-1234-5678)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        container.addView(etName)
        container.addView(etPhone)

        AlertDialog.Builder(requireContext())
            .setTitle("피보호자 정보 등록")
            .setView(container)
            .setPositiveButton("저장") { _, _ ->
                val name  = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                registerUserInfo(name, phone)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun registerUserInfo(name: String, phone: String) {
        val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/userinfo"
        val body = JSONObject().apply {
            put("userName", name)
            put("userPhone", phone)
        }.toString()
        ApiClient.post(path, body) { result ->
            activity?.runOnUiThread {
                when (result) {
                    is ApiResult.Success -> Toast.makeText(context, "피보호자 정보가 저장됐어요.", Toast.LENGTH_SHORT).show()
                    is ApiResult.Error   -> Toast.makeText(context, "저장 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDeleteTask(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("과업 삭제")
            .setMessage("'${task.taskName}' 과업을 목록에서 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                repository.deleteWhitelistTask(task.taskId) { result ->
                    activity?.runOnUiThread {
                        when (result) {
                            is ApiResult.Success -> {
                                taskList.remove(task)
                                whitelistAdapter.notifyDataSetChanged()
                                Toast.makeText(context, "삭제됐어요.", Toast.LENGTH_SHORT).show()
                            }
                            is ApiResult.Error -> {
                                Toast.makeText(context, "삭제 실패: ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<ProgressBar>(R.id.pbWhitelist)?.let { loadWhitelist(it) }
    }
}

// 화이트리스트 어댑터 (SettingsFragment 전용)
class WhitelistAdapter(
    private val list: MutableList<Task>,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<WhitelistAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView      = view.findViewById(R.id.tvTaskName)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_whitelist_task, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = list[position]
        holder.tvName.text = task.taskName
        holder.btnDelete.setOnClickListener { onDelete(task) }
    }
}
