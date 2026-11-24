package com.huixue.sandbox.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathSanitizer {

    // Removes absolute paths from error messages, returning only the filename.
    // e.g., /sandbox/workdir/tmp_xx/Main.java:12 -> Main.java:12
    public static String sanitize(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        // Match paths that end with .java, .cpp, .py and possibly follow by :lineNumber
        // E.g., /some/path/to/Main.java:12 -> Main.java:12
        String regex = "(?:/[a-zA-Z0-9_.-]+)+/([a-zA-Z0-9_.-]+\\.(?:java|cpp|py)(?::\\d+)?)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(errorMessage);

        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
