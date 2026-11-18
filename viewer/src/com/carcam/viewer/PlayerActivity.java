package com.carcam.viewer;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

/** 在线播放器（HTTP 流式播放，支持拖动进度） */
public class PlayerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra("url");
        String name = getIntent().getStringExtra("name");
        setTitle(name != null ? name : "播放");

        VideoView vv = new VideoView(this);
        MediaController mc = new MediaController(this);
        vv.setMediaController(mc);
        setContentView(vv);

        try {
            vv.setVideoURI(Uri.parse(url));
        } catch (Exception e) {
            finish();
            return;
        }
        vv.setOnPreparedListener(mp -> {
            mp.setOnVideoSizeChangedListener((mp2, w, h) -> {
                if (w > 0 && h > 0) {
                    float ar = (float) w / h;
                    if (ar > 1.2f) {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                }
            });
            vv.start();
        });
        vv.setOnErrorListener((mp, what, extra) -> {
            android.widget.Toast.makeText(this, "播放失败，可返回下载后用播放器打开", android.widget.Toast.LENGTH_LONG).show();
            return false;
        });
    }
}
