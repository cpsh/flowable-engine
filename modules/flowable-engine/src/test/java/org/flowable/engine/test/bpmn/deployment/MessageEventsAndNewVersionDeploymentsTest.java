/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.engine.test.bpmn.deployment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.impl.test.HistoryTestHelper;
import org.flowable.engine.impl.test.PluggableFlowableTestCase;
import org.flowable.engine.runtime.EventSubscription;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

/**
 * A test specifically written to test how events (start/boundary) are handled when deploying a new version of a process definition.
 * 
 * @author Joram Barrez
 */
public class MessageEventsAndNewVersionDeploymentsTest extends PluggableFlowableTestCase {

    private static final String TEST_PROCESS_GLOBAL_BOUNDARY_MESSAGE = "org/flowable/engine/test/bpmn/deployment/MessageEventsAndNewVersionDeploymentsTest.testGlobalMessageBoundaryEvent.bpmn20.xml";

    private static final String TEST_PROCESS_START_MESSAGE = "org/flowable/engine/test/bpmn/deployment/MessageEventsAndNewVersionDeploymentsTest.testStartMessageEvent.bpmn20.xml";

    private static final String TEST_PROCESS_NO_EVENTS = "org/flowable/engine/test/bpmn/deployment/MessageEventsAndNewVersionDeploymentsTest.processWithoutEvents.bpmn20.xml";

    private static final String TEST_PROCESS_BOTH_START_AND_BOUNDARY_MESSAGE = "org/flowable/engine/test/bpmn/deployment/MessageEventsAndNewVersionDeploymentsTest.testBothBoundaryAndStartMessage.bpmn20.xml";

    private static final String TEST_PROCESS_BOTH_START_AND_BOUNDARY_MESSAGE_SAME_MESSAGE = "org/flowable/engine/test/bpmn/deployment/MessageEventsAndNewVersionDeploymentsTest.testBothBoundaryAndStartMessageSameMessage.bpmn20.xml";

    /*
     * BOUNDARY MESSAGE EVENT
     */

    @Test
    public void testMessageBoundaryEvent() {
        String deploymentId1 = deployBoundaryMessageTestProcess();
        runtimeService.startProcessInstanceByKey("messageTest");
        assertEquals(1, getAllEventSubscriptions().size());

        String deploymentId2 = deployBoundaryMessageTestProcess();
        runtimeService.startProcessInstanceByKey("messageTest");
        assertEquals(2, getAllEventSubscriptions().size());

        assertReceiveMessage("myMessage", 2);

        List<org.flowable.task.api.Task> tasks = taskService.createTaskQuery().list();
        assertEquals(2, tasks.size());

        for (org.flowable.task.api.Task task : tasks) {
            assertEquals("Task after message", task.getName());
        }

        cleanup(deploymentId1, deploymentId2);
    }

    /**
     * Verifying that the event subscriptions do get removed when removing a deployment.
     */
    @Test
    public void testBoundaryEventSubscriptionDeletedOnDeploymentDelete() {
        String deploymentId = deployBoundaryMessageTestProcess();
        runtimeService.startProcessInstanceByKey("messageTest");
        assertEquals("My Task", taskService.createTaskQuery().singleResult().getName());

        String deploymentId2 = deployBoundaryMessageTestProcess();
        runtimeService.startProcessInstanceByKey("messageTest");
        assertEquals(2, taskService.createTaskQuery().count());
        assertEquals(2, getAllEventSubscriptions().size());

        repositoryService.deleteDeployment(deploymentId, true);
        assertEquals("My Task", taskService.createTaskQuery().singleResult().getName());
        assertEquals(1, getAllEventSubscriptions().size());

        repositoryService.deleteDeployment(deploymentId2, true);
        assertEquals(0, getAllEventSubscriptions().size());
    }

    /**
     * Verifying that the event subscriptions do get removed when removing a process instance.
     */
    @Test
    public void testBoundaryEventSubscriptionsDeletedOnProcessInstanceDelete() {
        String deploymentId1 = deployBoundaryMessageTestProcess();
        runtimeService.startProcessInstanceByKey("messageTest");
        assertEquals("My Task", taskService.createTaskQuery().singleResult().getName());

        String deploymentId2 = deployBoundaryMessageTestProcess();
        ProcessInstance processInstance2 = runtimeService.startProcessInstanceByKey("messageTest");
        assertEquals(2, taskService.createTaskQuery().count());
        assertEquals(2, getAllEventSubscriptions().size());

        // Deleting PI of second deployment
        runtimeService.deleteProcessInstance(processInstance2.getId(), "testing");
        assertEquals("My Task", taskService.createTaskQuery().singleResult().getName());
        assertEquals(1, getAllEventSubscriptions().size());

        runtimeService.messageEventReceived("myMessage", getExecutionIdsForMessageEventSubscription("myMessage").get(0));
        assertEquals(0, getAllEventSubscriptions().size());
        assertEquals("Task after message", taskService.createTaskQuery().singleResult().getName());

        cleanup(deploymentId1, deploymentId2);
    }

