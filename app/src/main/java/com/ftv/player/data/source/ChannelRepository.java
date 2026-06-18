package com.ftv.player.data.source;

import com.ftv.player.data.model.Channel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChannelRepository {

    public interface Callback {
        void onSuccess(Map<String, List<Channel>> channels);
        void onError(String error);
    }

    private final String serverUrl;

    private static Map<String, List<Channel>> probeCache = null;
    private static long probeCacheTime = 0;
    private static final long PROBE_CACHE_TTL = 300_000;

    public ChannelRepository(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    public void loadChannels(Callback callback) {
        new Thread(() -> {
            try {
                Map<String, List<Channel>> channels = tryLoadM3U();
                if (channels != null && !channels.isEmpty()) {
                    callback.onSuccess(channels);
                    return;
                }

                channels = tryLoadFromHtml();
                if (channels != null && !channels.isEmpty()) {
                    callback.onSuccess(channels);
                    return;
                }

                channels = tryLoadFromApi();
                if (channels != null && !channels.isEmpty()) {
                    callback.onSuccess(channels);
                    return;
                }

                channels = tryLoadProbedStreams();
                if (channels != null && !channels.isEmpty()) {
                    callback.onSuccess(channels);
                    return;
                }

                callback.onError("No channels found on server");
            } catch (Exception e) {
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "FTVPlayer/1.0");
        int code = conn.getResponseCode();
        if (code != 200) return null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString();
    }

    private Map<String, List<Channel>> tryLoadM3U() throws Exception {
        String m3uContent = httpGet(serverUrl + "/playlist.m3u");
        if (m3uContent == null) m3uContent = httpGet(serverUrl + "/get.php");
        if (m3uContent == null) return null;
        if (!m3uContent.startsWith("#EXTM3U")) return null;

        Map<String, List<Channel>> result = new LinkedHashMap<>();
        String[] lines = m3uContent.split("\n");
        String currentCategory = "All";
        String currentName = null;
        String currentLogo = null;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) {
                Matcher m = Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                currentCategory = m.find() ? m.group(1) : "All";
                if (currentCategory.isEmpty()) currentCategory = "All";

                Matcher m2 = Pattern.compile("tvg-logo=\"([^\"]*)\"").matcher(line);
                currentLogo = m2.find() ? m2.group(1) : null;

                Matcher m3 = Pattern.compile(",(.*?)$").matcher(line);
                currentName = m3.find() ? m3.group(1).trim() : null;
            } else if (!line.startsWith("#") && !line.isEmpty() && currentName != null) {
                Channel ch = new Channel(currentName, line, currentLogo, currentCategory);
                result.computeIfAbsent(currentCategory, k -> new ArrayList<>()).add(ch);
                currentName = null;
                currentLogo = null;
            }
        }
        return result;
    }

    private Map<String, List<Channel>> tryLoadFromHtml() throws Exception {
        String html = httpGet(serverUrl);
        if (html == null) return null;

        Map<String, List<Channel>> result = new LinkedHashMap<>();
        List<Channel> allChannels = new ArrayList<>();

        Pattern linkPattern = Pattern.compile("<a[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = linkPattern.matcher(html);

        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String text = matcher.group(2).trim();

            if (href.startsWith("#") || href.startsWith("javascript:") || text.isEmpty()) continue;

            String url;
            if (href.startsWith("http")) url = href;
            else if (href.startsWith("/")) url = serverUrl + href;
            else url = serverUrl + "/" + href;

            boolean isVideo = href.contains(".m3u8") || href.contains(".mp4") || href.contains(".ts")
                    || href.contains(".m3u") || href.contains(".flv") || href.contains("stream")
                    || href.contains("play") || href.contains("live");

            if (isVideo || isLikelyChannel(text)) {
                Channel ch = new Channel(cleanName(text), url);
                allChannels.add(ch);
            }
        }

        if (allChannels.isEmpty()) {
            Pattern optionPattern = Pattern.compile("<option[^>]*value\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>([^<]+)</option>", Pattern.CASE_INSENSITIVE);
            Matcher optionMatcher = optionPattern.matcher(html);
            while (optionMatcher.find()) {
                String value = optionMatcher.group(1).trim();
                String text = optionMatcher.group(2).trim();
                if (value.isEmpty() || text.isEmpty()) continue;
                String url = value.startsWith("http") ? value : serverUrl + "/" + value;
                Channel ch = new Channel(cleanName(text), url);
                allChannels.add(ch);
            }
        }

        if (allChannels.isEmpty()) {
            Pattern divPattern = Pattern.compile("<div[^>]*class\\s*=\\s*['\"][^'\"]*channel[^'\"]*['\"][^>]*>([^<]+)</div>", Pattern.CASE_INSENSITIVE);
            Matcher divMatcher = divPattern.matcher(html);
            while (divMatcher.find()) {
                String text = divMatcher.group(1).trim();
                if (!text.isEmpty()) {
                    Channel ch = new Channel(cleanName(text), "");
                    allChannels.add(ch);
                }
            }
        }

        if (!allChannels.isEmpty()) result.put("All", allChannels);
        return result;
    }

    private Map<String, List<Channel>> tryLoadFromApi() throws Exception {
        String json = httpGet(serverUrl + "/player_api.php");
        if (json == null) return null;

        Map<String, List<Channel>> result = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONObject channels = root.optJSONObject("channels");
            if (channels != null) {
                JSONArray categories = channels.names();
                if (categories != null) {
                    for (int i = 0; i < categories.length(); i++) {
                        String category = categories.getString(i);
                        JSONArray catChannels = channels.getJSONArray(category);
                        List<Channel> list = new ArrayList<>();
                        for (int j = 0; j < catChannels.length(); j++) {
                            JSONObject ch = catChannels.getJSONObject(j);
                            String name = ch.optString("name", "Channel " + (j + 1));
                            String url = ch.optString("url", "");
                            String logo = ch.optString("logo", null);
                            list.add(new Channel(name, url, logo, category));
                        }
                        result.put(category, list);
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private boolean isLikelyChannel(String text) {
        if (text.length() < 2) return false;
        String upper = text.toUpperCase();
        return upper.contains("TV") || upper.contains("HD") || upper.contains("CHANNEL")
                || upper.contains("NEWS") || upper.contains("SPORTS") || upper.contains("MOVIE")
                || upper.contains("ENTERTAINMENT") || upper.contains("MUSIC")
                || upper.contains("DOCUMENTARY") || upper.contains("KIDS")
                || text.matches(".*\\d+.*");
    }

    private Map<String, List<Channel>> tryLoadProbedStreams() throws Exception {
        if (probeCache != null && System.currentTimeMillis() - probeCacheTime < PROBE_CACHE_TTL) {
            return probeCache;
        }

        Map<String, List<Channel>> result = new LinkedHashMap<>();
        List<Channel> channels = new ArrayList<>();
        int consecutiveFailures = 0;
        final int MAX_PROBE = 100;
        final int MAX_CONSECUTIVE_FAILURES = 10;

        for (int i = 0; i < MAX_PROBE; i++) {
            String probeUrl = serverUrl + "/" + i + ".m3u8";
            try {
                URL url = new URL(probeUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(1000);
                conn.setRequestProperty("User-Agent", "FTVPlayer/1.0");
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String firstLine = reader.readLine();
                    reader.close();
                    if (firstLine != null && firstLine.startsWith("#EXTM3U")) {
                        String name = "Channel " + i;
                        channels.add(new Channel(name, probeUrl, null, "All"));
                        consecutiveFailures = 0;
                        continue;
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) break;
        }

        if (!channels.isEmpty()) {
            result.put("All", channels);
            probeCache = result;
            probeCacheTime = System.currentTimeMillis();
            return result;
        }
        return null;
    }

    public static void clearProbeCache() {
        probeCache = null;
        probeCacheTime = 0;
    }

    private String cleanName(String name) {
        return name.replaceAll("<[^>]+>", "").trim();
    }
}
