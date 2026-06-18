package com.ftv.player.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ftv.player.FTVApp;
import com.ftv.player.R;
import com.ftv.player.data.model.Channel;
import com.ftv.player.data.source.ChannelRepository;
import com.ftv.player.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BrowseActivity extends AppCompatActivity {

    private RecyclerView channelList;
    private ChannelAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyText;
    private EditText urlInput;
    private Button btnPlayUrl;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<Channel> allChannels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);
        toolbar.setBackgroundColor(getResources().getColor(R.color.primary_dark));
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_scan) {
                ChannelRepository.clearProbeCache();
                loadChannels();
                return true;
            }
            if (item.getItemId() == R.id.action_settings) {
                showSettingsDialog();
                return true;
            }
            return false;
        });

        urlInput = findViewById(R.id.url_input);
        btnPlayUrl = findViewById(R.id.btn_play_url);
        channelList = findViewById(R.id.channel_list);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        emptyText = findViewById(R.id.empty_text);

        channelList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter(allChannels, channel -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("channel_name", channel.getName());
            intent.putExtra("channel_url", channel.getUrl());
            startActivity(intent);
        });
        channelList.setAdapter(adapter);

        btnPlayUrl.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                playUrl(url, "Stream");
            } else {
                Toast.makeText(this, "Enter a stream URL", Toast.LENGTH_SHORT).show();
            }
        });

        swipeRefresh.setOnRefreshListener(() -> {
            ChannelRepository.clearProbeCache();
            loadChannels();
        });
        loadChannels();
    }

    private void playUrl(String url, String name) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_name", name);
        intent.putExtra("channel_url", url);
        startActivity(intent);
    }

    private void showSettingsDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        EditText serverInput = view.findViewById(R.id.server_url_input);
        EditText m3uInput = view.findViewById(R.id.m3u_url_input);

        serverInput.setText(FTVApp.getInstance().getServerUrl());
        m3uInput.setText(FTVApp.getInstance().getM3uUrl());

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    String serverUrl = serverInput.getText().toString().trim();
                    if (!serverUrl.isEmpty()) FTVApp.getInstance().setServerUrl(serverUrl);
                    String m3uUrl = m3uInput.getText().toString().trim();
                    FTVApp.getInstance().setM3uUrl(m3uUrl);
                    loadChannels();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadChannels() {
        emptyText.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(true);
        allChannels.clear();
        adapter.notifyDataSetChanged();

        String serverUrl = FTVApp.getInstance().getServerUrl();
        new ChannelRepository(serverUrl).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(Map<String, List<Channel>> channels) {
                mainHandler.post(() -> {
                    for (List<Channel> list : channels.values()) {
                        allChannels.addAll(list);
                    }
                    adapter.notifyDataSetChanged();
                    swipeRefresh.setRefreshing(false);
                    if (allChannels.isEmpty()) {
                        emptyText.setText(R.string.no_channels);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    String m3uUrl = FTVApp.getInstance().getM3uUrl();
                    if (!TextUtils.isEmpty(m3uUrl)) {
                        loadChannelsFromM3u(m3uUrl);
                    } else {
                        swipeRefresh.setRefreshing(false);
                        emptyText.setText(R.string.error_loading);
                        emptyText.setVisibility(View.VISIBLE);
                        Toast.makeText(BrowseActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void loadChannelsFromM3u(String url) {
        if (url.endsWith(".m3u8") || url.endsWith(".m3u")) {
            Channel ch = new Channel("Stream", url);
            allChannels.add(ch);
            adapter.notifyDataSetChanged();
            swipeRefresh.setRefreshing(false);
            return;
        }
        new ChannelRepository(url).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(Map<String, List<Channel>> channels) {
                mainHandler.post(() -> {
                    for (List<Channel> list : channels.values()) {
                        allChannels.addAll(list);
                    }
                    adapter.notifyDataSetChanged();
                    swipeRefresh.setRefreshing(false);
                    if (allChannels.isEmpty()) {
                        emptyText.setText(R.string.no_channels);
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    swipeRefresh.setRefreshing(false);
                    emptyText.setText(R.string.error_loading);
                    emptyText.setVisibility(View.VISIBLE);
                    Toast.makeText(BrowseActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
