package me.paulbares.subscription;

import me.paulbares.domain.Notification;
import me.paulbares.user.CamundaUserDetails;

import java.util.function.Consumer;

/**
 * A subscription linked a {@link Subscriber} to a {@link CamundaUserDetails}.
 */
public class Subscription {

  /**
   * The {@link CamundaUserDetails} this subscription is bound to.
   */
  private final CamundaUserDetails userDetails;

  /**
   * The {@link Subscriber} this subscription is bound to.
   */
  private final Subscriber<Notification> subscriber;

  /**
   * The action to execute to unsubscribe the associated subscriber.
   */
  private final Consumer<Subscription> unsubscriber;

  /**
   * Constructor.
   */
  public Subscription(CamundaUserDetails userDetails, Subscriber<Notification> subscriber, Consumer<Subscription> unsubscriber) {
    this.userDetails = userDetails;
    this.subscriber = subscriber;
    this.unsubscriber = unsubscriber;
  }

  /**
   * Returns the {@link CamundaUserDetails} this subscription is bound to.
   *
   * @return the {@link CamundaUserDetails} this subscription is bound to.
   */
  public CamundaUserDetails getCamundaUserDetails() {
    return this.userDetails;
  }

  /**
   * Returns the {@link Subscriber} this subscription is bound to.
   *
   * @return the {@link Subscriber} this subscription is bound to.
   */
  public Subscriber<Notification> getSubscriber() {
    return this.subscriber;
  }

  /**
   * Method to invoke to unregister the subscriber. After invocation, the subscriber won't receive any new
   * notification.
   */
  public void unsubscribe() {
    this.unsubscriber.accept(this);
  }
}