    /*
     * START MESSAGE EVENT
     */

    @Test
    public void testStartMessageEvent() {
        String deploymentId1 = deployStartMessageTestProcess();
        assertEquals(1, getAllEventSubscriptions().size());
        assertEventSubscriptionsCount(1);
        assertEquals(0, runtimeService.createProcessInstanceQuery().count());
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(1, runtimeService.createProcessInstanceQuery().count());

        String deploymentId2 = deployStartMessageTestProcess();
        assertEventSubscriptionsCount(1);

        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(2, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(1);

        cleanup(deploymentId1, deploymentId2);
    }

    @Test
    public void testMessageStartEventSubscriptionAfterDeploymentDelete() {

        // Deploy two version of process definition, delete latest and check if all is good

        String deploymentId1 = deployStartMessageTestProcess();
        List<EventSubscription> eventSubscriptions = getAllEventSubscriptions();
        assertEquals(1, eventSubscriptions.size());

        String deploymentId2 = deployStartMessageTestProcess();
        eventSubscriptions = getAllEventSubscriptions();
        assertEventSubscriptionsCount(1);

        repositoryService.deleteDeployment(deploymentId2, true);
        eventSubscriptions = getAllEventSubscriptions();
        assertEquals(1, eventSubscriptions.size());

        cleanup(deploymentId1);
        assertEquals(0, getAllEventSubscriptions().size());

        // Deploy two versions of process definition, delete the first
        deploymentId1 = deployStartMessageTestProcess();
        deploymentId2 = deployStartMessageTestProcess();
        assertEquals(1, getAllEventSubscriptions().size());
        repositoryService.deleteDeployment(deploymentId1, true);
        eventSubscriptions = getAllEventSubscriptions();
        assertEquals(1, eventSubscriptions.size());
        assertEquals(repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId2).singleResult().getId(), eventSubscriptions.get(0).getProcessDefinitionId());

        cleanup(deploymentId2);
        assertEquals(0, getAllEventSubscriptions().size());
    }

