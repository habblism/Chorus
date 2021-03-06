// IMyAidlInterface.aidl
package yhb.chorus;


import yhb.chorus.entity.MP3;
import yhb.chorus.ICallback;
// Declare any non-default types here with import statements

interface IPlayer {

    void playOrPause( inout MP3 mp3) ;

    void newCurrent( inout MP3 mp3) ;

    void setVolume(float volume);

    void seekTo(int progress);

    boolean isPlaying();

    int getProgress();

    void registerCallback(ICallback callback);
}
