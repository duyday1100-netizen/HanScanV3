package com.hanscan.v3.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class FlashcardStore {
    private static final String PREF = "hanscan_flashcards";
    private static final String KEY = "cards";
    private final SharedPreferences prefs;

    public FlashcardStore(Context context) { prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public synchronized void add(String zh, String pinyin, String vi, String source) {
        try {
            JSONArray a = getAll();
            for (int i = 0; i < a.length(); i++) if (zh.equals(a.getJSONObject(i).optString("zh"))) return;
            JSONObject o = new JSONObject();
            o.put("zh", zh); o.put("pinyin", pinyin); o.put("vi", vi); o.put("source", source); o.put("createdAt", System.currentTimeMillis());
            JSONArray b = new JSONArray(); b.put(o);
            for (int i = 0; i < a.length(); i++) b.put(a.get(i));
            prefs.edit().putString(KEY, b.toString()).apply();
        } catch (Exception ignored) {}
    }

    public JSONArray getAll() {
        try { return new JSONArray(prefs.getString(KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
}