    /**
     * v1 -> has start message event v2 -> has no start message event v3 -> has start message event
     */
    @Test
    public void testDeployIntermediateVersionWithoutMessageStartEvent() {
        String deploymentId1 = deployStartMessageTestProcess();
        assertEquals(1, getAllEventSubscriptions().size());
        assertEquals(0, runtimeService.createProcessInstanceQuery().count());
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(1, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(1);

        String deploymentId2 = deployProcessWithoutEvents();
        assertEquals(0, getAllEventSubscriptions().size());
        assertEquals(1, runtimeService.createProcessInstanceQuery().count());
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(1, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(0);

        String deploymentId3 = deployStartMessageTestProcess();
        assertEquals(1, getAllEventSubscriptions().size());
        assertEquals(1, runtimeService.createProcessInstanceQuery().count());
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(2, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(1);

        List<EventSubscription> eventSubscriptions = getAllEventSubscriptions();
        assertEquals(repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId3).singleResult().getId(),
                eventSubscriptions.get(0).getProcessDefinitionId());

        cleanup(deploymentId1, deploymentId2, deploymentId3);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents1() {
        String deploymentId1;
        String deploymentId2;
        String deploymentId3;
        deploymentId1 = deployStartMessageTestProcess();
        deploymentId2 = deployProcessWithoutEvents();
        deploymentId3 = deployStartMessageTestProcess();
        repositoryService.deleteDeployment(deploymentId3, true);
        assertEventSubscriptionsCount(0); // the latest is now the one without a message start
        cleanup(deploymentId1, deploymentId2);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents2() {
        String deploymentId1 = deployStartMessageTestProcess();
        String deploymentId2 = deployProcessWithoutEvents();
        String deploymentId3 = deployStartMessageTestProcess();
        repositoryService.deleteDeployment(deploymentId2, true);
        assertEventSubscriptionsCount(1); // the latest is now the one with the message
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId3).singleResult().getId(),
                runtimeService.createProcessInstanceQuery().singleResult().getProcessDefinitionId());
        cleanup(deploymentId1, deploymentId3);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents3() {
        String deploymentId1 = deployStartMessageTestProcess();
        String deploymentId2 = deployProcessWithoutEvents();
        String deploymentId3 = deployStartMessageTestProcess();
        repositoryService.deleteDeployment(deploymentId1, true);
        assertEventSubscriptionsCount(1); // the latest is now the one with the message
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId3).singleResult().getId(),
                runtimeService.createProcessInstanceQuery().singleResult().getProcessDefinitionId());
        cleanup(deploymentId2, deploymentId3);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents4() {
        String deploymentId1 = deployStartMessageTestProcess();
        String deploymentId2 = deployProcessWithoutEvents();
        String deploymentId3 = deployStartMessageTestProcess();
        repositoryService.deleteDeployment(deploymentId2, true);
        repositoryService.deleteDeployment(deploymentId3, true);
        assertEventSubscriptionsCount(1); // the latest is now the one with the message start
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId1).singleResult().getId(),
                runtimeService.createProcessInstanceQuery().singleResult().getProcessDefinitionId());
        cleanup(deploymentId1);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents5() {
        String deploymentId1 = deployStartMessageTestProcess();
        String deploymentId2 = deployProcessWithoutEvents();
        assertEventSubscriptionsCount(0);
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(0, runtimeService.createExecutionQuery().count());
        repositoryService.deleteDeployment(deploymentId2, true);
        assertEventSubscriptionsCount(1); // the first is now the one with the signal
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId1).singleResult().getId(),
                runtimeService.createProcessInstanceQuery().singleResult().getProcessDefinitionId());
        cleanup(deploymentId1);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents6() {
        String deploymentId1 = deployStartMessageTestProcess();
        String deploymentId2 = deployProcessWithoutEvents();
        String deploymentId3 = deployStartMessageTestProcess();
        String deploymentId4 = deployProcessWithoutEvents();
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(0, runtimeService.createExecutionQuery().count());

        repositoryService.deleteDeployment(deploymentId2, true);
        repositoryService.deleteDeployment(deploymentId3, true);
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(0, runtimeService.createExecutionQuery().count());

        repositoryService.deleteDeployment(deploymentId1, true);
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(0, runtimeService.createExecutionQuery().count());
        cleanup(deploymentId4);
    }

    @Test
    public void testDeleteDeploymentWithStartMessageEvents7() {
        String deploymentId1 = deployStartMessageTestProcess();
        String deploymentId2 = deployProcessWithoutEvents();
        String deploymentId3 = deployStartMessageTestProcess();
        String deploymentId4 = deployProcessWithoutEvents();
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(0, runtimeService.createExecutionQuery().count());

        repositoryService.deleteDeployment(deploymentId2, true);
        repositoryService.deleteDeployment(deploymentId3, true);
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(0, runtimeService.createExecutionQuery().count());

        repositoryService.deleteDeployment(deploymentId4, true);
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(1, runtimeService.createProcessInstanceQuery().count());
        cleanup(deploymentId1);
    }

    /*
     * BOTH BOUNDARY AND START MESSAGE
     */

