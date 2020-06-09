package me.paulbares.camunda;

import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BpmnModelElementInstance;

public class BasicApprovalWorflow {

  /**
   * The id of the process.
   */
  public static final String NAME = BasicApprovalWorflow.class.getName();

  /**
   * Constant to define the name of the bean of the {@link TaskListener}.
   */
  public static final String LISTENER_BEAN_NAME = "myBean";

  /**
   * Creates and gets the {@link BpmnModelElementInstance}.
   */
  public static BpmnModelInstance getBpmnModelInstance() {
    String delegateExpression = "${" + LISTENER_BEAN_NAME + "}";
    return Bpmn.createExecutableProcess(NAME)
            .startEvent()

            .userTask()
            .name("first task")
            .camundaCandidateUsers("user1,user2,user3")
            .camundaCandidateGroups("group1,group2")
            .camundaTaskListenerDelegateExpression(TaskListener.EVENTNAME_CREATE, delegateExpression)
            .camundaTaskListenerDelegateExpression(TaskListener.EVENTNAME_COMPLETE, delegateExpression)

            .exclusiveGateway("gateway")
            .condition("first", "${" + ApprovalWorkflowTaskListener.APPROVED_KEY + "}")

            .userTask()
            .name("second task")
            .camundaCandidateUsers("user4")
            .camundaTaskListenerDelegateExpression(TaskListener.EVENTNAME_CREATE, delegateExpression)
            .camundaTaskListenerDelegateExpression(TaskListener.EVENTNAME_COMPLETE, delegateExpression)
            .endEvent()

            .moveToLastGateway()
            .condition("rejected", "${!" + ApprovalWorkflowTaskListener.APPROVED_KEY + "}")

            .endEvent()
            .done();
  }

  /**
   * Main to print the model in XML format.
   *
   * @param args the args
   */
  public static void main(String[] args) {
    BpmnModelInstance modelInstance = getBpmnModelInstance();
    Bpmn.writeModelToStream(System.out, modelInstance);
  }
}
