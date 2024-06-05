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
import static io.camunda.zeebe.engine.processing.bpmn.activity.ExecutionListenerTest.createProcessInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public class ExecutionListenerEventElementsTest {

  @RunWith(Parameterized.class)
  public static class ParametrizedExecutionListenerStartEventTest {

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
                  () ->
                      ENGINE.message().withName("startMessage").withCorrelationKey("id").publish(),
                  ParametrizedExecutionListenerStartEventTest::getProcessInstanceKey)
            },
            {
              ParametrizedExecutionListenerStartEventTest.StartEventTestScenario.of(
                  "timer",
                  Collections.emptyMap(),
                  e -> e.timerWithDate(Instant.now().plus(Duration.ofSeconds(25)).toString()),
                  () -> ENGINE.increaseTime(Duration.ofSeconds(25)),
                  ParametrizedExecutionListenerStartEventTest::getProcessInstanceKey)
            },
            {
              StartEventTestScenario.of(
                  "signal",
                  Collections.emptyMap(),
                  e -> e.signal("signal"),
                  () -> ENGINE.signal().withSignalName("signal").broadcast(),
                  ParametrizedExecutionListenerStartEventTest::getProcessInstanceKey)
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

      // trigger event
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

  @RunWith(Parameterized.class)
  public static class ParametrizedExecutionListenerIntermediateCatchEventTest {

    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Parameter public IntermediateCatchEventTestScenario scenario;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> intermediateCatchEventParameters() {
      return Arrays.asList(
          new Object[][] {
            // catch events
            {
              IntermediateCatchEventTestScenario.of(
                  "message",
                  Map.of("key", "key-1"),
                  e -> e.message(m -> m.name("my_message").zeebeCorrelationKeyExpression("key")),
                  () ->
                      ENGINE.message().withName("my_message").withCorrelationKey("key-1").publish())
            },
            {
              IntermediateCatchEventTestScenario.of(
                  "timer",
                  Collections.emptyMap(),
                  e -> e.timerWithDate("=now() + duration(\"PT15S\")"),
                  () -> ENGINE.increaseTime(Duration.ofSeconds(15)))
            },
            {
              IntermediateCatchEventTestScenario.of(
                  "signal",
                  Collections.emptyMap(),
                  e -> e.signal("signal"),
                  () -> ENGINE.signal().withSignalName("signal").broadcast())
            },
          });
    }

    @Test
    public void shouldCompleteIntermediateCatchEventWithMultipleExecutionListeners() {
      // given
      final var modelInstance =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent()
              .intermediateCatchEvent(
                  scenario.name,
                  c ->
                      scenario
                          .builderFunction
                          .apply(c)
                          .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                          .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                          .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                          .zeebeEndExecutionListener(END_EL_TYPE + "_2"))
              .manualTask()
              .endEvent()
              .done();

      final long processInstanceKey =
          createProcessInstance(ENGINE, modelInstance, scenario.processVariables);

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // trigger event
      scenario.intermediateCatchEventTrigger.run();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    private record IntermediateCatchEventTestScenario(
        String name,
        Map<String, Object> processVariables,
        UnaryOperator<IntermediateCatchEventBuilder> builderFunction,
        Runnable intermediateCatchEventTrigger) {

      @Override
      public String toString() {
        return name;
      }

      private static IntermediateCatchEventTestScenario of(
          final String name,
          final Map<String, Object> processVariables,
          final UnaryOperator<IntermediateCatchEventBuilder> builderFunction,
          final Runnable eventTrigger) {
        return new IntermediateCatchEventTestScenario(
            name, processVariables, builderFunction, eventTrigger);
      }
    }
  }

  // non-parametrized tests for event elements
  public static class ExecutionListenerEventTest {
    @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

    @Rule
    public final RecordingExporterTestWatcher recordingExporterTestWatcher =
        new RecordingExporterTestWatcher();

    @Test
    public void shouldCompleteIntermediateNoneThrowEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .intermediateThrowEvent()
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .manualTask()
                  .endEvent()
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteLinkEventWithMultipleExecutionListeners() {
      // given
      final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
      processBuilder
          .startEvent()
          .intermediateThrowEvent(
              "throw",
              b ->
                  b.link("linkA")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_throw_1")
                      .zeebeStartExecutionListener(START_EL_TYPE + "_throw_2")
                      .zeebeEndExecutionListener(END_EL_TYPE + "_throw_1"));
      final BpmnModelInstance modelInstance =
          processBuilder
              .linkCatchEvent("catch")
              .link("linkA")
              .zeebeStartExecutionListener(START_EL_TYPE + "_catch_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_catch_1")
              .zeebeEndExecutionListener(END_EL_TYPE + "_catch_2")
              .manualTask()
              .endEvent()
              .done();

      final long processInstanceKey = createProcessInstance(ENGINE, modelInstance);

      // when: complete the execution listener jobs for link throw events
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_throw_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_throw_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_throw_1").complete();

      // complete the execution listener jobs for link catch events
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_catch_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_catch_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_catch_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_THROW_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(
                  BpmnElementType.INTERMEDIATE_CATCH_EVENT,
                  ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteNoneEndEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent()
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteMessageEndEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent()
                  .message("my_message")
                  .zeebeJobType("end_event_job")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete end event job
      ENGINE.job().ofInstance(processInstanceKey).withType("end_event_job").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteSignalEndEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent()
                  .signal("my_signal")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteTerminateEndEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent()
                  .terminate()
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteEscalationEndEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent()
                  .escalation("escalation_code")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteEscalationEndEventFromSubprocessWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess("process")
                  .startEvent()
                  .subProcess(
                      "sp",
                      s ->
                          s.embeddedSubProcess()
                              .startEvent()
                              .endEvent("end", e -> e.escalation("escalation"))
                              .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                              .zeebeStartExecutionListener(START_EL_TYPE + "_2"))
                  .boundaryEvent("catch", b -> b.escalation("escalation"))
                  .endEvent()
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteErrorEndEventWithMultipleStartExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .subProcess(
                      "subProcess",
                      subProcess ->
                          subProcess
                              .embeddedSubProcess()
                              .startEvent()
                              .manualTask()
                              .endEvent("throw-error", e -> e.error("error"))
                              .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                              .zeebeStartExecutionListener(START_EL_TYPE + "_2"))
                  .boundaryEvent("catch-error", b -> b.error("error"))
                  .endEvent()
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
              tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteCompensationEndEventWithMultipleExecutionListeners() {
      // given
      final long processInstanceKey =
          createProcessInstance(
              ENGINE,
              Bpmn.createExecutableProcess(PROCESS_ID)
                  .startEvent()
                  .manualTask()
                  .endEvent(
                      "compensation-event",
                      e -> e.compensateEventDefinition().compensateEventDefinitionDone())
                  .zeebeStartExecutionListener(START_EL_TYPE + "_1")
                  .zeebeStartExecutionListener(START_EL_TYPE + "_2")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_1")
                  .zeebeEndExecutionListener(END_EL_TYPE + "_2")
                  .done());

      // when: complete the start execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();

      // complete the end execution listener jobs
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      // assert the process instance has completed as expected
      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
          .containsSubsequence(
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.MANUAL_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
    }

    @Test
    public void shouldCompleteCompensationEndEventFromSubprocessWithMultipleExecutionListeners() {
      // given
      final Consumer<EventSubProcessBuilder> compensationEventSubprocess =
          eventSubprocess ->
              eventSubprocess.startEvent().error().endEvent("compensation-throw-event");

      final BpmnModelInstance process =
          Bpmn.createExecutableProcess(PROCESS_ID)
              .eventSubProcess("event-subprocess", compensationEventSubprocess)
              .startEvent()
              .serviceTask(
                  "A",
                  task ->
                      task.zeebeJobType("A")
                          .boundaryEvent()
                          .compensation(
                              compensation ->
                                  compensation.serviceTask("Undo-A").zeebeJobType("Undo-A")))
              .serviceTask("C", task -> task.zeebeJobType("C"))
              .done();

      final EndEvent endEvent = process.getModelElementById("compensation-throw-event");
      endEvent
          .builder()
          .zeebeStartExecutionListener(START_EL_TYPE + "_1")
          .zeebeStartExecutionListener(START_EL_TYPE + "_2")
          .zeebeEndExecutionListener(END_EL_TYPE + "_1")
          .zeebeEndExecutionListener(END_EL_TYPE + "_2")
          .compensateEventDefinition()
          .activityRef("A");

      ENGINE.deployment().withXmlResource(process).deploy();

      final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

      ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

      // when
      ENGINE.job().ofInstance(processInstanceKey).withType("C").withErrorCode("error").throwError();

      // when
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(START_EL_TYPE + "_2").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType("Undo-A").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_1").complete();
      ENGINE.job().ofInstance(processInstanceKey).withType(END_EL_TYPE + "_2").complete();

      assertThat(
              RecordingExporter.processInstanceRecords()
                  .withProcessInstanceKey(processInstanceKey)
                  .limitToProcessInstanceCompleted())
          .extracting(r -> r.getValue().getElementId(), Record::getIntent)
          .containsSubsequence(
              tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATING),
              tuple("compensation-throw-event", ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple("compensation-throw-event", ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_ACTIVATED),
              tuple("Undo-A", ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETING),
              tuple("compensation-throw-event", ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple("compensation-throw-event", ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
              tuple("compensation-throw-event", ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple("event-subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED),
              tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
          .doesNotContain(tuple("Undo-B", ProcessInstanceIntent.ELEMENT_ACTIVATED));
    }
  }
}
