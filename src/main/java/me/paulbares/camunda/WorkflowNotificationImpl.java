package me.paulbares.camunda;

/**
 * POJO implementation of {@link WorkflowNotification}.
 */
public class WorkflowNotificationImpl implements WorkflowNotification {

  /**
   * An id the uniquely identified the notification.
   */
  protected final String notificationId;

  /**
   * The type of notification.
   */
  protected final String type;

  /**
   * A message associated to this notification.
   */
  protected final String message;

  /**
   * Constructor.
   */
  public WorkflowNotificationImpl(String notificationId, String type, String message) {
    this.notificationId = notificationId;
    this.type = type;
    this.message = message;
  }

  @Override
  public String getId() {
    return this.notificationId;
  }

  @Override
  public String getType() {
    return this.type;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
