package io.c14r;

public class JobbieStringUtils {
    private JobbieStringUtils() {
    }

    static String getJsonFromImage(String image) {
        String name = getBetween('/', ':', image);
        String repo = getUntil('/', image);
        String tag = getFrom(':', image);
        return "{\"imageName\":\"" + name + "\", \"imageTag\": \"" + tag + "\", \"repositoryName\": \"" + repo + "\"}";
    }

    private static String getFrom(char from, String s) {
        return s.substring(s.indexOf(from) + 1);
    }

    private static String getBetween(char from, char to, String s) {
        return s.substring(s.indexOf(from) + 1, s.indexOf(to));
    }

    private static String getUntil(char c, String s) {
        int iend = s.indexOf(c);
        String subString = null;
        if (iend != -1) {
            subString = s.substring(0, iend);
        }
        return subString;
    }

    static String strip(String s, int n) {
        return s.substring(0, Math.min(s.length(), n));
    }
}
