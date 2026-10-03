package com.hanscan.v3.model;

import android.graphics.Bitmap;
import java.util.List;

public class OcrResult {
    public final Bitmap bitmap;
    public final List<OcrChar> chars;
    public final String rawText;
    public final float qualityScore;
    public final String variantName;

    public OcrResult(Bitmap bitmap, List<OcrChar> chars, String rawText, float qualityScore, String variantName) {
        this.bitmap = bitmap;
        this.chars = chars;
        this.rawText = rawText;
        this.qualityScore = qualityScore;
        this.variantName = variantName;
    }
}
