package me.paulbares.camunda;

import me.paulbares.domain.Notification;
import me.paulbares.service.NotificationService;
import me.paulbares.subscription.ApproverWorkflowRegistrar;
import org.camunda.bpm.engine.delegate.DelegateTask;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.task.IdentityLink;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The {@link TaskListener} of {@link BasicApprovalWorflow} to be listened of task events.
 */
public class ApprovalWorkflowTaskListener implements TaskListener {

  /**
   * Key to retrieve the user decision when task is completed.
   */
  public static final String APPROVED_KEY = "approved";

  /**
   * Component to notify subscribers when a task is created or completed.
   */
  protected final ApproverWorkflowRegistrar registrar;

  /**
   * The {@link NotificationService} to manage the notifications.
   */
  protected final NotificationService notificationService;

  /**
   * Constructor.
   */
  public ApprovalWorkflowTaskListener(NotificationService notificationService, ApproverWorkflowRegistrar registrar) {
    this.notificationService = notificationService;
    this.registrar = registrar;
  }

  @Override
  public void notify(DelegateTask delegateTask) {
    String taskId = delegateTask.getId();
    Set<String> users;
    Set<String> groups;
    Notification notification;
    boolean isNew;
    // Only support 2 types of event for the time being.
    switch (delegateTask.getEventName()) {
      case TaskListener.EVENTNAME_CREATE:
        Set<String>[] candidates = extractCandidates(delegateTask.getCandidates());
        // type and message can be customized here. The values can be read from delegateTask.getVariable() for instance
        WorkflowNotificationImpl workflowNotification = new WorkflowNotificationImpl(taskId, "task", "message");
        notification = this.notificationService.saveNotificationAndRecipients(
                workflowNotification,
                users = candidates[0],
                groups = candidates[1]);
        isNew = true;
        break;
      case TaskListener.EVENTNAME_COMPLETE:
        notification = this.notificationService.markAsInactive(
                taskId,
                users = new HashSet<>(),
                groups = new HashSet<>());
        isNew = false;
        break;
      default:
        return; // do nothing
    }

    if (notification != null) {
      this.registrar.publish(notification, users, groups, isNew);
    }
  }

  /**
   * Extracts the list of candidates from the input collection of {@link IdentityLink}. They can be users or groups or
   * both. The result array is of size 2. The set at index 0 contains the set of users, the set at index 1 the set of
   * groups.
   *
   * @param candidates the candidates
   * @return the set of users and groups that can complete the task
   */
  public static Set<String>[] extractCandidates(Collection<IdentityLink> candidates) {
    Set[] r = new Set[]{new HashSet<>(), new HashSet<>()};
    for (IdentityLink candidate : candidates) {
      String userId = candidate.getUserId();
      if (userId != null) {
        r[0].add(userId);
      }
      String groupId = candidate.getGroupId();
      if (groupId != null) {
        r[1].add(groupId);
      }
    }
    return r;
  }
}
