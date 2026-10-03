package com.hanscan.v3.model;

public class DictionaryEntry {
    public String simplified;
    public String traditional;
    public String pinyin;
    public String vi;
    public String source;
    public String confidence;

    public String displaySource() {
        String c = confidence == null ? "" : confidence;
        String s = source == null ? "" : source;
        if (c.isEmpty()) return s;
        if (s.isEmpty()) return c;
        return c + " · " + s;
    }
}
