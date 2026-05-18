package com.example.myapplication.guardian.onboarding

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.common.model.ChatMessage

class ChatAdapter(
    private val chatList: List<ChatMessage>,
    private val listener: OnSuggestionClickListener
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    // 인터페이스 정의 (기존 이름 유지)
    interface OnSuggestionClickListener {
        fun onSuggestionClick(text: String?)
    }

    override fun getItemViewType(position: Int): Int {
        return chatList[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutRes = if (viewType == ChatMessage.TYPE_MINE) {
            R.layout.item_chat_mine
        } else {
            R.layout.item_chat_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = chatList[position]

        // 1. 텍스트 갱신 보장
        holder.tvContent?.let { tv ->
            val content = msg.content
            if (!content.isNullOrEmpty()) {
                tv.text = content
                tv.visibility = View.VISIBLE
            } else {
                tv.visibility = View.GONE
            }
        }

        // 2. 이미지 표시 로직
        holder.ivImage?.let { iv ->
            if (msg.isImage && msg.imageBitmap != null) {
                iv.visibility = View.VISIBLE
                iv.setImageBitmap(msg.imageBitmap)
            } else {
                iv.visibility = View.GONE
            }
        }

        // 3. 추천 질문(칩) 생성 로직
        holder.layoutSuggestions?.let { layout ->
            layout.removeAllViews() // 이전 뷰들 삭제
            val suggestions = msg.suggestions
            if (!suggestions.isNullOrEmpty()) {
                layout.visibility = View.VISIBLE
                for (text in suggestions) {
                    val chip = TextView(holder.itemView.context).apply {
                        this.text = text
                        textSize = 14f
                        setTextColor(Color.parseColor("#1E88E5"))
                        setBackgroundResource(R.drawable.bg_suggestion_chip)
                        setPadding(30, 15, 30, 15)
                        gravity = Gravity.CENTER

                        val params = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 15, 15)
                        }
                        layoutParams = params

                        setOnClickListener { listener.onSuggestionClick(text) }
                    }
                    layout.addView(chip)
                }
            } else {
                layout.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = chatList.size

    // ViewHolder 클래스 (기존 필드명 유지)
    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvContent: TextView? = itemView.findViewById(R.id.tvContent)
        val ivImage: ImageView? = itemView.findViewById(R.id.ivImage)
        val layoutSuggestions: LinearLayout? = itemView.findViewById(R.id.layoutSuggestions)
    }
}