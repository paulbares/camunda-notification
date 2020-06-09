package me.paulbares.service;

import me.paulbares.camunda.WorkflowNotification;
import me.paulbares.domain.Notification;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Interface for service managing {@link Notification the notifications}.
 */
public interface NotificationService {

  /**
   * Saves the notification and the given users and groups that should received this notification as well as updates
   * when its status change (when it is completed for instance).
   *
   * @param notification the notification to save.
   * @param users the users to notify
   * @param groups the groups of users to notify.
   * @return the saved notification that contains a {@link Notification#getId() unique id} whose value is higher than
   * the ones previously saved and lower than the ones of the future notifications that will be saved.
   */
  Notification saveNotificationAndRecipients(WorkflowNotification notification, Set<String> users, Set<String> groups);

  /**
   * Marks the notification with the given id as read by the given user. Once read, this notification won't be fetched
   * when calling {@link #getUnreadAndActiveNotificationsInDescOrder(String, Collection)}.
   *
   * @param userId the id of the user
   * @param notificationId the id of the notification
   */
  void markAsRead(String userId, String notificationId);

  /**
   * Marks the notifications with the given ids as read by the given user. Once read, these notifications won't be
   * fetched when calling {@link #getUnreadAndActiveNotificationsInDescOrder(String, Collection)}.
   *
   * @param userId the id of the user
   * @param notificationIds the ids of the notifications
   */
  void markAsRead(String userId, Collection<String> notificationIds);

  /**
   * Marks the notification with the given id as inactive and fills the input sets with respectively of users and groups
   * of users that need to be notified by this status change. Once inactive, a notification will not be retrieved by
   * calling {@link #getUnreadAndActiveNotificationsInDescOrder(String, Collection)}.
   *
   * @param notificationId IN - the id of the notification.
   * @param users OUT - the sets of users to notify. It should not be null.
   * @param groups OUT - the sets of groups of users to notify. It should not be null.
   * @return the {@link Notification} with the given id marked as inactive or null if none has been found with this id
   * as active.
   */
  Notification markAsInactive(String notificationId, Set<String> users, Set<String> groups);

  /**
   * Retrieves all notifications intended to the given user and given groups of users. Only the ones marked as not read
   * and still active (see {@link #markAsRead(String, String)} and {@link #markAsInactive(String, Set, Set)} will be
   * retrieved. They are given in descending order of the {@link Notification#getCreatedAt()}.
   *
   * @param userId the id of the user. It should not be null.
   * @param groupIds the ids of the groups of users. It should not be null.
   * @return the retrieved notifications
   */
  List<Notification> getUnreadAndActiveNotificationsInDescOrder(String userId, Collection<String> groupIds);
}
