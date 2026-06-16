package com.ftv.player.ui.browse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
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
            if (item.getItemId() == R.id.action_settings) {
                showServerUrlDialog();
                return true;
            }
            return false;
        });

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

        swipeRefresh.setOnRefreshListener(this::loadChannels);

        loadChannels();
    }

    private void showServerUrlDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Server URL");

        EditText input = new EditText(this);
        input.setText(FTVApp.getInstance().getServerUrl());
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                FTVApp.getInstance().setServerUrl(url);
                loadChannels();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadChannels() {
        emptyText.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(true);

        String serverUrl = FTVApp.getInstance().getServerUrl();
        new ChannelRepository(serverUrl).loadChannels(new ChannelRepository.Callback() {
            @Override
            public void onSuccess(Map<String, List<Channel>> channels) {
                mainHandler.post(() -> {
                    allChannels.clear();
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
