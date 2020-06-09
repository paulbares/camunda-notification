package me.paulbares.subscription;

import com.google.common.util.concurrent.Striped;
import me.paulbares.domain.Notification;
import me.paulbares.user.CamundaUserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Manages subscriptions to notification for several users. This implementation has the following guarantees:
 * <ul>
 *   <li>Only unread and active notifications are provided to subscriber during the subscription process</li>
 *   <li>A notification is sent only once.</li>
 *   <li>Notifications are delivered in correct order i.e in the order they are produced by the workflows instances.</li>
 *   <li>No notification is lost in particular if the subscription of a new {@link Subscriber} happens when a new
 *   notification or an update of a notification occurs at the same time</li>
 * </ul>
 *
 * <p>
 *   <b>IMPORTANT:</b> the implementation relies on the fact that notification ids are generated using an auto increment
 *   sequence generator.
 * </p>
 */
public class ApproverWorkflowRegistrar {

  /**
   * A notification provider that takes as argument a user id and a list of group ids and returns the list of
   * unread and active notifications in descending order, ordered by {@link Notification#getCreatedAt()}.
   */
  protected final BiFunction<String, Collection<String>, List<Notification>> notificationProvider;

  /**
   * Higher {@link Notification#getId()} sent during the subscription of a new {@link Subscriber}.
   */
  protected final Map<Subscriber<?>, Long> lastSubIdBySub;

  /**
   * The set of subscribers for each user.
   */
  protected final Entry subscribersByUserId;

  /**
   * The sets of {@link Subscriber} for each group.
   */
  protected final Map<String, Entry> subscribersByGroupId;

  /**
   * A striped lock to allow operations for different users to be done in parallel without using the same lock.
   */
  protected final Striped<ReadWriteLock> sync;

  /**
   * Constructor.
   */
  public ApproverWorkflowRegistrar(BiFunction<String, Collection<String>, List<Notification>> notificationProvider) {
    this.notificationProvider = notificationProvider;
    this.sync = Striped.lazyWeakReadWriteLock(Runtime.getRuntime().availableProcessors() * 4);
    this.subscribersByUserId = new Entry();
    this.subscribersByGroupId = new HashMap<>();
    this.lastSubIdBySub = new ConcurrentHashMap<>();
  }

  /**
   * Sends the notification to the given users and users belonging to the given groups. The notification will be send
   * only once to each {@link Subscriber}.
   *
   * @param users the users
   * @param groups the groups
   * @param notification the notification to send
   * @param isNew true if the notification is a new one i.e corresponds to a newly created task, false otherwise (task
   * completed)
   */
  public void publish(Notification notification, Set<String> users, Set<String> groups, boolean isNew) {
    var subscribersByUser = collectSubscribers(users, groups);
    if (!subscribersByUser.isEmpty()) {
      // To keep track of Subscriber for which the notification has been sent.
      // It guarantees the notification will be deliver only once.
      var notified = Collections.newSetFromMap(new ConcurrentHashMap<>());

      List<SendNotificationTask> tasks = new ArrayList<>();
      for (Map.Entry<String, Set<Subscriber<Notification>>> subscribers : subscribersByUser.entrySet()) {
        tasks.add(new SendNotificationTask(subscribers.getKey(),
                notification,
                subscribers.getValue(),
                // Order in the condition does matter here !
                // Only check the id when the notification is new i.e has just been created. If not new, it means
                // the notification has already been sent either during the subscription or via #onCreate()
                s -> (!isNew || notification.getId() > this.lastSubIdBySub.getOrDefault(s, -1L)) && notified.add(s)));
      }

      // Will be done in the common pool. Publish is done in a synchronous way for the time being.
      ForkJoinTask.invokeAll(tasks);
    }
  }

