package com.hanscan.v3.model;

import android.graphics.RectF;

public class OcrChar {
    public final String text;
    public final RectF box;
    public final int lineIndex;

    public OcrChar(String text, RectF box, int lineIndex) {
        this.text = text;
        this.box = box;
        this.lineIndex = lineIndex;
    }
}
