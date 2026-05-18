package com.example.myapplication.guardian.qr

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.onboarding.GuardianChatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class QrScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_ONBOARDING = "from_onboarding"
    }

    private val fromOnboarding get() = intent.getBooleanExtra(EXTRA_FROM_ONBOARDING, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        val ivQr     = findViewById<ImageView>(R.id.ivGuardianQr)
        val pbLoad   = findViewById<ProgressBar>(R.id.pbQrLoading)
        val tvStatus = findViewById<TextView>(R.id.tvQrStatus)
        val btnLink  = findViewById<Button>(R.id.btnLinkUser)
        val btnNext  = findViewById<Button>(R.id.btnNext)
        val btnBack  = findViewById<android.widget.ImageButton>(R.id.btnBack)

        if (fromOnboarding) btnBack.visibility = View.GONE

        btnBack.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            startActivity(Intent(this, GuardianChatActivity::class.java))
            finish()
        }

        // 이미 userIdx가 저장돼 있으면 기존 QR 바로 표시
        if (Session.userIdx > 0) {
            showQr(Session.guardianId, Session.userIdx, ivQr, tvStatus, btnNext)
            btnLink.text = "새 사용자 추가 연동"
        }

        btnLink.setOnClickListener {
            btnLink.isEnabled  = false
            ivQr.visibility    = View.GONE
            btnNext.visibility = View.GONE
            pbLoad.visibility  = View.VISIBLE
            tvStatus.text      = ""

            val body = JSONObject().apply {
                put("guardianId", Session.guardianId)
            }.toString()

            ApiClient.post("/v1/guardian/users", body) { result ->
                runOnUiThread {
                    pbLoad.visibility = View.GONE
                    btnLink.isEnabled = true
                    when (result) {
                        is ApiResult.Success -> {
                            android.util.Log.d("QrScan", "응답 원문: ${result.data}")
                            try {
                                val json   = JSONObject(result.data)
                                val status = json.optString("status", "")
                                if (status != "SUCCESS") {
                                    val msg = json.optString("message", "서버 오류가 발생했어요.")
                                    tvStatus.text = "연동 실패: ${msg.ifEmpty { "서버 오류" }}"
                                    return@runOnUiThread
                                }
                                val obj = if (json.has("result")) json.getJSONObject("result") else json
                                val userId  = obj.optString("userId", Session.guardianId)
                                val userIdx = when {
                                    obj.has("userIdx") -> obj.getInt("userIdx")
                                    obj.has("idx")     -> obj.getInt("idx")
                                    else -> { tvStatus.text = "연동 실패: 사용자 정보 없음"; return@runOnUiThread }
                                }
                                Session.userIdx = userIdx
                                showQr(userId, userIdx, ivQr, tvStatus, btnNext)
                            } catch (e: Exception) {
                                android.util.Log.e("QrScan", "파싱 오류: ${e.message}")
                                tvStatus.text = "응답 처리 오류: ${e.message}"
                            }
                        }
                        is ApiResult.Error -> tvStatus.text = "연동 실패: ${result.message}"
                    }
                }
            }
        }
    }

    private fun showQr(userId: String, userIdx: Int, ivQr: ImageView, tvStatus: TextView, btnNext: Button) {
        val qrContent = JSONObject().apply {
            put("userId", userId)
            put("userIdx", userIdx)
        }.toString()
        ivQr.setImageBitmap(generateQr(qrContent, 600))
        ivQr.visibility    = View.VISIBLE
        tvStatus.text      = "사용자 기기로 이 QR을 스캔해주세요"
        if (fromOnboarding) btnNext.visibility = View.VISIBLE
    }

    private fun generateQr(content: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
