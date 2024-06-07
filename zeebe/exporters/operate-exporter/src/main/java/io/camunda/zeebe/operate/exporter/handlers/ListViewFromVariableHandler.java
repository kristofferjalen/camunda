/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.camunda.zeebe.operate.exporter.util.OperateExportUtil.tenantOrDefault;

public class ListViewFromVariableHandler implements ExportHandler<VariableForListViewEntity, VariableRecordValue> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewFromVariableHandler.class);

  private final ListViewTemplate listViewTemplate;

  public ListViewFromVariableHandler(ListViewTemplate listViewTemplate) {
    this.listViewTemplate = listViewTemplate;
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.VARIABLE;
  }

  @Override
  public Class<VariableForListViewEntity> getEntityType() {
    return VariableForListViewEntity.class;
  }

  @Override
  public boolean handlesRecord(Record<VariableRecordValue> record) {
    return true;
  }

  @Override
  public List<String> generateIds(Record<VariableRecordValue> record) {
    return List.of(String.valueOf(record.getValue().getScopeKey()));
  }

  @Override
  public VariableForListViewEntity createNewEntity(String id) {
    return new VariableForListViewEntity().setId(id);
  }

  @Override
  public void updateEntity(Record<VariableRecordValue> record, VariableForListViewEntity entity) {
    final var recordValue = record.getValue();
    entity.setId(
        VariableForListViewEntity.getIdBy(recordValue.getScopeKey(), recordValue.getName()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setPosition(record.getPosition());
    entity.setScopeKey(recordValue.getScopeKey());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setVarName(recordValue.getName());
    entity.setVarValue(recordValue.getValue());
    entity.setTenantId(tenantOrDefault(recordValue.getTenantId()));

    // set parent
    final Long processInstanceKey = recordValue.getProcessInstanceKey();
    entity.getJoinRelation().setParent(processInstanceKey);
  }

  @Override
  public void flush(VariableForListViewEntity entity, NewElasticsearchBatchRequest batchRequest) throws PersistenceException {
    batchRequest.add(getIndexName(), entity);
  }

  @Override
  public String getIndexName() {
    return listViewTemplate.getFullQualifiedName();
  }
}
