/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.core.GetResponse;
import io.camunda.zeebe.exporter.TestClient.ComponentTemplatesDto.ComponentTemplateWrapper;
import io.camunda.zeebe.exporter.TestClient.IndexSettings;
import io.camunda.zeebe.exporter.TestClient.IndexSettings.Index;
import io.camunda.zeebe.exporter.TestClient.IndexSettings.Settings;
import io.camunda.zeebe.exporter.TestClient.IndexTemplatesDto.IndexTemplateWrapper;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests here should be purely about verifying changes made to Elasticsearch with default
 * configuration. Testing flushing behavior, template configuration, etc., isn't necessary here.
 *
 * <p>Similarly, testing against a secured Elasticsearch, or testing fault tolerance when Elastic is
 * down, should be done elsewhere (e.g. {@link FaultToleranceIT}
 */
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
final class ElasticsearchExporterIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSupport.createDefaultContainer().withEnv("action.destructive_requires_name", "false");

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ProtocolFactory factory = new ProtocolFactory();
  private final ExporterTestController controller = new ExporterTestController();
  private final ElasticsearchExporter exporter = new ElasticsearchExporter();
  private final RecordIndexRouter indexRouter = new RecordIndexRouter(config.index);

  private TestClient testClient;
  private ExporterTestContext exporterTestContext;

  @BeforeAll
  public void beforeAll() {
    config.url = CONTAINER.getHttpHostAddress();
    config.index.setNumberOfShards(1);
    config.index.setNumberOfReplicas(1);
    config.index.createTemplate = true;
    config.bulk.size = 1; // force flushing on the first record
    // here; enable all indexes that needed during the tests beforehand as they will be created once
    TestSupport.provideValueTypes()
        .forEach(valueType -> TestSupport.setIndexingForValueType(config.index, valueType, true));

    testClient = new TestClient(config, indexRouter);

    exporterTestContext =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("elastic", config));
    exporter.configure(exporterTestContext);
    exporter.open(controller);
  }

  @AfterAll
  void afterAll() {
    CloseHelper.quietCloseAll(testClient);
  }

  @BeforeEach
  void cleanup() {
    testClient.deleteIndices();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
  void shouldExportRecord(final ValueType valueType) {
    // given
    final var record = factory.generateRecord(valueType);

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response)
        .extracting(GetResponse::index, GetResponse::id, GetResponse::routing, GetResponse::source)
        .containsExactly(
            indexRouter.indexFor(record),
            indexRouter.idFor(record),
            String.valueOf(record.getPartitionId()),
            record);
  }

  // both tests below are regression tests for https://github.com/camunda/camunda/issues/4640
  // one option would be to get rid of these and instead have unit tests on the templates themselves
  // where we can guarantee that this field is not indexed, for example

  // regression test for https://github.com/camunda/camunda/issues/4640
  @Test
  void shouldExportJobRecordWithOverlappingCustomHeaders() {
    // given
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(Map.of("x", "1", "x.y", "2"))
            .build();
    final Record<JobRecordValue> record =
        factory.generateRecord(ValueType.JOB, builder -> builder.withValue(value));

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response.source()).isEqualTo(record);
  }

  // regression test for https://github.com/camunda/camunda/issues/4640
  @Test
  void shouldExportJobBatchRecordWithOverlappingCustomHeaders() {
    // given
    final JobRecordValue job =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withCustomHeaders(Map.of("x", "1", "x.y", "2"))
            .build();
    final JobBatchRecordValue value =
        ImmutableJobBatchRecordValue.builder()
            .from(factory.generateObject(JobBatchRecordValue.class))
            .withJobs(Collections.singleton(job))
            .withJobKeys(Collections.singleton(1L))
            .build();
    final Record<JobBatchRecordValue> record =
        factory.generateRecord(ValueType.JOB_BATCH, builder -> builder.withValue(value));

    // when
    export(record);

    // then
    final var response = testClient.getExportedDocumentFor(record);
    assertThat(response.source()).isEqualTo(record);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
  void shouldPutIndexTemplate(final ValueType valueType) {
    // assuming
    Assumptions.assumeTrue(
        config.shouldIndexValueType(valueType),
        "no template is created because the exporter is configured filter out records of this type");

    // given
    final var record = factory.generateRecord(valueType);
    final var expectedIndexTemplateName = indexRouter.indexPrefixForValueType(valueType);

    // when - export a single record to enforce installing all index templatesWrapper
    export(record);

    // then
    final var template = testClient.getIndexTemplate(valueType);
    assertThat(template)
        .as("should have created index template for value type %s", valueType)
        .isPresent()
        .get()
        .extracting(IndexTemplateWrapper::name)
        .isEqualTo(expectedIndexTemplateName);
  }

  @Test
  void shouldPutComponentTemplate() {
    // given
    final var record = factory.generateRecord();

    // when - export a single record to enforce installing all index templatesWrapper
    export(record);

    // then
    final var template = testClient.getComponentTemplate();
    assertThat(template)
        .as("should have created the component template")
        .isPresent()
        .get()
        .extracting(ComponentTemplateWrapper::name)
        .isEqualTo(config.index.prefix);
  }

  private boolean export(final Record<?> record) {
    exporter.export(record);
    return true;
  }

  @Nested
  final class IndexSettingsTest {
    @Test
    void shouldAddIndexLifecycleSettingsToExistingIndicesOnRerunWhenRetentionIsEnabled() {
      // given
      configureExporter(false);
      final var record1 = factory.generateRecord(ValueType.JOB);

      // when
      export(record1);

      // then
      final var index1 = indexRouter.indexFor(record1);
      var response1 = testClient.getIndexSettings(index1);

      assertIndexSettingsHasNoLifecyclePolicy(response1);

      /* Tests when retention is later enabled all indices should have lifecycle policy */
      // given
      configureExporter(true);
      final var record2 = factory.generateRecord(ValueType.JOB);

      // when
      export(record2);

      // then
      final var index2 = indexRouter.indexFor(record2);
      final var response2 = testClient.getIndexSettings(index2);
      assertIndexSettingsHasLifecyclePolicy(response2);

      response1 = testClient.getIndexSettings(index1);
      assertIndexSettingsHasLifecyclePolicy(response1);
    }

    @Test
    void shouldRemoveIndexLifecycleSettingsFromExistingIndicesOnRerunWhenRetentionIsDisabled() {
      // given
      configureExporter(true);
      final var record1 = factory.generateRecord(ValueType.JOB);

      // when
      export(record1);

      // then
      final var index1 = indexRouter.indexFor(record1);
      var response1 = testClient.getIndexSettings(index1);
      assertIndexSettingsHasLifecyclePolicy(response1);

      /* Tests when retention is later disabled all indices should not have a lifecycle policy */
      // given
      configureExporter(false);
      final var record2 = factory.generateRecord(ValueType.JOB);

      // when
      export(record2);

      // then
      final var index2 = indexRouter.indexFor(record2);
      final var response2 = testClient.getIndexSettings(index2);
      assertIndexSettingsHasNoLifecyclePolicy(response2);

      response1 = testClient.getIndexSettings(index1);
      assertIndexSettingsHasNoLifecyclePolicy(response1);
    }

    /**
     * Default timeout for elasticsearch `PUT /<target>/_settings` is 30 seconds.
     *
     * <p>500 records each has a shard and a replica means 1000 shards, which is the maximum open
     * shards in a one node cluster
     */
    @Test
    void shouldNotTimeoutWhenUpdatingLifecyclePolicyForExistingIndices() {
      // given
      configureExporter(false);
      final var records = new ArrayList<Record<RecordValue>>();
      // using 498 here as we will export one more record after (1 main shard, 1 replica)
      final int limit = 498;
      for (int i = 0; i < limit; i++) {
        final var record = factory.generateRecord(ValueType.JOB);
        records.add(record);
        export(record);
      }
      // when
      configureExporter(true);
      final var record2 = factory.generateRecord(ValueType.JOB);
      // when
      await("New record is exported, and existing indices are updated")
          .atMost(Duration.ofSeconds(30))
          .until(() -> export(record2));

      // then
      final var index2 = indexRouter.indexFor(record2);
      final var response2 = testClient.getIndexSettings(index2);
      assertIndexSettingsHasLifecyclePolicy(response2);

      for (final var record : records) {
        final var index = indexRouter.indexFor(record);
        final var response = testClient.getIndexSettings(index);

        assertIndexSettingsHasLifecyclePolicy(response);
      }
    }

    private void configureExporter(final boolean retentionEnabled) {
      config.retention.setEnabled(retentionEnabled);
      exporter.configure(exporterTestContext);
    }

    private void assertIndexSettingsHasLifecyclePolicy(
        final Optional<IndexSettings> indexSettings) {
      assertThat(indexSettings)
          .as("should have found the index")
          .isPresent()
          .get()
          .extracting(IndexSettings::settings)
          .extracting(Settings::index)
          .extracting(Index::lifecycle)
          .as("should have lifecycle config")
          .isNotNull()
          .extracting(IndexSettings.Lifecycle::name)
          .isEqualTo(config.retention.getPolicyName());
    }

    private static void assertIndexSettingsHasNoLifecyclePolicy(
        final Optional<IndexSettings> indexSettings) {
      assertThat(indexSettings)
          .as("should have found the index")
          .isPresent()
          .get()
          .extracting(IndexSettings::settings)
          .extracting(Settings::index)
          .extracting(Index::lifecycle)
          .as("Lifecycle policy should not be configured")
          .isNull();
    }
  }
}
