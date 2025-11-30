package ru.wisetree.cirno;

public interface SchedulerEventListener {
    void onTrackStartOrStop();
    void onSchedulerMessage(String message, MessageType messageType);
}
