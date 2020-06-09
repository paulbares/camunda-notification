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
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "notification")
public class Notification implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
  @SequenceGenerator(name = "sequenceGenerator")
  private Long id;

  @NotNull
  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "message")
  private String message;

  @NotNull
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @NotNull
  @Column(name = "is_active", nullable = false)
  private Boolean isActive;

  @NotNull
  @Column(name = "business_id", nullable = false, unique = true)
  private String businessId;

  /**
   * Empty constructor.
   */
  public Notification() {}

  /**
   * Constructor.
   */
  public Notification(String type, String message, Instant createdAt, Boolean isActive, String businessId) {
    this.type = type;
    this.message = message;
    this.createdAt = createdAt;
    this.isActive = isActive;
    this.businessId = businessId;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Boolean getActive() {
    return isActive;
  }

  public void setActive(Boolean active) {
    isActive = active;
  }

  public String getBusinessId() {
    return businessId;
  }

  public void setBusinessId(String businessId) {
    this.businessId = businessId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Notification that = (Notification) o;
    return Objects.equals(id, that.id) &&
            Objects.equals(type, that.type) &&
            Objects.equals(message, that.message) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(isActive, that.isActive) &&
            Objects.equals(businessId, that.businessId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, message, createdAt, isActive, businessId);
  }
}