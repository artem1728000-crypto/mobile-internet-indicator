package ru.artemzozula.mobileindicator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

final class ReleaseCheck {
    private static final String REPO = "artem1728000-crypto/mobile-internet-indicator";
    private static final String APK = "mobile-internet-indicator.apk";

    record Release(String version, String download) {}

    static Release latest(String installed) throws Exception {
        URL api = new URL("https://api.github.com/repos/" + REPO + "/releases/latest");
        HttpsURLConnection connection = (HttpsURLConnection) api.openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) != -1;) {
                if (bytes.size() + n > 262144) throw new java.io.IOException("Release response too large");
                bytes.write(buffer, 0, n);
            }
            JSONObject release = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            String tag = release.getString("tag_name");
            if (!tag.matches("v[0-9]+[.][0-9]+[.][0-9]+")) throw new java.io.IOException("Invalid release tag");
            if (!newer(tag.substring(1), installed)) return null;
            JSONArray assets = release.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                if (APK.equals(assets.getJSONObject(i).optString("name"))) {
                    return new Release(tag.substring(1),
                            "https://github.com/" + REPO + "/releases/download/" + tag + "/" + APK);
                }
            }
            throw new java.io.IOException("APK missing");
        } finally {
            connection.disconnect();
        }
    }

    private static boolean newer(String remote, String installed) {
        String[] left = remote.split("[.]"), right = installed.split("[.]");
        for (int i = 0; i < 3; i++) {
            int comparison = Integer.compare(Integer.parseInt(left[i]), Integer.parseInt(right[i]));
            if (comparison != 0) return comparison > 0;
        }
        return false;
    }
}
