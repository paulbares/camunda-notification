package me.paulbares.bpmn;

import me.paulbares.camunda.ApprovalWorkflowTaskListener;
import me.paulbares.camunda.BasicApprovalWorflow;
import me.paulbares.service.NotificationService;
import me.paulbares.subscription.ApproverWorkflowRegistrar;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static me.paulbares.Utils.extractCandidates;
import static me.paulbares.Utils.getCurrentTask;

public class TestBasicApprovalWorkflow {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestBasicApprovalWorkflow.class);

  protected static ProcessEngine processEngine;

  protected static ApproverWorkflowRegistrar registrar;

  @BeforeAll
  public static void init() {
    registrar = new ApproverWorkflowRegistrar((a, b) -> Collections.emptyList());
    StandaloneInMemProcessEngineConfiguration conf = new StandaloneInMemProcessEngineConfiguration();
    conf.setBeans(Collections.singletonMap(BasicApprovalWorflow.LISTENER_BEAN_NAME, new ApprovalWorkflowTaskListener(Mockito.mock(NotificationService.class), registrar)));
    processEngine = conf.buildProcessEngine();
    deployIfNecessary(processEngine);
  }

  @AfterAll
  public static void tearDown() {
    processEngine.close();
  }

  /**
   * Deploys the underlying process definition. Made public for testing purpose.
   */
  public static void deployIfNecessary(ProcessEngine processEngine) {
    // To avoid having multiple Process Definition with the same key and since we are redeploying each time the
    // definition, do a query to see if it exists.
    String bpmnName = BasicApprovalWorflow.NAME;
    List<ProcessDefinition> list = processEngine.getRepositoryService()
            .createProcessDefinitionQuery()
            .processDefinitionKey(bpmnName)
            .list();
    if (list == null || list.isEmpty()) {
      // Deploying Process Definition
      final String resource = bpmnName + ".bpmn";
      Deployment deploy = processEngine.getRepositoryService()
              .createDeployment()
              .addModelInstance(resource, BasicApprovalWorflow.getBpmnModelInstance())
              .deploy();
      LOGGER.info("Process definition with key '{}' deployed. {}", bpmnName, deploy);
    } else {
      LOGGER.info("Process definition with key '{}' not deployed because already exists in the repository service.",
              bpmnName);
    }
  }

  @Test
  void testTasksApproved() {
    ProcessInstance instance = processEngine.getRuntimeService()
            .createProcessInstanceByKey(BasicApprovalWorflow.NAME)
            .execute();

    Task currentTask = getCurrentTask(processEngine, instance);
    Set<String>[] candidates = extractCandidates(processEngine, currentTask);

    Assertions.assertThat(candidates[0]).containsExactlyInAnyOrder("user1", "user2", "user3");
    Assertions.assertThat(candidates[1]).containsExactlyInAnyOrder("group1", "group2");

    processEngine.getTaskService().complete(currentTask.getId(), Collections.singletonMap(ApprovalWorkflowTaskListener.APPROVED_KEY, true));

    currentTask = getCurrentTask(processEngine, instance);
    candidates = extractCandidates(processEngine, currentTask);

    Assertions.assertThat(candidates[0]).containsExactlyInAnyOrder("user4");
    Assertions.assertThat(candidates[1]).isEmpty();

    processEngine.getTaskService().complete(currentTask.getId(), Collections.singletonMap(ApprovalWorkflowTaskListener.APPROVED_KEY, true));

    assertProcessEnded(instance.getId());
  }

  @Test
  void testFirstTaskRejected() {
    ProcessInstance instance = processEngine.getRuntimeService()
            .createProcessInstanceByKey(BasicApprovalWorflow.NAME)
            .execute();

    Task currentTask = getCurrentTask(processEngine, instance);
    Set<String>[] candidates = extractCandidates(processEngine, currentTask);

    Assertions.assertThat(candidates[0]).containsExactlyInAnyOrder("user1", "user2", "user3");
    Assertions.assertThat(candidates[1]).containsExactlyInAnyOrder("group1", "group2");

    processEngine.getTaskService().complete(currentTask.getId(), Collections.singletonMap(ApprovalWorkflowTaskListener.APPROVED_KEY, false));
    assertProcessEnded(instance.getId());
  }

  protected void assertProcessEnded(String processInstanceId) {
    ProcessInstance processInstance = processEngine
            .getRuntimeService()
            .createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();

    if (processInstance != null) {
      Assertions.fail("Process with id " + processInstanceId + " is still running");
    }
  }
}
