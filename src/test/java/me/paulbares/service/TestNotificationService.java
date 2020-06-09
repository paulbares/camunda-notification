package me.paulbares.service;

import me.paulbares.camunda.WorkflowNotification;
import me.paulbares.camunda.WorkflowNotificationImpl;
import me.paulbares.domain.Notification;
import me.paulbares.domain.Recipient;
import me.paulbares.repository.NotificationRepository;
import me.paulbares.repository.RecipientRepository;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
public class TestNotificationService {

  @Autowired
  EntityManager manager;

  @Autowired
  NotificationRepository notificationRepository;

  @Autowired
  RecipientRepository recipientRepository;

  @Autowired
  NotificationServiceImpl service;

  @BeforeEach
  void setUp() {
    // Make sure repositories are empty before each test
    Assertions.assertEquals(0, this.notificationRepository.findAll().size());
    Assertions.assertEquals(0, this.recipientRepository.findAll().size());
  }

  @Test
  void testSave() {
    WorkflowNotification notification = new WorkflowNotificationImpl("task1", "type1", "message1");
    Notification save = this.service.saveNotificationAndRecipients(notification, Collections.singleton("user1"), Collections.singleton("group1"));

    List<Notification> notifications = this.notificationRepository.findAll();
    Assertions.assertEquals(1, notifications.size());

    List<Recipient> recipients = this.recipientRepository.findAll();
    Assertions.assertEquals(2, recipients.size());
    Assertions.assertEquals(save.getId(), recipients.get(0).getNotificationId());
    Assertions.assertEquals(save.getId(), recipients.get(1).getNotificationId());
  }

  @Test
  void testBusinessIdMustBeUnique() {
    Supplier<WorkflowNotification> supplier = () -> new WorkflowNotificationImpl("task1", "type1", "message1");
    this.service.saveNotificationAndRecipients(supplier.get(), Collections.singleton("user1"), Collections.singleton("group1"));
    assertThatThrownBy(
            () -> {
              this.service.saveNotificationAndRecipients(supplier.get(), Collections.singleton("user1"), Collections.singleton("group1"));
              this.notificationRepository.flush();
            }
    ).hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);
  }

  @Test
  void testGetUnreadNotifications() {
    WorkflowNotification notification1 = new WorkflowNotificationImpl("task1", "type1", "message1");
    WorkflowNotification notification2 = new WorkflowNotificationImpl("task2", "type1", "message2");
    WorkflowNotification inactiveNotif = new WorkflowNotificationImpl("task3", "type1", "message1");
    Notification save1 = this.service.saveNotificationAndRecipients(notification1, Collections.singleton("user1"), Collections.singleton("group1"));
    Notification save2 = this.service.saveNotificationAndRecipients(notification2, Collections.emptySet(), Collections.singleton("group3"));
    this.service.saveNotificationAndRecipients(inactiveNotif, Collections.singleton("user1"), Collections.singleton("group1"));
    this.service.markAsInactive(inactiveNotif.getId(), new HashSet<>(), new HashSet<>());

    List<Notification> notifs = this.service.getUnreadAndActiveNotificationsInDescOrder("user1", Arrays.asList("group1", "group2"));
    Assertions.assertEquals(1, notifs.size());
    Assertions.assertEquals(save1, notifs.get(0));

    notifs = this.service.getUnreadAndActiveNotificationsInDescOrder("user1", Arrays.asList("group1", "group2", "group3"));
    org.assertj.core.api.Assertions.assertThat(notifs).containsExactlyInAnyOrder(save1, save2);

    // user2 does not exist in the db.
    notifs = this.service.getUnreadAndActiveNotificationsInDescOrder("user2", Arrays.asList("group1", "group2", "group3"));
    org.assertj.core.api.Assertions.assertThat(notifs).containsExactlyInAnyOrder(save1, save2);
  }

  @Test
  void testOrderNotifications() {
    List<Notification> saved = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      WorkflowNotification n = new WorkflowNotificationImpl("task" + i, "type1", "message1");
      saved.add(this.service.saveNotificationAndRecipients(n, Collections.singleton("user1"), Collections.emptySet()));
    }

    Collections.sort(saved, Comparator.comparing(Notification::getCreatedAt).reversed());

    List<Notification> notifs = this.service.getUnreadAndActiveNotificationsInDescOrder("user1", Collections.emptyList());
    org.assertj.core.api.Assertions.assertThat(notifs).containsExactly(saved.toArray(new Notification[0]));
  }

  @Test
  void testMarkAsInactive() {
    WorkflowNotification notification = new WorkflowNotificationImpl("task1", "type1", "message1");
    WorkflowNotification other = new WorkflowNotificationImpl("task2", "type1", "message1");
    Notification save = this.service.saveNotificationAndRecipients(notification, Collections.singleton("user1"), Collections.singleton("group1"));
    this.service.saveNotificationAndRecipients(other, Collections.singleton("user1"), Collections.singleton("group1"));
    Set<String> users = new HashSet<>();
    Set<String> groups = new HashSet<>();
    Notification inactive = this.service.markAsInactive("task1", users, groups);

    org.assertj.core.api.Assertions.assertThat(users).containsExactlyInAnyOrder("user1");
    org.assertj.core.api.Assertions.assertThat(groups).containsExactlyInAnyOrder("group1");
    save.setActive(false);
    Assertions.assertEquals(save, inactive);
  }

  @Test
  void testMarkAsRead() {
    Instant now = Instant.now();
    List<Notification> notifications = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      WorkflowNotification notification = new WorkflowNotificationImpl("task" + i, "type", "message");
      notifications.add(this.service.saveNotificationAndRecipients(notification, Collections.singleton("user1"), Collections.singleton("group1")));
    }

    List<Notification> unreadNotificationsInDescOrder = this.service.getUnreadAndActiveNotificationsInDescOrder("user1", Collections.singleton("group1"));
    org.assertj.core.api.Assertions.assertThat(unreadNotificationsInDescOrder).containsExactlyInAnyOrder(notifications.toArray(new Notification[0]));

    Notification notification2 = notifications.get(2);
    Notification notification4 = notifications.get(4);
    this.service.markAsRead("user1", notification2.getBusinessId());
    this.service.markAsRead("user1", notification4.getBusinessId());

    unreadNotificationsInDescOrder = this.service.getUnreadAndActiveNotificationsInDescOrder("user1", Collections.singleton("group1"));
    notifications.remove(notification2);
    notifications.remove(notification4);
    org.assertj.core.api.Assertions.assertThat(unreadNotificationsInDescOrder).containsExactlyInAnyOrder(notifications.toArray(new Notification[0]));

    Set<String> toRemove = new HashSet<>();
    for (int i = 0; i < notifications.size(); i++) {
      if (i % 2 == 0) {
        toRemove.add(notifications.get(i).getBusinessId());
      }
    }
    this.service.markAsRead("user1", toRemove);

    notifications.removeIf(n -> toRemove.contains(n.getBusinessId()));
    unreadNotificationsInDescOrder = this.service.getUnreadAndActiveNotificationsInDescOrder("user1", Collections.singleton("group1"));
    org.assertj.core.api.Assertions.assertThat(unreadNotificationsInDescOrder).containsExactlyInAnyOrder(notifications.toArray(new Notification[0]));
  }
}
