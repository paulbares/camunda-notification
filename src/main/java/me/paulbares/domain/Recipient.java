package me.paulbares.domain;

import com.sun.istack.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "recipient")
public class Recipient implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
  @SequenceGenerator(name = "sequenceGenerator")
  private Long id;

  @NotNull
  @Column(name = "notification_id", nullable = false)
  private Long notificationId;

  @NotNull
  @Column(name = "user_id")
  private String userId;

  @NotNull
  @Column(name = "group_id")
  private String groupId;

  @NotNull
  @Column(name = "is_read", nullable = false)
  private byte isRead;

  /**
   * Empty constructor.
   */
  public Recipient() {}

  /**
   * Constructor.
   */
  public Recipient(long notificationId, String userId, String groupId, byte isRead) {
    this.notificationId = notificationId;
    this.userId = userId;
    this.groupId = groupId;
    this.isRead = isRead;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getNotificationId() {
    return notificationId;
  }

  public String getUserId() {
    return userId;
  }

  public String getGroupId() {
    return groupId;
  }

  public byte isRead() {
    return isRead;
  }

  public void setNotificationId(Long notificationId) {
    this.notificationId = notificationId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public void setRead(byte read) {
    isRead = read;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Recipient recipient = (Recipient) o;
    return isRead == recipient.isRead &&
            Objects.equals(id, recipient.id) &&
            Objects.equals(notificationId, recipient.notificationId) &&
            Objects.equals(userId, recipient.userId) &&
            Objects.equals(groupId, recipient.groupId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, notificationId, userId, groupId, isRead);
  }
}