package com.routeonomy.app.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.routeonomy.app.R;

/** Short chime played when a briefing segment was heard to the end (res/raw/segment_chime.wav, made for this app). */
final class SegmentChime {
    /** Pause between two segments, chime included. */
    static final long GAP_MILLIS = 3000;

    private final SoundPool pool;
    private final int sound;
    private boolean loaded;

    SegmentChime(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, id, status) -> loaded = status == 0);
        sound = pool.load(context, R.raw.segment_chime, 1);
    }

    /** Plays once; silently skipped until the sound has loaded. */
    void play() {
        if (loaded) pool.play(sound, 1f, 1f, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
