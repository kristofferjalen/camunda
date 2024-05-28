/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessStoreElasticSearch implements ProcessStore {

  private static final Boolean CASE_INSENSITIVE = true;
  private static final String BPMN_PROCESS_ID_TENANT_ID_AGG_NAME = "bpmnProcessId_tenantId_buckets";
  private static final String TOP_HITS_AGG_NAME = "top_hit_doc";
  private static final String DEFINITION_ID_TERMS_SOURCE_NAME = "group_by_definition_id";
  private static final String TENANT_ID_TERMS_SOURCE_NAME = "group_by_tenant_id";

  @Autowired private ProcessIndex processIndex;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  public ProcessEntity getProcessByProcessDefinitionKey(String processDefinitionKey) {
    final QueryBuilder qb = QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey);

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.KEY))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                    .size(1));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value > 0) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return processEntity;
      } else {
        throw new NotFoundException(
            String.format("Process with key %s not found", processDefinitionKey));
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  /** Gets the process by id. */
  public ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId) {
    return getProcessByBpmnProcessId(bpmnProcessId, null);
  }

  public ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId, final String tenantId) {
    final QueryBuilder qb;
    if (tasklistProperties.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      qb =
          ElasticsearchUtil.joinWithAnd(
              QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, bpmnProcessId),
              QueryBuilders.termQuery(ProcessIndex.TENANT_ID, tenantId));
    } else {
      qb = QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, bpmnProcessId);
    }

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.PROCESS_DEFINITION_ID))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                    .size(1));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value > 0) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return processEntity;
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", bpmnProcessId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public ProcessEntity getProcess(String processId) {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(ProcessIndex.KEY, processId)));

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique process with id '%s'.", processId));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

  public List<ProcessEntity> getProcesses(
      final List<String> processDefinitions, final String tenantId, final Boolean isStartedByForm) {
    final QueryBuilder qb;

    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()) {
      if (processDefinitions.size() == 0) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        qb =
            QueryBuilders.boolQuery()
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));
      } else {

        qb =
            QueryBuilders.boolQuery()
                .must(
                    QueryBuilders.termsQuery(
                        ProcessIndex.PROCESS_DEFINITION_ID, processDefinitions))
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));
      }

    } else {
      qb =
          QueryBuilders.boolQuery()
              .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
              .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));
    }

    QueryBuilder finalQuery = enhanceQueryByTenantIdCheck(qb, tenantId);
    finalQuery = enhanceQueryByIsStartedByForm(finalQuery, isStartedByForm);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(finalQuery);
  }

  public List<ProcessEntity> getProcesses(
      String search,
      final List<String> processDefinitions,
      final String tenantId,
      final Boolean isStartedByForm) {

    if (StringUtils.isBlank(search)) {
      return getProcesses(processDefinitions, tenantId, isStartedByForm);
    }

    final QueryBuilder qb;
    final String regexSearch = String.format(".*%s.*", search);

    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()) {

      if (processDefinitions.size() == 0) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        qb =
            QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.PROCESS_DEFINITION_ID, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""))
                .minimumShouldMatch(1);
      } else {
        qb =
            QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.PROCESS_DEFINITION_ID, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .must(
                    QueryBuilders.termsQuery(
                        ProcessIndex.PROCESS_DEFINITION_ID, processDefinitions))
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""))
                .minimumShouldMatch(1);
      }

    } else {
      qb =
          QueryBuilders.boolQuery()
              .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
              .should(
                  QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                      .caseInsensitive(CASE_INSENSITIVE))
              .should(
                  QueryBuilders.regexpQuery(ProcessIndex.PROCESS_DEFINITION_ID, regexSearch)
                      .caseInsensitive(CASE_INSENSITIVE))
              .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
              .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""))
              .minimumShouldMatch(1);
    }
    QueryBuilder finalQuery = enhanceQueryByTenantIdCheck(qb, tenantId);
    finalQuery = enhanceQueryByIsStartedByForm(finalQuery, isStartedByForm);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(finalQuery);
  }

  private QueryBuilder enhanceQueryByTenantIdCheck(QueryBuilder qb, final String tenantId) {
    if (tasklistProperties.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      return ElasticsearchUtil.joinWithAnd(
          QueryBuilders.termQuery(ProcessIndex.TENANT_ID, tenantId), qb);
    }

    return qb;
  }

  private QueryBuilder enhanceQueryByIsStartedByForm(
      QueryBuilder qb, final Boolean isStartedByForm) {
    // Construct queries to check if FORM_KEY or FORM_ID is not null
    // This is in order to consider a process as started by form but not public
    final BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
    if (Boolean.TRUE.equals(isStartedByForm)) {
      final ExistsQueryBuilder formKeyExistsQuery =
          QueryBuilders.existsQuery(ProcessIndex.FORM_KEY);
      final ExistsQueryBuilder formIdExistsQuery = QueryBuilders.existsQuery(ProcessIndex.FORM_ID);

      boolQuery.should(formKeyExistsQuery);
      boolQuery.should(formIdExistsQuery);
      boolQuery.minimumShouldMatch(1);

      return ElasticsearchUtil.joinWithAnd(boolQuery, qb);
    }
    if (Boolean.FALSE.equals(isStartedByForm)) {
      final BoolQueryBuilder mustNotQuery = QueryBuilders.boolQuery();
      mustNotQuery.mustNot(QueryBuilders.existsQuery(ProcessIndex.FORM_KEY));
      mustNotQuery.mustNot(QueryBuilders.existsQuery(ProcessIndex.FORM_ID));

      boolQuery.must(mustNotQuery);
      return ElasticsearchUtil.joinWithAnd(boolQuery, qb);
    }
    // No filter is applied if isStartedByForm is null
    return qb;
  }

  public List<ProcessEntity> getProcessEntityUniqueByProcessDefinitionIdAndTenantId(
      QueryBuilder qb) {
    final SearchSourceBuilder sourceBuilder =
        new SearchSourceBuilder()
            .query(qb)
            .size(0) // Set size to 0 to retrieve only aggregation results
            .aggregation(
                AggregationBuilders.composite(
                        BPMN_PROCESS_ID_TENANT_ID_AGG_NAME,
                        List.of(
                            new TermsValuesSourceBuilder(DEFINITION_ID_TERMS_SOURCE_NAME)
                                .field(ProcessIndex.PROCESS_DEFINITION_ID),
                            new TermsValuesSourceBuilder(TENANT_ID_TERMS_SOURCE_NAME)
                                .field(ProcessIndex.TENANT_ID)))
                    .size(ElasticsearchUtil.QUERY_MAX_SIZE)
                    .subAggregation(
                        AggregationBuilders.topHits(TOP_HITS_AGG_NAME)
                            .sort(
                                SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                            .size(1)));

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias()).source(sourceBuilder);

    final SearchResponse response;
    try {
      response = tenantAwareClient.search(searchRequest);

      final CompositeAggregation compositeAggregation =
          response.getAggregations().get(BPMN_PROCESS_ID_TENANT_ID_AGG_NAME);
      final List<ProcessEntity> results = new ArrayList<>();
      for (final CompositeAggregation.Bucket bucket : compositeAggregation.getBuckets()) {
        final TopHits topHits = bucket.getAggregations().get(TOP_HITS_AGG_NAME);
        for (final SearchHit hit : topHits.getHits().getHits()) {
          final ProcessEntity entity =
              ElasticsearchUtil.fromSearchHit(
                  hit.getSourceAsString(), objectMapper, ProcessEntity.class);
          results.add(entity);
        }
      }
      return results;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<ProcessEntity> getProcessesStartedByForm() {
    final QueryBuilder qb;

    qb =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
            .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));

    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(qb).stream()
        .filter(ProcessEntity::isStartedByForm)
        .toList();
  }
}
