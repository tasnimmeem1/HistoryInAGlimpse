package org.example.historyinaglimpse;

/** Common lifecycle for cloud MP3 and bundled WAV ambience. */
interface AmbientAudio extends AutoCloseable {
    void play();
    void pause();
    void setVolume(double value);
    @Override void close();
}
