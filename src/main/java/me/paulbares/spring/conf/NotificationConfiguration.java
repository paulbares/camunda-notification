package me.paulbares.spring.conf;

import me.paulbares.service.NotificationService;
import me.paulbares.subscription.ApproverWorkflowRegistrar;
import me.paulbares.repository.NotificationRepository;
import me.paulbares.repository.RecipientRepository;
import me.paulbares.service.NotificationServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManager;

/**
 * The configuration of for the {@link NotificationService} and {@link ApproverWorkflowRegistrar}.
 */
@Configuration
public class NotificationConfiguration {

  @Autowired
  EntityManager manager;

  @Autowired
  NotificationRepository notificationRepository;

  @Autowired
  RecipientRepository recipientRepository;

  @Bean
  public NotificationServiceImpl notificationService() {
    return new NotificationServiceImpl(manager, notificationRepository, recipientRepository);
  }

  @Bean
  public ApproverWorkflowRegistrar registrar() {
    return new ApproverWorkflowRegistrar((user, groups) -> notificationService().getUnreadAndActiveNotificationsInDescOrder(user, groups));
  }
}