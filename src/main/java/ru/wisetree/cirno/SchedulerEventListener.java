package ru.wisetree.cirno;

public interface SchedulerEventListener {
    void onTrackStartOrStop();
    void onSchedulerMessage(String message, MessageType messageType);

    /**
     * Optional: receive messages with user attribution.
     * Default implementation delegates to onSchedulerMessage with null user.
     */
    default void onSchedulerMessage(String message, MessageType messageType, String userName) {
        onSchedulerMessage(message, messageType);
    }
}
