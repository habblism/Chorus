package yhb.chorus.service;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;

import yhb.chorus.ICallback;
import yhb.chorus.IPlayer;
import yhb.chorus.R;
import yhb.chorus.app.ChorusApplication;
import yhb.chorus.entity.MP3;
import yhb.chorus.utils.LRUCache;

import static yhb.chorus.service.MainService.REMOTE_INTENT_EXIT;
import static yhb.chorus.service.MainService.REMOTE_INTENT_NEXT;
import static yhb.chorus.service.MainService.REMOTE_INTENT_PLAY_PAUSE;
import static yhb.chorus.service.MainService.REMOTE_INTENT_PREVIOUS;
import static yhb.chorus.utils.MP3Utils.getAlbumart;


public class PlayCenter {
    /*
     * three kinds of play mode
     */
    public static final int MODE_SINGLE_LOOP = 1;
    public static final int MODE_LIST_LOOP = 2;
    public static final int MODE_RANDOM = 3;

    @SuppressLint("StaticFieldLeak")
    private static PlayCenter sPlayCenter;
    private Context mContext;
    private int playMode = MODE_LIST_LOOP;
    private float mVolume = 1f;

    private ArrayList<MP3> mQueueMP3s = new ArrayList<>();
    private List<MP3> mp3s;
    private MP3 currentMP3;
    private MP3 candidateNextMP3;
    private MP3 candidatePreviousMP3;
    private IPlayer mPlayer;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mPlayer = MainService.Player.asInterface(service);
                mPlayer.registerCallback(new ICallback.Stub() {
                    @Override
                    public void onComplete() throws RemoteException {
                        next(false);
                    }

                    @Override
                    public void onProgressChange(boolean isPlaying,int progress)throws RemoteException {

                    }

                    @Override
                    public void onNewRemoteIntent(String action) {
                        switch (action) {
                            case REMOTE_INTENT_NEXT:
                                next(true);
                                break;
                            case REMOTE_INTENT_PREVIOUS:
                                previous(true);
                                break;
                            case REMOTE_INTENT_PLAY_PAUSE:
                                playOrPause();
                                break;
                            case REMOTE_INTENT_EXIT:
                                mContext.unbindService(mConnection);
                                break;
                        }
                    }

                });
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    public static PlayCenter getInstance() {
        if (sPlayCenter == null) {
            sPlayCenter = new PlayCenter();
        }
        return sPlayCenter;
    }

