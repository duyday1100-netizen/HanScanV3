package com.hanscan.v3.data;

import com.hanscan.v3.model.OcrChar;
import java.util.ArrayList;
import java.util.List;

public class SmartSegmenter {
    private final ChineseDictionary dictionary;
    public SmartSegmenter(ChineseDictionary dictionary) { this.dictionary = dictionary; }

    public List<Integer> smartSelection(List<OcrChar> chars, int center) {
        List<Integer> out = new ArrayList<>();
        List<int[]> ranges = dictionary.candidatesAround(chars, center);
        if (!ranges.isEmpty()) {
            int[] r = ranges.get(0); // longest-first
            for (int i = r[0]; i <= r[1]; i++) out.add(i);
        } else if (center >= 0 && center < chars.size()) {
            out.add(center);
        }
        return out;
    }
}
