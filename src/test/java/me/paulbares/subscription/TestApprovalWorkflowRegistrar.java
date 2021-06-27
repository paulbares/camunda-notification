package me.paulbares.subscription;

import me.paulbares.Utils.AccumulatorSubscriber;
import me.paulbares.camunda.WorkflowNotification;
import me.paulbares.camunda.WorkflowNotificationImpl;
import me.paulbares.domain.Notification;
import me.paulbares.service.NotificationService;
import me.paulbares.service.NotificationServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static me.paulbares.Utils.user1;
import static me.paulbares.Utils.user2;
import static org.assertj.core.api.Assertions.assertThat;

public class TestApprovalWorkflowRegistrar {

  static final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

  InMemoryNotificationService service;
  ApproverWorkflowRegistrar registrar;

  @AfterAll
  static void tearDown() throws InterruptedException {
    executorService.shutdown();
    executorService.awaitTermination(1, TimeUnit.SECONDS);
  }

  @BeforeEach
  void setup() {
    this.service = new InMemoryNotificationService();
    this.registrar = new ApproverWorkflowRegistrar((u, g) -> this.service.getUnreadAndActiveNotificationsInDescOrder(u, g));
  }

  @Test
  void testNotificationWithOnlyOneSubscriber() {
    AccumulatorSubscriber subscriber = new AccumulatorSubscriber();

    int id = 3;
    final int startId = id;
    IntStream.range(0, id).forEach(i -> createAndPublish(i, Collections.singleton(user1.getUser()),
            Collections.emptySet()));
    Subscription sub = this.registrar.subscribe(user1, subscriber);

    assertThat(subscriber.initialIds)
            .containsExactlyElementsOf(() -> IntStream.range(0, startId).mapToObj(Integer::valueOf).sorted(Comparator.comparing(Integer::intValue).reversed()).map(String::valueOf).iterator());
    subscriber.clear();

    // Complete the first task
    completeAndPublish(0);
    assertThat(subscriber.initialIds).isEmpty();
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(0));
    subscriber.clear();

