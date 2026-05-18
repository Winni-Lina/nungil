package com.example.myapplication.core.network

import com.example.myapplication.config.AppConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

object ApiClient {

    private const val BASE_URL = AppConfig.BASE_URL+"api"
    private val client = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    fun get(path: String, onResult: (ApiResult<String>) -> Unit) {
        val request = Request.Builder().url("$BASE_URL$path").get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ApiResult.Error(e.message ?: "네트워크 오류"))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(ApiResult.Error("서버 오류 ${response.code}: ${body ?: ""}"))
                    return
                }
                if (body != null) {
                    onResult(ApiResult.Success(body))
                } else {
                    onResult(ApiResult.Error("빈 응답"))
                }
            }
        })
    }

    // SC-005: 녹음 파일 업로드 (multipart)
    fun postAudioFile(path: String, file: File, onResult: (ApiResult<String>) -> Unit) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", file.name, file.asRequestBody("audio/m4a".toMediaType()))
            .build()
        val request = Request.Builder().url("$BASE_URL$path").post(requestBody).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ApiResult.Error(e.message ?: "네트워크 오류"))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(ApiResult.Error("서버 오류 ${response.code}: ${body ?: ""}"))
                    return
                }
                if (body != null) onResult(ApiResult.Success(body))
                else onResult(ApiResult.Error("빈 응답"))
            }
        })
    }

    fun post(path: String, body: String, onResult: (ApiResult<String>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ApiResult.Error(e.message ?: "네트워크 오류"))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(ApiResult.Error("서버 오류 ${response.code}: ${body ?: ""}"))
                    return
                }
                if (body != null) {
                    onResult(ApiResult.Success(body))
                } else {
                    onResult(ApiResult.Error("빈 응답"))
                }
            }
        })
    }

    fun put(path: String, body: String, onResult: (ApiResult<String>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .put(body.toRequestBody(JSON))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ApiResult.Error(e.message ?: "네트워크 오류"))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(ApiResult.Error("서버 오류 ${response.code}: ${body ?: ""}"))
                    return
                }
                if (body != null) onResult(ApiResult.Success(body))
                else onResult(ApiResult.Error("빈 응답"))
            }
        })
    }

    fun patch(path: String, body: String, onResult: (ApiResult<String>) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL$path")
            .patch(body.toRequestBody(JSON))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ApiResult.Error(e.message ?: "네트워크 오류"))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(ApiResult.Error("서버 오류 ${response.code}: ${body ?: ""}"))
                    return
                }
                if (body != null) onResult(ApiResult.Success(body))
                else onResult(ApiResult.Error("빈 응답"))
            }
        })
    }

    fun delete(path: String, onResult: (ApiResult<String>) -> Unit) {
        val request = Request.Builder().url("$BASE_URL$path").delete().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ApiResult.Error(e.message ?: "네트워크 오류"))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    onResult(ApiResult.Error("서버 오류 ${response.code}: ${body ?: ""}"))
                    return
                }
                if (body != null) onResult(ApiResult.Success(body))
                else onResult(ApiResult.Error("빈 응답"))
            }
        })
    }
}
