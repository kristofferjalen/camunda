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
import io.camunda.zeebe.model.bpmn.builder.IntermediateCatchEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
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
  }
}