    id++;
    createAndPublish(id, Collections.singleton(user1.getUser()), Collections.emptySet());
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(id));
    subscriber.clear();

    completeAndPublish(id);
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(id));
    subscriber.clear();

    // Create a notification whose recipient is a group user1 belongs to
    id++;
    createAndPublish(id, Collections.emptySet(), Collections.singleton("group1"));
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(id));
    subscriber.clear();

    // Create a notification whose recipient is user1 AND a group user1 belongs to. Only 1 notification should be sent
    id++;
    createAndPublish(id, Collections.singleton(user1.getUser()), new HashSet<>(Arrays.asList("group1", "group2")));
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(id));
    subscriber.clear();

    // A notification that does not concern user2
    id++;
    createAndPublish(id, Collections.singleton(user2.getUser()), Collections.emptySet());
    assertThat(subscriber.updateIds).isEmpty();

    // Idem
    completeAndPublish(id);
    assertThat(subscriber.updateIds).isEmpty();

    sub.unsubscribe();
    Assertions.assertTrue(this.registrar.subscribersByGroupId.isEmpty());
    Assertions.assertFalse(this.registrar.subscribersByUserId.hasSubscriber());
  }

  @Test
  void testCheckPublishHappensOnlyOnce() {
    AccumulatorSubscriber subscriber = new AccumulatorSubscriber();

    int id = 3;
    final int startId = id;
    IntStream.range(0, id).forEach(i -> createAndPublish(i, Collections.singleton(user2.getUser()), Collections.emptySet()));
    Subscription sub = this.registrar.subscribe(user2, subscriber);

    assertThat(subscriber.initialIds)
            .containsExactlyElementsOf(() -> IntStream.range(0, startId).mapToObj(Integer::valueOf).sorted(Comparator.comparing(Integer::intValue).reversed()).map(String::valueOf).iterator());

    // Create a new notif that concern user2 and all the groups he belongs to. Only 1 notification should be sent.
    id++;
    createAndPublish(id, Collections.singleton(user2.getUser()), new HashSet<>(Arrays.asList("group1", "group2")));
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(id));
    subscriber.clear();

    // Idem for complete
    completeAndPublish(id);
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(id));
    subscriber.clear();

    sub.unsubscribe();
    Assertions.assertTrue(this.registrar.subscribersByGroupId.isEmpty());
    Assertions.assertFalse(this.registrar.subscribersByUserId.hasSubscriber());
  }

  @Test
  void testSeveralSubscribersSameUser() {
    AccumulatorSubscriber subscriber1 = new AccumulatorSubscriber();
    AccumulatorSubscriber subscriber2 = new AccumulatorSubscriber();

    int id = 3;
    final int startId = id;
    IntStream.range(0, id).forEach(i -> createAndPublish(i, Collections.singleton(user1.getUser()), Collections.emptySet()));
    Subscription sub1 = this.registrar.subscribe(user1, subscriber1);
    Subscription sub2 = this.registrar.subscribe(user1, subscriber2);

    assertThat(subscriber1.initialIds)
            .containsExactlyElementsOf(() -> IntStream.range(0, startId).mapToObj(Integer::valueOf).sorted(Comparator.comparing(Integer::intValue).reversed()).map(String::valueOf).iterator());
    assertThat(subscriber2.initialIds)
            .containsExactlyElementsOf(() -> IntStream.range(0, startId).mapToObj(Integer::valueOf).sorted(Comparator.comparing(Integer::intValue).reversed()).map(String::valueOf).iterator());

    id++;
    createAndPublish(id, Collections.singleton(user1.getUser()), Collections.emptySet());
    assertThat(subscriber1.updateIds).containsExactly(String.valueOf(id));
    subscriber1.clear();
    subscriber2.clear();

    completeAndPublish(id);
    assertThat(subscriber2.updateIds).containsExactly(String.valueOf(id));

    sub1.unsubscribe();
    sub2.unsubscribe();
    Assertions.assertTrue(this.registrar.subscribersByGroupId.isEmpty());
    Assertions.assertFalse(this.registrar.subscribersByUserId.hasSubscriber());
  }

  @Test
  void testSeveralSubscribersSameGroup() {
    AccumulatorSubscriber subscriber1 = new AccumulatorSubscriber();
    AccumulatorSubscriber subscriber2 = new AccumulatorSubscriber();

    Subscription sub1 = this.registrar.subscribe(user1, subscriber1);
    Subscription sub2 = this.registrar.subscribe(user2, subscriber2);

    // This should be received by user1 and user2

    int id = 0;
    createAndPublish(id, Collections.emptySet(), Collections.singleton("group1"));
    assertThat(subscriber1.updateIds).containsExactly(String.valueOf(id));
    assertThat(subscriber2.updateIds).containsExactly(String.valueOf(id));
    subscriber1.clear();
    subscriber2.clear();

    id++;
    // This should be received by user2 only
    createAndPublish(id, Collections.emptySet(), Collections.singleton("group2"));
    assertThat(subscriber1.updateIds).isEmpty();
    assertThat(subscriber2.updateIds).containsExactly(String.valueOf(id));

    sub1.unsubscribe();
    sub2.unsubscribe();
    Assertions.assertTrue(this.registrar.subscribersByGroupId.isEmpty());
    Assertions.assertFalse(this.registrar.subscribersByUserId.hasSubscriber());
  }

  @Test
  void testUnsubscribe() {
    AccumulatorSubscriber subscriber = new AccumulatorSubscriber();
    Subscription sub = this.registrar.subscribe(user1, subscriber);

    createAndPublish(0, Collections.singleton(user1.getUser()), Collections.emptySet());
    assertThat(subscriber.updateIds).containsExactly(String.valueOf(0));
    subscriber.clear();

    sub.unsubscribe();
    Assertions.assertTrue(this.registrar.subscribersByGroupId.isEmpty());
    Assertions.assertFalse(this.registrar.subscribersByUserId.hasSubscriber());

    completeAndPublish(0);
    assertThat(subscriber.updateIds).isEmpty();
    assertThat(subscriber.initialIds).isEmpty();
  }

  @Test
  void testSubscribeWhilePublishUserAndGroups() throws ExecutionException, InterruptedException {
    AccumulatorSubscriber subscriber = new AccumulatorSubscriber();

    int nThreads = Runtime.getRuntime().availableProcessors();
    for (int i = 0; i < 100_000; i++) {
      List<Future<?>> futures = new ArrayList<>();
      Random random = new Random();
      int randomInt = random.nextInt(nThreads);
      AtomicReference<Subscription> ref = new AtomicReference<>();
      for (int j = 0; j < nThreads; j++) {
        if (j == randomInt) {
          futures.add(executorService.submit(() -> {
            Subscription subscribe = this.registrar.subscribe(user2, subscriber);
            ref.set(subscribe);
            return subscribe;
          }));
        }

        int id = j;
        if (j % 3 == 0) {
          futures.add(executorService.submit(() -> createAndPublish(id, Collections.singleton(user2.getUser()), Collections.emptySet())));
        } else if (j % 3 == 1) {
          futures.add(executorService.submit(() -> createAndPublish(id, Collections.singleton(user2.getUser()), Collections.singleton("group1"))));
        } else {
          futures.add(executorService.submit(() -> createAndPublish(id, Collections.singleton(user2.getUser()), Collections.singleton("group2"))));
        }
      }

      for (Future<?> future : futures) {
        future.get();
      }

      // depending on when the subscription happen, the subscriber will receive the notifications via #onSubscribe or
      // via #onUpdate. In any case, it should receive everything and only ONCE !

      // Collect the ids.
      List<String> merged = new ArrayList<>(subscriber.initialIds);
      merged.addAll(subscriber.updateIds);
      List<Integer> ids = merged.stream().map(Integer::valueOf).collect(Collectors.toList());
      assertThat(ids)
              .containsExactlyInAnyOrderElementsOf(() -> IntStream.range(0, nThreads).iterator());

      subscriber.clear();
      ref.get().unsubscribe();
      this.service.clear();
    }
  }

  @Test
  void testSubscriberCannotBeReused() {
    AccumulatorSubscriber subscriber = new AccumulatorSubscriber();

    this.registrar.subscribe(user1, subscriber);
    Assertions.assertThrows(IllegalStateException.class, () -> this.registrar.subscribe(user2, subscriber));
  }

  protected WorkflowNotification createNotification(int id) {
    return new WorkflowNotificationImpl(Long.toString(id), "type", "message");
  }

  protected void createAndPublish(int id, Set<String> users, Set<String> groups) {
    Notification n = this.service.saveNotificationAndRecipients(createNotification(id), users, groups);
    this.registrar.publish(n, users, groups, true);
  }


  protected void completeAndPublish(int id) {
    Set<String> users = new HashSet<>();
    Set<String> groups = new HashSet<>();
    Notification notification = this.service.markAsInactive(String.valueOf(id), users, groups);
    if (notification != null) {
      this.registrar.publish(notification, users, groups, false);
    }
  }

  /**
   * An in memory implementation of {@link NotificationServiceImpl} for unit tests.
   */
  protected static class InMemoryNotificationService implements NotificationService {

    final Map<String, Set<Notification>> notifByUser = new HashMap<>();
    final Map<String, Set<Notification>> notifByGroup = new HashMap<>();

    final LongSupplier idGenerator = new AtomicLong()::getAndIncrement;

    Instant now = Instant.now();

    @Override
    public synchronized Notification saveNotificationAndRecipients(WorkflowNotification notification, Set<String> users, Set<String> groups) {
      Notification n = NotificationServiceImpl.create(notification, () -> this.now);
      // Change now each time the method is called to be sure two notifications do not have the same #createAt value
      // which can cause unpredictability in tests.
      this.now = this.now.plus(Duration.ofSeconds(1)); // within sync block so safe.
      for (String user : users) {
        notifByUser.computeIfAbsent(user, k -> new HashSet<>()).add(n);
      }
      for (String group : groups) {
        notifByGroup.computeIfAbsent(group, k -> new HashSet<>()).add(n);
      }
      n.setId(idGenerator.getAsLong());
      return n;
    }

    @Override
    public synchronized List<Notification> getUnreadAndActiveNotificationsInDescOrder(String userId, Collection<String> groupIds) {
      Map<String, Notification> result = new HashMap<>();
      {
        Set<Notification> notifications = this.notifByUser.get(userId);
        if (notifications != null) {
          notifications.stream().forEach(n -> result.put(n.getBusinessId(), n));
        }
      }

      for (String groupId : groupIds) {
        Set<Notification> notifications = this.notifByGroup.get(groupId);
        if (notifications != null) {
          notifications.stream().forEach(n -> result.put(n.getBusinessId(), n));
        }
      }

      return result.values()
              .stream()
              .filter(n -> n.getActive())
              .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
              .collect(Collectors.toList());
    }

    @Override
    public synchronized Notification markAsInactive(String notificationId, Set<String> users, Set<String> groups) {
      Notification notification = null;
      for (Map.Entry<String, Set<Notification>> entry : this.notifByUser.entrySet()) {
        for (Notification n : entry.getValue()) {
          if (n.getBusinessId().equals(notificationId)) {
            users.add(entry.getKey());
            notification = n;
          }
        }
      }
      for (Map.Entry<String, Set<Notification>> entry : this.notifByGroup.entrySet()) {
        for (Notification n : entry.getValue()) {
          if (n.getBusinessId().equals(notificationId)) {
            groups.add(entry.getKey());
            notification = n;
          }
        }
      }
      notification.setActive(false);
      return notification;
    }

    @Override
    public void markAsRead(String userId, String notificationId) {
      throw new RuntimeException("not implemented");
    }

    @Override
    public void markAsRead(String userId, Collection<String> notificationIds) {
      throw new RuntimeException("not implemented");
    }

    public synchronized void clear() {
      this.notifByGroup.clear();
      this.notifByUser.clear();
    }
  }
}
