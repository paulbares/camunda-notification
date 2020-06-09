package me.paulbares.camunda;

/**
 * This interface represents a notification emitted by the workflow engine.
 */
public interface WorkflowNotification {

  /**
   * Returns an identifier to uniquely identify a {@link WorkflowNotification}.
   *
   * @return the identifier
   */
  String getId();

  /**
   * Returns the type of the notification. It can be used to group them.
   *
   * @return the type of the notification
   */
  String getType();

  /**
   * Returns a message associated to this notification.
   *
   * @return the message
   */
  String getMessage();
}