  /**
   * Collects all {@link Subscriber} associated to the given set of users and groups of users indexed by user id.
   *
   * @param users the sets of users
   * @param groups the sets of groups of users
   * @return the {@link Subscriber subscribers} by user id.
   */
  protected Map<String, Set<Subscriber<Notification>>> collectSubscribers(Set<String> users, Set<String> groups) {
    Map<String, Set<Subscriber<Notification>>> result = new HashMap<>(); // collect Subscriber per user
    users.forEach(u -> {
      Set<Subscriber<Notification>> subscribers = this.subscribersByUserId.getSubscribers(u);
      if (subscribers != null) {
        result.put(u, subscribers);
      }
    });
    groups.forEach(g -> {
      Entry entry = this.subscribersByGroupId.get(g);
      if (entry != null) {
        for (Map.Entry<String, Set<Subscriber<Notification>>> e : entry.subscriberByUserId.entrySet()) {
          result.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
        }
      }
    });
    return result;
  }

  /**
   * Registers a new {@link Subscriber} for the given userDetails. It will receive all notifications intended to this
   * user and the groups he belongs to. After the subscription, the subscriber will receive all notifications.
   *
   * @param userDetails the user details
   * @param subscriber the subscriber to register
   * @return the {@link Subscription} to be used to stop receiving notifications by {@link Subscription#unsubscribe()
   *         unsubscribing}
   */
  public Subscription subscribe(CamundaUserDetails userDetails, Subscriber<Notification> subscriber) {
    String userId = userDetails.getUser();
    Collection<String> groupIds = userDetails.getGroups();
    writeExecute(userId, () -> {

      /*
       * Add the subscriber first before fetching the existing notifications to make sure if a new notification
       * arrives in parallel the subscriber does not miss the new notification.
       *
       *    Thread 1                                               | Thread 2
       * -  subscribe and add subscriber                           | new notification is created, onCreate is called but
       *                                                             the notification is not saved yet
       * -  getUnreadAndActiveNotificationsInDescOrder() is called | ø
       *    the new notification is not retrieved                  |
       * -  ø                                                      | new notification saved, it will be published to
       *                                                           | subscribers
       * -  ø                                                      | collect the subscribers to notify. The new
       *                                                           | subscriber is in the list.
       * -  send fetched notifications                             | send new notification
       *
       * The subscriber won't miss any notification, he might receive them in the wrong order but can reorder them
       * thanks to the notification timestamp.
       */

      this.subscribersByUserId.addSubscriber(userId, subscriber);
      groupIds
              .forEach(group -> this.subscribersByGroupId.computeIfAbsent(group, __ -> new Entry())
                      .addSubscriber(userId, subscriber));

      // Once registered, send initial view to subscriber
      List<Notification> notifications = this.notificationProvider.apply(userId, groupIds);
      Optional<Notification> max = notifications.stream().max(Comparator.comparingLong(Notification::getId));
      Long previous = this.lastSubIdBySub.put(subscriber, max.isPresent() ? max.get().getId() : -1);
      if (previous != null) {
        throw new IllegalStateException("A subscriber cannot be use multiple times");
      }

      if (!notifications.isEmpty()) {
        subscriber.onSubscribe(notifications);
      }
    });
    return new Subscription(userDetails, subscriber, this::unsubscribe);
  }

  /**
   * Unregistered the subscriber associated to this subscription.
   *
   * @param subscription the subscription
   */
  private void unsubscribe(Subscription subscription) {
    CamundaUserDetails userDetails = subscription.getCamundaUserDetails();
    Subscriber<Notification> subscriber = subscription.getSubscriber();
    String userId = userDetails.getUser();
    writeExecute(userId, () -> {
      this.lastSubIdBySub.remove(subscriber);
      this.subscribersByUserId.removeSubscriber(userId, subscriber);
      userDetails.getGroups().forEach(groupId -> {
        Entry entry = this.subscribersByGroupId.get(groupId);
        if (entry != null) {
          entry.removeSubscriber(userId, subscriber);
          if (!entry.hasSubscriber()) {
            this.subscribersByGroupId.remove(groupId);
          }
        }
      });
    });
  }

