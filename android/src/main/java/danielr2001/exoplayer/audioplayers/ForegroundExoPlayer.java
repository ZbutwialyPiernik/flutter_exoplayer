package danielr2001.exoplayer.audioplayers;

import danielr2001.exoplayer.interfaces.AudioPlayer;
import danielr2001.exoplayer.notifications.MediaNotificationManager;
import danielr2001.exoplayer.ExoPlayerPlugin;
import danielr2001.exoplayer.notifications.AudioObject;
import danielr2001.exoplayer.enums.PlayerState;

import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import android.util.Log;
public class ForegroundExoPlayer extends Service implements AudioPlayer {
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public ForegroundExoPlayer getService() {

            return ForegroundExoPlayer.this;
        }
    }

    private ForegroundExoPlayer foregroundExoPlayer;
    private MediaNotificationManager mediaNotificationManager;
    private Context context;
    private ExoPlayerPlugin ref;

    private float volume = 1;
    private boolean repeatMode = false;
    private boolean respectAudioFocus = false;

    private boolean released = true;
    private boolean playing = false;
    private boolean buffering = false;

    private String playerId;
    private SimpleExoPlayer player;
    private ArrayList<AudioObject> audioObjects;
    private AudioObject audioObject;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction() != null){
            if (intent.getAction().equals(MediaNotificationManager.PREVIOUS_ACTION)) {
                previous();
            } else if (intent.getAction().equals(MediaNotificationManager.PLAY_ACTION)) {
                resume();
            } else if (intent.getAction().equals(MediaNotificationManager.PAUSE_ACTION)) {
                pause();
            }else if (intent.getAction().equals(MediaNotificationManager.NEXT_ACTION)) {
                next();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.release();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        this.release();
    }

    

    @Override
    public void initAudioPlayer(ExoPlayerPlugin ref, Context context, String playerId) {
        this.ref = ref;
        this.context = context;
        this.playerId = playerId;
        this.mediaNotificationManager = new MediaNotificationManager(this, this.context);
        this.foregroundExoPlayer = this;
    }

    @Override
    public String getPlayerId() {
        return this.playerId;
    }

    @Override
    public void play(boolean repeatMode, boolean respectAudioFocus, AudioObject audioObject) {
        this.released = false;
        this.repeatMode = repeatMode;
        this.respectAudioFocus = respectAudioFocus;
        this.audioObject = audioObject;
        this.audioObjects = null;
        initExoPlayer();
        initStateChangeListener();
        player.setPlayWhenReady(true);
    }

    @Override
    public void playAll(boolean repeatMode, boolean respectAudioFocus, ArrayList<AudioObject> audioObjects) {
        this.released = false;
        this.repeatMode = repeatMode;
        this.respectAudioFocus = respectAudioFocus;
        this.audioObjects = audioObjects;
        this.audioObject = null;
        initExoPlayer();
        initStateChangeListener();
        player.setPlayWhenReady(true);
    }

    @Override
    public void next() {
        if (!this.released) {
            player.next();
            this.resume();
        }
    }

    @Override
    public void previous() { //!TODO first time go to previous (maybe make counter for 3 sec)
        if (!this.released) {
            player.previous();
            this.resume();
        }
    }

    @Override
    public void pause() {
        if (!this.released && this.playing) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    public void resume() {
        if (!this.released && !this.playing) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    public void stop() {
        if (!this.released) {
            player.stop(true);
        }
    }

    @Override
    public void release() {
        if (!this.released) {
            this.released = true;
            this.playing = false;
            this.audioObject = null;
            this.audioObjects = null;
            player.release();
            player = null;
            ref.handleStateChange(this, PlayerState.RELEASED);
            stopForeground(true);
            stopSelf();
        }
    }

    @Override
    public void setVolume(float volume) {
        if (!this.released && this.volume != volume) {
            this.volume = volume;
            player.setVolume(volume);
        }
    }

    @Override
    public long getDuration() {
        if (!this.released) {
            return player.getDuration();
        } else {
            return -1;
        }
    }

    @Override
    public long getCurrentPosition() {
        if (!this.released) {
            return player.getCurrentPosition();
        } else {
            return -1;
        }
    }

    @Override
    public void seek(int position) {
        if (!this.released) {
            player.seekTo(player.getCurrentWindowIndex(), position);
        }
    }

    @Override
    public boolean isPlaying() {
        return this.playing;
    }

    @Override
    public boolean isBackground(){
        return false;
    }
    private void initExoPlayer() {
        player = ExoPlayerFactory.newSimpleInstance(this.context, new DefaultTrackSelector());
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this.context, Util.getUserAgent(this.context, "exoPlayerLibrary"));
        player.setForegroundMode(true);
        // playlist/single audio load
        if(this.audioObjects != null){
            Log.d("hii", "playlist!");
            ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource();
            for (AudioObject audioObject : audioObjects) {
                MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(audioObject.getUrl()));
                concatenatingMediaSource.addMediaSource(mediaSource);
            }
            player.prepare(concatenatingMediaSource);
        }else{
            Log.d("hii", "single!");
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(this.audioObject.getUrl()));
            player.prepare(mediaSource);
        }
        //handle audio focus
        if(this.respectAudioFocus){ //! TODO catch duck pause!
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build();
            player.setAudioAttributes(audioAttributes, true);
        }
        //set repeat mode
        if (repeatMode) {
            player.setRepeatMode(player.REPEAT_MODE_ALL);
        }
    }

    private void initStateChangeListener() {
        player.addListener(new Player.EventListener() {

            @Override
            public void onTimelineChanged(Timeline timeline, @Nullable Object manifest, int reason) {
                if(reason == Player.TIMELINE_CHANGE_REASON_DYNAMIC){
                    ref.handlePositionUpdates();
                    ref.handleDurationUpdates();
                }
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) { 
                switch (playbackState){
                    case Player.STATE_BUFFERING:{
                        //play first time
                        buffering = true;
                    }
                    case Player.STATE_READY:{
                        if(!buffering){
                            if (playWhenReady) {
                                //resumed
                                if(audioObjects != null) {
                                    mediaNotificationManager.makeNotification(true);
                                }else {
                                    mediaNotificationManager.makeNotification(true);
                                }
                                playing = true;
                                ref.handleStateChange(foregroundExoPlayer, PlayerState.PLAYING);
                                ref.handlePositionUpdates();
                            }else{
                                //paused
                                if(audioObjects != null) {
                                    mediaNotificationManager.makeNotification(false);
                                }else {
                                    mediaNotificationManager.makeNotification(false);
                                }
                                playing = false;
                                ref.handleStateChange(foregroundExoPlayer, PlayerState.PAUSED);
                            }
                        }else{
                            //play
                            if(audioObjects != null) {
                                mediaNotificationManager.makeNotification(audioObjects.get(player.getCurrentWindowIndex()), true);
                            }else {
                                mediaNotificationManager.makeNotification(audioObject, true);
                            }
                            playing = true;
                            buffering = false;
                            ref.handleStateChange(foregroundExoPlayer, PlayerState.PLAYING);
                        }
                        break;
                    }
                    case Player.STATE_ENDED:{
                        //completed
                        playing = false;
                        ref.handleStateChange(foregroundExoPlayer, PlayerState.COMPLETED);
                        break;
                    }
                    case Player.STATE_IDLE:{
                        //stopped
                        playing = false;
                        ref.handleStateChange(foregroundExoPlayer, PlayerState.STOPPED);
                        break;
                    }
                    //handle of released is in release method!
                }
            }
        });
    }
}