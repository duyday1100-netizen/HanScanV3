package com.hanscan.v3.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

public final class PinyinUtils {
    private PinyinUtils() {}

    public static String toPinyin(String text) {
        if (text == null || text.isEmpty()) return "";
        HanyuPinyinOutputFormat fmt = new HanyuPinyinOutputFormat();
        fmt.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        fmt.setToneType(HanyuPinyinToneType.WITH_TONE_MARK);
        fmt.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
        StringBuilder out = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            String ch = new String(Character.toChars(cp));
            if (isHan(cp)) {
                try {
                    String[] arr = PinyinHelper.toHanyuPinyinStringArray((char) cp, fmt);
                    if (arr != null && arr.length > 0) {
                        if (out.length() > 0) out.append(' ');
                        out.append(arr[0]);
                    } else {
                        if (out.length() > 0) out.append(' ');
                        out.append(ch);
                    }
                } catch (Exception e) {
                    if (out.length() > 0) out.append(' ');
                    out.append(ch);
                }
            } else if (!Character.isWhitespace(cp)) {
                out.append(ch);
            }
            offset += Character.charCount(cp);
        }
        return out.toString();
    }

    public static boolean isHan(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN;
    }
}
