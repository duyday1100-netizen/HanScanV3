package com.hanscan.v3.data;

import android.content.Context;
import com.hanscan.v3.model.DictionaryEntry;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChineseDictionary {
    private final Map<String, DictionaryEntry> map = new HashMap<>();

    public ChineseDictionary(Context context) {
        load(context);
    }

    private void load(Context context) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                context.getAssets().open("dictionary/core_vi.json"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                DictionaryEntry e = new DictionaryEntry();
                e.simplified = o.optString("s");
                e.traditional = o.optString("t", e.simplified);
                e.pinyin = o.optString("p");
                e.vi = o.optString("vi");
                e.source = o.optString("src", "HanScan Core");
                e.confidence = o.optString("confidence", "VERIFIED");
                if (!e.simplified.isEmpty()) map.put(e.simplified, e);
                if (!e.traditional.isEmpty()) map.put(e.traditional, e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load core dictionary", e);
        }
    }

    public DictionaryEntry lookup(String text) {
        return map.get(text);
    }

    public boolean contains(String text) {
        return map.containsKey(text);
    }

    public int size() { return map.size(); }

    public List<int[]> candidatesAround(List<com.hanscan.v3.model.OcrChar> chars, int center) {
        if (center < 0 || center >= chars.size()) return Collections.emptyList();
        int line = chars.get(center).lineIndex;
        List<int[]> out = new ArrayList<>();
        for (int len = 6; len >= 2; len--) {
            for (int start = Math.max(0, center - len + 1); start <= center; start++) {
                int end = start + len - 1;
                if (end >= chars.size()) continue;
                if (chars.get(start).lineIndex != line || chars.get(end).lineIndex != line) continue;
                StringBuilder sb = new StringBuilder();
                boolean sameLine = true;
                for (int i = start; i <= end; i++) {
                    if (chars.get(i).lineIndex != line) { sameLine = false; break; }
                    sb.append(chars.get(i).text);
                }
                if (sameLine && contains(sb.toString())) out.add(new int[]{start, end});
            }
        }
        return out;
    }
}
