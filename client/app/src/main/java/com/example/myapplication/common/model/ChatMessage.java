package com.example.myapplication.common.model;

import android.graphics.Bitmap;
import java.util.List;

public class ChatMessage {
    public static final int TYPE_MINE = 0;
    public static final int TYPE_OTHER = 1;

    private String content;
    private int type;
    private boolean isImage;
    private Bitmap bitmap;
    private List<String> suggestions;

    public ChatMessage(String content, int type, boolean isImage, Bitmap bitmap, List<String> suggestions) {
        this.content = content;
        this.type = type;
        this.isImage = isImage;
        this.bitmap = bitmap;
        this.suggestions = suggestions;
    }

    // [필수] 이 메서드가 있어야 실시간으로 글자를 바꿀 수 있습니다.
    public void setContent(String content) { this.content = content; }

    public String getContent() { return content; }
    public int getType() { return type; }
    public boolean isImage() { return isImage; }
    public Bitmap getImageBitmap() { return bitmap; }
    public List<String> getSuggestions() { return suggestions; }
}