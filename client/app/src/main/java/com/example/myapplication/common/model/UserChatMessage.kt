package com.example.myapplication.common.model

import android.graphics.Bitmap

class UserChatMessage(
    var content: String?,
    val type: Int,
    val isImage: Boolean = false,
    val imageBitmap: Bitmap? = null,
    val suggestions: MutableList<String?>? = null
) {
    companion object {
        const val TYPE_MINE: Int = 0
        const val TYPE_OTHER: Int = 1
    }
}