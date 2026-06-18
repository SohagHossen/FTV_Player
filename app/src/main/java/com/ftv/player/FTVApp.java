package com.ftv.player;

import android.app.Application;
import android.content.SharedPreferences;
import android.text.TextUtils;

public class FTVApp extends Application {
    private static FTVApp instance;
    private SharedPreferences prefs;
    private static final String PREF_NAME = "ftv_settings";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
    }

    public static FTVApp getInstance() {
        return instance;
    }

    public String getServerUrl() {
        return prefs.getString("server_url", getString(R.string.server_url_default));
    }

    public void setServerUrl(String url) {
        prefs.edit().putString("server_url", url).apply();
    }

    public String getM3uUrl() {
        return prefs.getString("m3u_url", "");
    }

    public void setM3uUrl(String url) {
        prefs.edit().putString("m3u_url", url).apply();
    }
}