    @Test
    public void testBothBoundaryAndStartEvent() {

        // Deploy process with both boundary and start event

        String deploymentId1 = deployProcessWithBothStartAndBoundaryMessage();
        assertEventSubscriptionsCount(1);
        assertEquals(0, runtimeService.createExecutionQuery().count());

        runtimeService.startProcessInstanceByMessage("myStartMessage");
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(2, runtimeService.createProcessInstanceQuery().count());
        assertEquals(3, getAllEventSubscriptions().size()); // 1 for the start, 2 for the boundary

        // Deploy version with only a boundary signal
        String deploymentId2 = deployBoundaryMessageTestProcess();
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(2, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(2); // 2 boundary events remain

        // Deploy version with signal start
        String deploymentId3 = deployStartMessageTestProcess();
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(3, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(3);

        // Delete last version again, making the one with the boundary the latest
        repositoryService.deleteDeployment(deploymentId3, true);
        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myStartMessage"));
        assertEquals(2, runtimeService.createProcessInstanceQuery().count()); // -1, cause process instance of deploymentId3 is gone too
        assertEventSubscriptionsCount(2); // The 2 boundary remains

        // Test the boundary signal
        assertReceiveMessage("myBoundaryMessage", 2);
        assertEquals(2, taskService.createTaskQuery().taskName("Task after boundary message").list().size());

        // Delete second version
        repositoryService.deleteDeployment(deploymentId2, true);
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(3, runtimeService.createProcessInstanceQuery().count()); // -1, cause process instance of deploymentId3 is gone too
        assertEventSubscriptionsCount(2); // 2 boundaries

        cleanup(deploymentId1);
    }

    @Test
    public void testBothBoundaryAndStartSameMessageId() {

        // Deploy process with both boundary and start event

        String deploymentId1 = deployProcessWithBothStartAndBoundarySameMessage();
        assertEquals(1, getAllEventSubscriptions().size());
        assertEventSubscriptionsCount(1);
        assertEquals(0, runtimeService.createExecutionQuery().count());

        for (int i = 0; i < 9; i++) {
            // Every iteration will signal the boundary event of the previous iteration!
            runtimeService.startProcessInstanceByMessage("myMessage");
        }

        if (HistoryTestHelper.isHistoryLevelAtLeast(HistoryLevel.ACTIVITY, processEngineConfiguration)) {
            assertEquals(9, historyService.createHistoricProcessInstanceQuery().count());
        }
        assertEquals(10, getAllEventSubscriptions().size()); // 1 for the start, 9 for boundary

        // Deploy version with only a start signal. The boundary events should still react though!
        String deploymentId2 = deployStartMessageTestProcess();
        runtimeService.startProcessInstanceByMessage("myStartMessage");
        assertEquals(10, runtimeService.createProcessInstanceQuery().count());
        assertEventSubscriptionsCount(10); // Remains 10: 1 one was removed, but one added for the new message

        assertThatThrownBy(() -> runtimeService.startProcessInstanceByMessage("myMessage"));

        cleanup(deploymentId1, deploymentId2);
    }

    /*
     * HELPERS
     */

    private String deployBoundaryMessageTestProcess() {
        return deploy(TEST_PROCESS_GLOBAL_BOUNDARY_MESSAGE);
    }

    private String deployStartMessageTestProcess() {
        return deploy(TEST_PROCESS_START_MESSAGE);
    }

    private String deployProcessWithoutEvents() {
        return deploy(TEST_PROCESS_NO_EVENTS);
    }

    private String deployProcessWithBothStartAndBoundaryMessage() {
        return deploy(TEST_PROCESS_BOTH_START_AND_BOUNDARY_MESSAGE);
    }

    private String deployProcessWithBothStartAndBoundarySameMessage() {
        return deploy(TEST_PROCESS_BOTH_START_AND_BOUNDARY_MESSAGE_SAME_MESSAGE);
    }

    private String deploy(String path) {
        String deploymentId = repositoryService
                .createDeployment()
                .addClasspathResource(path)
                .deploy()
                .getId();
        return deploymentId;
    }

    private void cleanup(String... deploymentIds) {
        deleteDeployments();
    }

    private List<String> getExecutionIdsForMessageEventSubscription(final String messageName) {
        List<EventSubscription> eventSubscriptions = runtimeService.createEventSubscriptionQuery()
                .eventType("message")
                .eventName(messageName)
                .orderByCreateDate()
                .desc()
                .list();

        List<String> executionIds = new ArrayList<>();
        for (EventSubscription eventSubscription : eventSubscriptions) {
            executionIds.add(eventSubscription.getExecutionId());
        }
        return executionIds;
    }

    private List<EventSubscription> getAllEventSubscriptions() {
        List<EventSubscription> eventSubscriptionEntities = runtimeService.createEventSubscriptionQuery()
                .orderByCreateDate()
                .desc()
                .list();

        for (EventSubscription entity : eventSubscriptionEntities) {
            assertEquals("message", entity.getEventType());
            assertNotNull(entity.getProcessDefinitionId());
        }

        return eventSubscriptionEntities;
    }

    private void assertReceiveMessage(String messageName, int executionIdsCount) {
        List<String> executionIds = getExecutionIdsForMessageEventSubscription(messageName);
        assertEquals(executionIdsCount, executionIds.size());
        for (String executionId : executionIds) {
            runtimeService.messageEventReceived(messageName, executionId);
        }
    }

    private void assertEventSubscriptionsCount(long count) {
        assertEquals(count, getAllEventSubscriptions().size());
    }

}
