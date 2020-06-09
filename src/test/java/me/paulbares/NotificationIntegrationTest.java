package me.paulbares;

import me.paulbares.Utils.AccumulatorSubscriber;
import me.paulbares.bpmn.TestBasicApprovalWorkflow;
import me.paulbares.camunda.ApprovalWorkflowTaskListener;
import me.paulbares.camunda.BasicApprovalWorflow;
import me.paulbares.domain.Notification;
import me.paulbares.subscription.ApproverWorkflowRegistrar;
import me.paulbares.subscription.Subscription;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static me.paulbares.Utils.user1;
import static me.paulbares.Utils.user4;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = {
        NotificationIntegrationTest.Conf.class
})
public class NotificationIntegrationTest {

  @Autowired
  protected ProcessEngine processEngine;

  @Autowired
  protected ApproverWorkflowRegistrar registrar;

  @Configuration
  protected static class Conf {

    @Autowired
    protected ProcessEngine processEngine;

    @Bean
    void deployIfNecessary() {
      TestBasicApprovalWorkflow.deployIfNecessary(processEngine);
    }
  }

  @Test
  void test() {
    ProcessInstance instance1 = processEngine.getRuntimeService()
            .createProcessInstanceByKey(BasicApprovalWorflow.NAME)
            .execute();

    // Start a second process whose first task is going to be rejected.
    ProcessInstance instance2 = processEngine.getRuntimeService()
            .createProcessInstanceByKey(BasicApprovalWorflow.NAME)
            .execute();

    AccumulatorSubscriber subscriber1 = new AccumulatorSubscriber();
    AccumulatorSubscriber subscriber4 = new AccumulatorSubscriber();
    Subscription subscribe1 = this.registrar.subscribe(user1, subscriber1);
    Subscription subscribe4 = this.registrar.subscribe(user4, subscriber4);

    Task currentTask1 = Utils.getCurrentTask(processEngine, instance1);
    Task currentTask2 = Utils.getCurrentTask(processEngine, instance2);

    // First process
    containsExactlyInAnyOrder(subscriber1.initialNotifications, p(currentTask1.getId(), true), p(currentTask2.getId(), true));
    subscriber1.clear();
    assertThat(subscriber4.initialIds.stream()).isEmpty();
    assertThat(subscriber4.updateIds.stream()).isEmpty();

    processEngine.getTaskService().complete(currentTask1.getId(), Collections.singletonMap(ApprovalWorkflowTaskListener.APPROVED_KEY, true));
    Task currentTask1Bis = Utils.getCurrentTask(processEngine, instance1);

    assertThat(subscriber1.initialIds.stream()).isEmpty();
    containsExactlyInAnyOrder(subscriber1.updateNotifications, p(currentTask1.getId(), false));
    subscriber1.clear();

    assertThat(subscriber4.initialIds.stream()).isEmpty();
    containsExactlyInAnyOrder(subscriber4.updateNotifications, p(currentTask1Bis.getId(), true));
    subscriber4.clear();

    // Simulate user4 task completion
    processEngine.getTaskService().complete(currentTask1Bis.getId(), Collections.singletonMap(ApprovalWorkflowTaskListener.APPROVED_KEY, true));

    assertThat(subscriber4.initialIds).isEmpty();
    containsExactlyInAnyOrder(subscriber4.updateNotifications, p(currentTask1Bis.getId(), false));
    subscriber4.clear();

    // Reject first task of the second process
    processEngine.getTaskService().complete(currentTask2.getId(), Collections.singletonMap(ApprovalWorkflowTaskListener.APPROVED_KEY, false));

    containsExactlyInAnyOrder(subscriber1.updateNotifications, p(currentTask2.getId(), false));
    // user4 does not receive anything
    assertThat(subscriber4.initialIds).isEmpty();
    assertThat(subscriber4.updateIds).isEmpty();

    subscribe1.unsubscribe();
    subscribe4.unsubscribe();
  }

  /**
   * Sugar syntax.
   */
  void containsExactlyInAnyOrder(List<Notification> notifications, Pair<String, Boolean>... idAndIsActive) {
    assertThat(notifications.stream().map(n -> new Pair<>(n.getBusinessId(), n.getActive()))).containsExactlyInAnyOrder(idAndIsActive);
  }

  static <T, U> Pair<T, U> p(T first, U second) {
    return new Pair<>(first, second);
  }

  static class Pair<T, U> {
    final T first;
    final U second;

    Pair(T first, U second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Pair<?, ?> pair = (Pair<?, ?>) o;
      return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
      return Objects.hash(first, second);
    }

    @Override
    public String toString() {
      return "Pair{" +
              "first=" + first +
              ", second=" + second +
              '}';
    }
  }
}
