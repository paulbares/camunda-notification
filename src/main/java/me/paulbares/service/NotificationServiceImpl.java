package me.paulbares.service;

import me.paulbares.camunda.WorkflowNotification;
import me.paulbares.domain.Notification;
import me.paulbares.domain.Recipient;
import me.paulbares.repository.NotificationRepository;
import me.paulbares.repository.RecipientRepository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Implementation of {@link NotificationService} that manages notification from a database.
 */
public class NotificationServiceImpl implements NotificationService {

  /**
   * The Entity manager.
   */
  protected final EntityManager entityManager;

  /**
   * The repository storing the notifications.
   */
  protected final NotificationRepository notificationRepository;

  /**
   * The repository storing the recipients.
   */
  protected final RecipientRepository recipientRepository;

  /**
   * Instant supplier to get the current instant. It is for test purpose.
   */
  private final Supplier<Instant> timeSupplier;

  /**
   * Constructor.
   */
  public NotificationServiceImpl(
          EntityManager entityManager,
          NotificationRepository notificationRepository,
          RecipientRepository recipientRepository,
          Supplier<Instant> timeSupplier) {
    this.entityManager = entityManager;
    this.notificationRepository = notificationRepository;
    this.recipientRepository = recipientRepository;
    this.timeSupplier = timeSupplier;
  }

  /**
   * Creates a {@link Notification} from {@link WorkflowNotification}.
   *
   * @param workflowNotification the {@link WorkflowNotification} emitted by the workflow engine.
   * @param timeSupplier a time supplier to indicate the current time.
   * @return the created {@link Notification}.
   */
  public static Notification create(WorkflowNotification workflowNotification, Supplier<Instant> timeSupplier) {
    return new Notification(workflowNotification.getType(),
            workflowNotification.getMessage(),
            timeSupplier.get(),
            true,
            workflowNotification.getId());
  }

  @Override
  @Transactional
  public Notification saveNotificationAndRecipients(WorkflowNotification workflowNotification, Set<String> users, Set<String> groups) {
    Notification record = this.notificationRepository.save(create(workflowNotification, this.timeSupplier));

    Iterator<Recipient> userIterator = users.stream().map(u -> new Recipient(record.getId(), u, null, (byte) 0)).iterator();
    Iterator<Recipient> groupIterator = groups.stream().map(g -> new Recipient(record.getId(), null, g, (byte) 0)).iterator();
    this.recipientRepository.saveAll(() -> userIterator);
    this.recipientRepository.saveAll(() -> groupIterator);

    return record;
  }

  @Override
  @Transactional
  public void markAsRead(String userId, String notificationId) {
    markAsRead(userId, Collections.singleton(notificationId));
  }

  /**
   * {@inheritDoc}
   * <p>
   * Notice that since we are using hibernate, it seems there is no generic way to update a joined table so that the
   * update is done in two steps. See this post https://stackoverflow.com/a/1293347.
   * </p>
   */
  @Override
  @Transactional
  public void markAsRead(String userId, Collection<String> notificationIds) {
    List resultList = this.entityManager
            .createNativeQuery("SELECT r.id FROM notification n"
                    + " INNER JOIN recipient r ON n.id = r.notification_id"
                    + " WHERE r.user_id = :userId AND n.is_active = true AND r.is_read = 0 AND n.business_id IN :notificationIds")
            .setParameter("userId", userId)
            .setParameter("notificationIds", notificationIds)
            .getResultList();

    Set<Long> ids = new HashSet<>(resultList.size());
    for (Object o : resultList) {
      ids.add(((Number) o).longValue());
    }

    if (ids.size() > 0) {
      this.entityManager
              .createNativeQuery("UPDATE recipient r SET r.is_read = 1 WHERE r.id IN :ids")
              .setParameter("ids", ids)
              .executeUpdate();
    }
  }

  @Override
  @Transactional
  public Notification markAsInactive(String notificationId, Set<String> users, Set<String> groups) {
    int n = this.entityManager
            .createNativeQuery("UPDATE notification n SET n.is_active = false WHERE n.business_id = :notificationId")
            .setParameter("notificationId", notificationId)
            .executeUpdate();
    assert n == 1; // should always be 1

    Notification notification = null;
    if (n > 0) {
      List<Object[]> resultList = this.entityManager.createNativeQuery(
              "SELECT r.user_id, r.group_id, n.type, n.message, n.created_at, n.is_active, n.business_id, n.id" +
                      " FROM recipient r" +
                      " INNER JOIN notification n ON n.id = r.notification_id" +
                      " WHERE n.business_id = :businessId")
              .setParameter("businessId", notificationId)
              .getResultList();
      for (Object[] o : resultList) {
        if (o[0] != null) {
          users.add((String) o[0]);
        }
        if (o[1] != null) {
          groups.add((String) o[1]);
        }

        if (notification == null) {
          int k = 2;
          notification = new Notification((String) o[k++],
                  (String) o[k++],
                  ((Timestamp) o[k++]).toInstant(),
                  (Boolean) o[k++],
                  (String) o[k++]);
          notification.setId(((Number) o[k++]).longValue());
        }
      }
    }

    return notification;
  }

  @Override
  public List<Notification> getUnreadAndActiveNotificationsInDescOrder(String userId, Collection<String> groupIds) {
    return this.entityManager.createNativeQuery(
            "SELECT n.id, n.type, n.message, n.created_at, n.is_active, n.business_id" +
                    " FROM notification n" +
                    " INNER JOIN recipient r ON n.id = r.notification_id" +
                    " WHERE (r.user_id = :userId OR r.group_id IN :groupIds) AND n.is_active = true" +
                    " GROUP BY n.id" +
                    " HAVING max(r.is_read) = 0" +  // if the max is positive, it means the user read this notification. Skip it.
                    " ORDER BY n.created_at DESC", Notification.class)
            .setParameter("userId", userId)
            .setParameter("groupIds", groupIds)
            .getResultList();
  }
}
