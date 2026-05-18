package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import org.json.JSONObject

class SpecialNotesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_special_notes)

        val etNotes = findViewById<TextInputEditText>(R.id.etNotes)
        val btnNext = findViewById<Button>(R.id.btnNext)

        btnNext.setOnClickListener {
            val note = etNotes.text.toString().trim()

            if (note.isEmpty()) {
                // 특이사항 없이 넘어가도 됨
                goNext()
                return@setOnClickListener
            }

            btnNext.isEnabled = false
            val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/profile"
            val body = JSONObject().apply {
                put("specialNote", note)
            }.toString()

            ApiClient.post(path, body) { result ->
                runOnUiThread {
                    btnNext.isEnabled = true
                    when (result) {
                        is ApiResult.Success -> goNext()
                        is ApiResult.Error -> {
                            // 저장 실패해도 다음으로 진행 (선택 사항이니까)
                            Toast.makeText(this, "특이사항 저장에 실패했지만 계속 진행해요.", Toast.LENGTH_SHORT).show()
                            goNext()
                        }
                    }
                }
            }
        }
    }

    private fun goNext() {
        startActivity(Intent(this, SetupCompleteActivity::class.java))
        finish()
    }
}
