package com.example.myapplication.user.chat

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.common.model.UserChatMessage
import com.example.myapplication.R

class ChatAdapter(
    private val chatList: MutableList<UserChatMessage>,
    private val listener: OnSuggestionClickListener
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    interface OnSuggestionClickListener {
        fun onSuggestionClick(text: String?)
    }

    override fun getItemViewType(position: Int): Int {
        // UserChatMessage에 정의된 TYPE_MINE(0), TYPE_OTHER(1) 반환
        return chatList[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        // viewType에 따라 나(Mine)와 상대방(Other) 레이아웃 결정
        val layoutRes = if (viewType == UserChatMessage.TYPE_MINE) {
            R.layout.item_chat_mine
        } else {
            R.layout.item_chat_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = chatList[position]

        // 1. 텍스트 내용 설정
        holder.tvContent?.apply {
            if (!msg.content.isNullOrEmpty()) {
                text = msg.content
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        // 2. 이미지 설정 (이미지 메시지인 경우)
        holder.ivImage?.apply {
            if (msg.isImage && msg.imageBitmap != null) {
                visibility = View.VISIBLE
                setImageBitmap(msg.imageBitmap)
            } else {
                visibility = View.GONE
            }
        }

        // 3. 추천 질문(Suggestions) 칩 생성
        holder.layoutSuggestions?.apply {
            removeAllViews() // 기존에 생성된 칩 제거
            msg.suggestions?.let { list ->
                if (list.isNotEmpty()) {
                    visibility = View.VISIBLE
                    for (suggestionText in list) {
                        if (suggestionText == null) continue

                        val chip = TextView(context).apply {
                            text = suggestionText
                            textSize = 14f
                            setTextColor(Color.parseColor("#1E88E5"))
                            setBackgroundResource(R.drawable.bg_suggestion_chip)
                            setPadding(30, 15, 30, 15)
                            gravity = Gravity.CENTER

                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 15, 15) // 칩 간의 간격
                            }

                            setOnClickListener { listener.onSuggestionClick(suggestionText) }
                        }
                        addView(chip)
                    }
                } else {
                    visibility = View.GONE
                }
            } ?: run { visibility = View.GONE }
        }
    }

    override fun getItemCount(): Int = chatList.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvContent: TextView? = itemView.findViewById(R.id.tvContent)
        val ivImage: ImageView? = itemView.findViewById(R.id.ivImage)
        val layoutSuggestions: LinearLayout? = itemView.findViewById(R.id.layoutSuggestions)
    }
}