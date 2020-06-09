package me.paulbares;

import me.paulbares.camunda.ApprovalWorkflowTaskListener;
import me.paulbares.domain.Notification;
import me.paulbares.subscription.Subscriber;
import me.paulbares.user.CamundaUserDetails;
import me.paulbares.user.CamundaUserDetailsImpl;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Utility class for tests.
 */
public class Utils {

  public static final CamundaUserDetails user1 = new CamundaUserDetailsImpl("user1", Collections.singletonList("group1"));
  public static final CamundaUserDetails user2 = new CamundaUserDetailsImpl("user2", Arrays.asList("group1", "group2"));
  public static final CamundaUserDetails user4 = new CamundaUserDetailsImpl("user4");

  public static Task getCurrentTask(ProcessEngine processEngine, ProcessInstance instance) {
    return processEngine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
  }

  public static Set<String>[] extractCandidates(ProcessEngine processEngine, Task task) {
    return ApprovalWorkflowTaskListener
            .extractCandidates(processEngine.getTaskService().getIdentityLinksForTask(task.getId()));
  }

  public static class AccumulatorSubscriber implements Subscriber<Notification> {

    public final List<String> initialIds = new ArrayList<>();
    public final List<Notification> initialNotifications = new ArrayList<>();
    public final List<String> updateIds = new ArrayList<>();
    public final List<Notification> updateNotifications = new ArrayList<>();

    @Override
    public synchronized void onSubscribe(List<Notification> notifications) {
      notifications.forEach(n -> {
        initialIds.add(n.getBusinessId());
        initialNotifications.add(n);
      });
    }

    @Override
    public synchronized void onUpdate(Notification n) {
      updateIds.add(n.getBusinessId());
      updateNotifications.add(n);
    }

    public synchronized void clear() {
      this.initialIds.clear();
      this.initialNotifications.clear();
      this.updateIds.clear();
      this.updateNotifications.clear();
    }
  }

}