  /**
   * Executes the given action within the write lock given by {@code this.sync.get(key)}.
   *
   * @param key the key used to retrieve the write lock in {@link #sync}
   * @param task the action to execute
   */
  protected void writeExecute(String key, Runnable task) {
    Lock lock = this.sync.get(key).writeLock();
    lock.lock();
    try {
      task.run();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Executes the given action within the read lock given by {@code this.sync.get(key)}.
   *
   * @param key the key used to retrieve the read lock in {@link #sync}
   * @param task the action to execute
   */
  protected void readExecute(String key, Runnable task) {
    Lock lock = this.sync.get(key).readLock();
    lock.lock();
    try {
      task.run();
    } finally {
      lock.unlock();
    }
  }

  /**
   * {@link ForkJoinTask} to send the notification to a set of {@link Subscriber} for a particular user.
   */
  class SendNotificationTask extends RecursiveAction {

    /**
     * The id of the user.
     */
    protected final String userId;

    /**
     * The set of {@link Subscriber} to which the notification will be delivered.
     */
    protected final Set<Subscriber<Notification>> subscribers;

    /**
     * The notification to send.
     */
    protected final Notification notification;

    /**
     * A predicate to filter out {@link Subscriber the subsctiber} from {@link #subscribers} to which the notification
     * is delivered.
     */
    protected final Predicate<Subscriber<Notification>> predicate;

    /**
     * Constructor.
     */
    public SendNotificationTask(String userId,
                                Notification notification,
                                Set<Subscriber<Notification>> subscribers,
                                Predicate<Subscriber<Notification>> predicate) {
      this.subscribers = subscribers;
      this.notification = notification;
      this.userId = userId;
      this.predicate = predicate;
    }

    @Override
    protected void compute() {
      readExecute(this.userId,
              () -> this.subscribers.stream()
                      // Creates a composed predicate to make sure that within the lock, the subscriber is still there
                      // and has not been unsubscribed
                      .filter(this.predicate.and(lastSubIdBySub::containsKey))
                      .forEach(s -> s.onUpdate(this.notification)));
    }
  }

  /**
   * Objects to manage a set of {@link Subscriber} for each user
   */
  static class Entry {

    /**
     * Set of subscriber for each user indexed by user id.
     */
    final Map<String, Set<Subscriber<Notification>>> subscriberByUserId = new ConcurrentHashMap<>();

    /**
     * Adds the {@link Subscriber} to the set of subscriber associated to the given user.
     *
     * @param userId the id of the user
     * @param subscriber the {@link Subscriber} to add
     */
    void addSubscriber(String userId, Subscriber<Notification> subscriber) {
      this.subscriberByUserId.computeIfAbsent(userId, __ -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
              .add(subscriber);
    }

    /**
     * Removes the {@link Subscriber} to the set of subscriber associated to the given user.
     *
     * @param userId the id of the user
     * @param subscriber the {@link Subscriber} to remove
     */
    void removeSubscriber(String userId, Subscriber<Notification> subscriber) {
      Set<Subscriber<Notification>> subscribers = this.subscriberByUserId.get(userId);
      if (subscribers != null) {
        Iterator<Subscriber<Notification>> iterator = subscribers.iterator();
        while (iterator.hasNext()) {
          if (iterator.next().equals(subscriber)) {
            iterator.remove();
            if (subscribers.isEmpty()) {
              this.subscriberByUserId.remove(userId);
            }
            break; // stop here
          }
        }
      }
    }

    /**
     * Gets all {@link Subscriber} associated to the given user.
     *
     * @param userId the id of the user
     * @return {@link Subscriber subscribers} associated to the given user.
     */
    Set<Subscriber<Notification>> getSubscribers(String userId) {
      return this.subscriberByUserId.get(userId);
    }

    /**
     * Returns true {@link #subscriberByUserId} is not empty, false otherwise.
     *
     * @return true {@link #subscriberByUserId} is not empty, false otherwise
     */
    boolean hasSubscriber() {
      return !this.subscriberByUserId.isEmpty();
    }
  }
}
