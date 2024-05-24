package com.nefrock.edgeocr_example.ntimes_scan;

import com.nefrock.edgeocr.Text;
import com.nefrock.edgeocr.TextMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostCodeTextMapper extends TextMapper {
    private final Pattern regexPattern;
    public PostCodeTextMapper() {
        //123-4567のような郵便番号をスキャンする
        regexPattern = Pattern.compile("^.*((\\d{3})-(\\d{4})).*$");
    }
    @Override
    public String apply(Text text) {
        String t = text.getText();
        t = t.replace("A", "4");
        t = t.replace("B", "8");
        t = t.replace("b", "6");
        t = t.replace("C", "0");
        t = t.replace("D", "0");
        t = t.replace("G", "6");
        t = t.replace("g", "9");
        t = t.replace("I", "1");
        t = t.replace("i", "1");
        t = t.replace("l", "1");
        t = t.replace("O", "0");
        t = t.replace("o", "0");
        t = t.replace("Q", "0");
        t = t.replace("q", "9");
        t = t.replace("S", "5");
        t = t.replace("s", "5");
        t = t.replace("U", "0");
        t = t.replace("Z", "2");
        t = t.replace("z", "2");
        t = t.replace("/", "1");

        Matcher m = regexPattern.matcher(t);
        if (m.find()) {
            t = m.group(1);
        }

        return t;
    }
}
