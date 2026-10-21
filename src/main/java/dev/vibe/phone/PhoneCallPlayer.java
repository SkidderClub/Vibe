package dev.vibe.phone;

/** A one-shot call clip. Finished means the final audio samples have drained. */
public interface PhoneCallPlayer extends AutoCloseable {
    void start(float volume);
    void setVolume(float volume);
    boolean isFinished();
    String error();
    @Override void close();
}