    private PlayCenter() {
        mContext = ChorusApplication.getsApplicationContext();

        Intent intent = new Intent(mContext, MainService.class);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /*
     * operations of controlling music mPlayer
     */

    public void playOrPause() {

        if (currentMP3 == null) {
            next(true);
            return;
        }

//        sendCommand(MainService.REMOTE_INTENT_PLAY_PAUSE);
        try {
            mPlayer.playOrPause(currentMP3);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void next(boolean fromUser) {

        if (candidateNextMP3 == null) {
            candidateNextMP3 = pickCandidatePrevious(fromUser);
        }

        currentMP3 = candidateNextMP3;

//        sendCommand(MainService.ACTION_NEXT);

        try {
            mPlayer.next(currentMP3);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        candidateNextMP3 = pickCandidateNext(fromUser);
        candidatePreviousMP3 = pickCandidatePrevious(fromUser);

    }

    public void previous(boolean fromUser) {

        if (candidatePreviousMP3 == null) {
            candidatePreviousMP3 = pickCandidatePrevious(fromUser);
        }

        currentMP3 = candidatePreviousMP3;

//        sendCommand(MainService.ACTION_PREVIOUS);
        try {
            mPlayer.previous(currentMP3);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        candidateNextMP3 = pickCandidateNext(fromUser);
        candidatePreviousMP3 = pickCandidatePrevious(fromUser);
    }

    public void point(MP3 mp3) {

        if (!mQueueMP3s.contains(mp3)) {
            mQueueMP3s.add(mp3);
        }

        currentMP3 = mp3;

//        sendCommand(MainService.ACTION_POINT);
        try {
            mPlayer.point(currentMP3);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        candidateNextMP3 = pickCandidateNext(true);
        candidatePreviousMP3 = pickCandidatePrevious(true);
    }

    public void nextPlayMode() {
        switch (playMode) {
            case MODE_LIST_LOOP:
                playMode = MODE_RANDOM;
                break;
            case MODE_RANDOM:
                playMode = MODE_SINGLE_LOOP;
                break;
            case MODE_SINGLE_LOOP:
                playMode = MODE_LIST_LOOP;
                break;
            default:
                playMode = MODE_LIST_LOOP;
                break;
        }
    }

    public void seekTo(int progress) {
        try {
            mPlayer.seekTo(progress);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private MP3 pickCandidatePrevious(boolean fromUser) {

        int currentIndex = mQueueMP3s.indexOf(currentMP3);

        if (playMode == MODE_LIST_LOOP) {
            if (currentIndex > 0) {
                currentIndex -= 1;
            } else {
                currentIndex = mQueueMP3s.size() - 1;
            }
        } else if (playMode == MODE_RANDOM) {
            currentIndex = (int) (mQueueMP3s.size() * Math.random());
        } else if (playMode == MODE_SINGLE_LOOP) {
            if (fromUser) {
                if (currentIndex > 0) {
                    currentIndex -= 1;
                } else {
                    currentIndex = mQueueMP3s.size() - 1;
                }
            }
        }
        if (currentIndex < 0 || currentIndex >= mQueueMP3s.size()) {
//            Toast.makeText(mContext, "Queue is empty!", Toast.LENGTH_SHORT).show();
            return null;
        }

        return mQueueMP3s.get(currentIndex);
    }

    private MP3 pickCandidateNext(boolean fromUser) {

        int currentIndex = mQueueMP3s.indexOf(currentMP3);

        if (playMode == PlayCenter.MODE_LIST_LOOP) {
            if (currentIndex + 1 <= mQueueMP3s.size() - 1) {
                currentIndex += 1;
            } else {
                currentIndex = 0;
            }
        } else if (playMode == PlayCenter.MODE_RANDOM) {
            currentIndex = (int) (mQueueMP3s.size() * Math.random());
        } else if (playMode == PlayCenter.MODE_SINGLE_LOOP) {
            if (fromUser) {
                if (currentIndex > 0) {
                    currentIndex += 1;
                } else {
                    currentIndex = 0;
                }
            }
        }

        if (currentIndex < 0 || currentIndex >= mQueueMP3s.size()) {
//            Toast.makeText(mContext, "Queue is empty!", Toast.LENGTH_SHORT).show();
            return null;
        }

        return mQueueMP3s.get(currentIndex);
    }


    /*
     * global public data get/set methods
     */

    /**
     * 设置当前的本地列表 mp3
     *
     * @param mp3s 本地列表 mp3，一般从数据库中查出
     */
    public void setMp3s(List<MP3> mp3s) {
        this.mp3s = mp3s;
    }

    public List<MP3> getMP3s() {
        return mp3s;
    }

    public int getPlayMode() {
        return playMode;
    }

    public MP3 getCurrentMP3() {
        return currentMP3;
    }

    private MP3 getCandidateNextMP3() {
        if (candidateNextMP3 == null) {
            candidateNextMP3 = pickCandidateNext(false);
        }
        return candidateNextMP3;
    }

    private MP3 getCandidatePreviousMP3() {
        if (candidatePreviousMP3 == null) {
            candidatePreviousMP3 = pickCandidatePrevious(false);
        }
        return candidatePreviousMP3;
    }

    public Bitmap[] loadCovers() {
        Bitmap[] bitmaps;
        MP3 currentMP3 = getCurrentMP3();
        MP3 candidateNextMP3 = getCandidateNextMP3();
        MP3 candidatePreviousMP3 = getCandidatePreviousMP3();

        bitmaps = new Bitmap[3];

        bitmaps[0] = getAlbumart(candidatePreviousMP3);
        bitmaps[1] = getAlbumart(currentMP3);
        bitmaps[2] = getAlbumart(candidateNextMP3);

        return bitmaps;
    }

    /*
     * record/get independent volume settings
     */

    /**
     * 记录当前独立音量
     *
     * @param volume 独立音量，0 ～ 1
     */
    public void recordVolume(float volume) {
        mVolume = volume;
    }

    /**
     * 获取独立音量
     *
     * @return 独立音量，0 ～ 1
     */
    public float getVolume() {
        return mVolume;
    }

    /*
     * mp3s queue operations
     */

    /**
     * 缓存数据库中查询到的 queue 队列
     *
     * @return
     */
    public ArrayList<MP3> getQueueMP3s() {
        return mQueueMP3s;
    }

    /**
     * 重新设置播放队列
     *
     * @param queueMP3s 播放队列，一般从数据库中查出
     */
    public void setQueueMP3s(ArrayList<MP3> queueMP3s) {
        mQueueMP3s = queueMP3s;
    }

    /**
     * @param selectedMP3s 选中的 mp3 list_menu
     * @return 成功添加到播放队列的 mp3 条目数
     */
    public int addIntoQueue(ArrayList<MP3> selectedMP3s) {

        int success = 0;
        for (MP3 selectedMP3 : selectedMP3s) {
            if (!mQueueMP3s.contains(selectedMP3)) {
                mQueueMP3s.add(selectedMP3);
                success++;
            }
        }
        return success;
    }
}
