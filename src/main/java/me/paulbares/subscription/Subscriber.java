package me.paulbares.subscription;

import java.util.List;

/**
 * A receiver of notifications.
 *
 * @param <T> the subscribed item type
 */
public interface Subscriber<T> {

  /**
   * Method invoked when the subscription happened successfully. The list of unread and active notifications are
   * provided to the {@link Subscriber}.
   *
   * @param notifications
   */
  void onSubscribe(List<T> notifications);

  /**
   * Method invoked when a new notification is intended to be delivered to the user this {@link Subscriber} is linked to
   * or when the status of an existing notification change.
   *
   * @param notification
   */
  void onUpdate(T notification);
}