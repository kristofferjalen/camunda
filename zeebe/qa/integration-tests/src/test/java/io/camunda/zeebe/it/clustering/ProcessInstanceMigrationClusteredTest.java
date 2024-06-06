/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class ProcessInstanceMigrationClusteredTest {

  private static final String MESSAGE_NAME = "message1";
  private static final BpmnModelInstance SOURCE_PROCESS =
      Bpmn.createExecutableProcess("sourceProcess")
          .startEvent()
          .userTask("A")
          .boundaryEvent("boundary1")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key1"))
          .endEvent()
          .moveToActivity("A")
          .endEvent()
          .done();

  private static final BpmnModelInstance TARGET_PROCESS =
      Bpmn.createExecutableProcess("targetProcess")
          .startEvent()
          .userTask("B")
          .boundaryEvent("boundary2")
          .message(m -> m.name("message2").zeebeCorrelationKeyExpression("key2"))
          .endEvent()
          .moveToActivity("B")
          .endEvent()
          .done();

  private static final BpmnModelInstance TARGET_PROCESS_2 =
      Bpmn.createExecutableProcess("targetProcess2")
          .startEvent()
          .userTask("C")
          .boundaryEvent("boundary3")
          .message(m -> m.name("message3").zeebeCorrelationKeyExpression("key3"))
          .endEvent()
          .moveToActivity("C")
          .endEvent()
          .done();

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          3,
          1,
          3,
          brokerCfg ->
              brokerCfg.getExperimental().getConsistencyChecks().setEnablePreconditions(true));
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldRejectMigrationWhenMessageSubscriptionMigrationIsNotDistributed() {
    // given
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployResourceCommand()
            .addProcessModel(SOURCE_PROCESS, "process.bpmn")
            .addProcessModel(TARGET_PROCESS, "targetProcess.bpmn")
            .addProcessModel(TARGET_PROCESS_2, "targetProcess2.bpmn")
            .send()
            .join();
    final var sourceProcessDefinitionKey =
        deploymentEvent.getProcesses().get(0).getProcessDefinitionKey();
    final var targetProcessDefinitionKey =
        deploymentEvent.getProcesses().get(1).getProcessDefinitionKey();
    final var secondTargetProcessDefinitionKey =
        deploymentEvent.getProcesses().get(2).getProcessDefinitionKey();

    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ACKNOWLEDGED)
                .withDistributionPartitionId(2)
                .findFirst())
        .isPresent();
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.ACKNOWLEDGED)
                .withDistributionPartitionId(3)
                .findFirst())
        .isPresent();

    int processInstancePartitionId = 0;
    int subscriptionPartitionId = 0;
    long processInstanceKey = -1;

    // To test multi partition behaviour of the migration, we need to ensure that the process
    // instance and the message subscription are on different partitions
    // TODO - make process instance partition id deterministic, so no need to loop
    while (processInstancePartitionId == subscriptionPartitionId) {
      final ProcessInstanceEvent processInstanceEvent =
          clientRule
              .getClient()
              .newCreateInstanceCommand()
              .processDefinitionKey(sourceProcessDefinitionKey)
              .variables(Map.of("key1", "key1", "key2", "key2", "key3", "key3"))
              .send()
              .join();
      processInstanceKey = processInstanceEvent.getProcessInstanceKey();

      processInstancePartitionId = Protocol.decodePartitionId(processInstanceKey);
      subscriptionPartitionId =
          SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(MESSAGE_NAME), 3);
    }

    // Pause the stream processing on the partition leader for the message subscription
    // Therefore, the message subscription migration distribution will not be processed
    final var messageSubscriptionPartitionLeader =
        clusteringRule.getLeaderForPartition(subscriptionPartitionId).getNodeId();
    final var adminServiceMsgSubPartitionLeader =
        clusteringRule
            .getBroker(messageSubscriptionPartitionLeader)
            .getBrokerContext()
            .getBrokerAdminService();
    adminServiceMsgSubPartitionLeader.pauseStreamProcessing();

    // when
    // first migration is expected to be successful
    clientRule
        .getClient()
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .send()
        .join();

    Assertions.assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName(MESSAGE_NAME)
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasBpmnProcessId("targetProcess")
        .hasElementId("boundary2")
        .describedAs("Expect that the correlation key is not re-evaluated")
        .hasCorrelationKey("key1");

    // second migration is expected to be rejected because the message subscription migration
    // distribution is not completed
    clientRule
        .getClient()
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(secondTargetProcessDefinitionKey)
        .addMappingInstruction("B", "C")
        .addMappingInstruction("boundary2", "boundary3")
        .send();

    Assertions.assertThat(
            RecordingExporter.processInstanceMigrationRecords(
                    ProcessInstanceMigrationIntent.MIGRATE)
                .withProcessInstanceKey(processInstanceKey)
                .onlyCommandRejections()
                .getFirst())
        .describedAs("Expect that the migration is rejected")
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            """
              Expected to migrate process instance '%s' \
              but active element with id '%s' has a pending message subscription \
              migration distribution for event with id '%s'."""
                .formatted(processInstanceKey, "B", "boundary1"));

    // TODO - assert that second migration is not rejected when processing is resumed and  message
    // subscription migration is distributed. To assert that, we first need to implement message
    // subscription migration processor where we will also send acknowledge command to the sender
  }
}
