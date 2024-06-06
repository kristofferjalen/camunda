/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.END_EL_TYPE;
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.PROCESS_ID;
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.START_EL_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult;
import io.camunda.zeebe.engine.processing.deployment.model.validation.ProcessValidationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ExecutionListenerStartEventElementTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public StartEventTestScenario scenario;

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> startEventParameters() {
    return Arrays.asList(
        new Object[][] {
          {
            StartEventTestScenario.of(
                "none",
                Collections.emptyMap(),
                e -> e,
                () -> {},
                ignore -> ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create())
          },
          {
            StartEventTestScenario.of(
                "message",
                Map.of("key", "id"),
                e -> e.message(m -> m.name("startMessage").zeebeCorrelationKeyExpression("key")),
                () -> ENGINE.message().withName("startMessage").withCorrelationKey("id").publish(),
                ExecutionListenerStartEventElementTest::getProcessInstanceKey)
          },
          {
            ExecutionListenerStartEventElementTest.StartEventTestScenario.of(
                "timer",
                Collections.emptyMap(),
                e -> e.timerWithDate(Instant.now().plus(Duration.ofSeconds(25)).toString()),
                () -> ENGINE.increaseTime(Duration.ofSeconds(25)),
                ExecutionListenerStartEventElementTest::getProcessInstanceKey)
          },
          {
            StartEventTestScenario.of(
                "signal",
                Collections.emptyMap(),
                e -> e.signal("signal"),
                () -> ENGINE.signal().withSignalName("signal").broadcast(),
                ExecutionListenerStartEventElementTest::getProcessInstanceKey)
          }
        });
  }

  private static long getProcessInstanceKey(final Record<DeploymentRecordValue> deployment) {
    final var processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withProcessDefinitionKey(processDefinitionKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getKey();
  }

  @Test
  public void shouldCompleteStartEventWithMultipleEndExecutionListeners() {
    // given
    final var modelInstance =
        scenario
            .builderFunction
            .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent(scenario.name))
            .zeebeEndExecutionListener(END_EL_TYPE + "_1")
            .zeebeEndExecutionListener(END_EL_TYPE + "_2")
            .manualTask()
            .endEvent()
            .done();

    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // trigger process execution
    scenario.processTrigger.run();

    final long processInstanceKey = scenario.processInstanceKeyProvider.apply(deployment);

    // when: complete end execution listener jobs
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

    // assert that start event has completed as expected
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotDeployProcessWithStartEventWithStartExecutionListeners() {
    // given
    final var modelInstance =
        scenario
            .builderFunction
            .apply(Bpmn.createExecutableProcess(PROCESS_ID).startEvent(scenario.name))
            .zeebeStartExecutionListener(START_EL_TYPE)
            .endEvent()
            .done();

    // when - then
    ProcessValidationUtil.validateProcess(
        modelInstance,
        ExpectedValidationResult.expect(
            StartEvent.class,
            "Execution listeners of type 'start' are not supported by [none, message, signal, timer] start events"));
  }

  private record StartEventTestScenario(
      String name,
      Map<String, Object> processVariables,
      UnaryOperator<StartEventBuilder> builderFunction,
      Runnable processTrigger,
      Function<Record<DeploymentRecordValue>, Long> processInstanceKeyProvider) {

    @Override
    public String toString() {
      return name;
    }

    private static StartEventTestScenario of(
        final String name,
        final Map<String, Object> processVariables,
        final UnaryOperator<StartEventBuilder> builderFunction,
        final Runnable eventTrigger,
        final Function<Record<DeploymentRecordValue>, Long> processInstanceKeyProvider) {
      return new StartEventTestScenario(
          name, processVariables, builderFunction, eventTrigger, processInstanceKeyProvider);
    }
  }
}
