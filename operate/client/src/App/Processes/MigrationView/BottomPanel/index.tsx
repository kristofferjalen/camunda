/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {SelectItem, Stack, Tag, Toggle} from '@carbon/react';

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {autoMappingStore} from 'modules/stores/autoMapping';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {
  BottomSection,
  CheckmarkFilled,
  DataTable,
  ErrorMessageContainer,
  LeftColumn,
  IconContainer,
  Select,
  SourceFlowNodeName,
  ArrowRight,
  ToggleContainer,
} from './styled';

const TOGGLE_LABEL = 'Show only not mapped';

const BottomPanel: React.FC = observer(() => {
  const {selectedSourceFlowNodeIds} = processInstanceMigrationStore;

  const handleCheckIsRowSelected = (selectedSourceFlowNodes?: string[]) => {
    return (rowId: string) => selectedSourceFlowNodes?.includes(rowId) ?? false;
  };
  const {
    updateFlowNodeMapping,
    clearFlowNodeMapping,
    state: {flowNodeMapping},
  } = processInstanceMigrationStore;

  const {
    autoMappableFlowNodes,
    isAutoMappable,
    toggleMappedFilter,
    state: {isMappedFilterEnabled},
  } = autoMappingStore;

  const {
    selectableFlowNodes: selectableSourceFlowNodes,
    hasSelectableFlowNodes: hasSelectableSourceFlowNodes,
  } = processXmlMigrationSourceStore;

  const {isTargetSelected} = processXmlMigrationTargetStore;

  const selectableFilteredSourceFlowNodes = isMappedFilterEnabled
    ? selectableSourceFlowNodes.filter(({id}) => {
        return flowNodeMapping[id] === undefined;
      })
    : selectableSourceFlowNodes;

  // Automatically map flow nodes with same id and type in source and target diagrams
  useEffect(() => {
    clearFlowNodeMapping();
    autoMappableFlowNodes.forEach((sourceFlowNode) => {
      updateFlowNodeMapping({
        sourceId: sourceFlowNode.id,
        targetId: sourceFlowNode.id,
      });
    });
  }, [autoMappableFlowNodes, updateFlowNodeMapping, clearFlowNodeMapping]);

  useEffect(() => {
    // reset store on unmount
    return autoMappingStore.reset;
  }, []);

  return (
    <BottomSection>
      {!hasSelectableSourceFlowNodes ? (
        <ErrorMessageContainer>
          <ErrorMessage
            message="There are no mappable flow nodes"
            additionalInfo="Exit migration to select a different process"
          />
        </ErrorMessageContainer>
      ) : (
        <>
          {isTargetSelected && (
            <ToggleContainer>
              <Toggle
                size="sm"
                id="not-mapped-toggle"
                labelA={TOGGLE_LABEL}
                labelB={TOGGLE_LABEL}
                onToggle={toggleMappedFilter}
              />
              <ArrowRight />
            </ToggleContainer>
          )}
          <DataTable
            size="md"
            headers={[
              {
                header: 'Source flow nodes',
                key: 'sourceFlowNode',
                width: '50%',
              },
              {
                header: 'Target flow nodes',
                key: 'targetFlowNode',
                width: '50%',
              },
            ]}
            onRowClick={(rowId) => {
              processInstanceMigrationStore.selectSourceFlowNode(rowId);
            }}
            checkIsRowSelected={handleCheckIsRowSelected(
              selectedSourceFlowNodeIds,
            )}
            rows={selectableFilteredSourceFlowNodes.map((sourceFlowNode) => {
              const selectableTargetFlowNodes =
                processXmlMigrationTargetStore.selectableFlowNodes.filter(
                  (flowNode) => {
                    return sourceFlowNode.$type === flowNode.$type;
                  },
                );
              const isMapped = flowNodeMapping[sourceFlowNode.id] !== undefined;

              return {
                id: sourceFlowNode.id,
                sourceFlowNode: (
                  <LeftColumn>
                    <SourceFlowNodeName>
                      {sourceFlowNode.name}
                    </SourceFlowNodeName>
                    {isTargetSelected && !isMapped && (
                      <Tag type="blue">Not mapped</Tag>
                    )}
                    <ArrowRight />
                  </LeftColumn>
                ),
                targetFlowNode: (() => {
                  const targetFlowNodeId =
                    flowNodeMapping[sourceFlowNode.id] ?? '';

                  return (
                    <Stack orientation="horizontal" gap={4}>
                      <Select
                        disabled={
                          processInstanceMigrationStore.state.currentStep ===
                            'summary' || selectableTargetFlowNodes.length === 0
                        }
                        size="sm"
                        hideLabel
                        labelText={`Target flow node for ${sourceFlowNode.name}`}
                        id={sourceFlowNode.id}
                        value={targetFlowNodeId}
                        onChange={({target}) => {
                          processInstanceMigrationStore.updateFlowNodeMapping({
                            sourceId: sourceFlowNode.id,
                            targetId: target.value,
                          });
                        }}
                      >
                        {[{id: '', name: ''}, ...selectableTargetFlowNodes].map(
                          ({id, name}) => {
                            return (
                              <SelectItem key={id} value={id} text={name} />
                            );
                          },
                        )}
                      </Select>
                      {isAutoMappable(sourceFlowNode.id) &&
                        // show icon only when target flow node is selected
                        sourceFlowNode.id === targetFlowNodeId && (
                          <IconContainer title="This flow node was automatically mapped">
                            <CheckmarkFilled data-testid="select-icon" />
                          </IconContainer>
                        )}
                    </Stack>
                  );
                })(),
              };
            })}
          />
        </>
      )}
    </BottomSection>
  );
});

export {BottomPanel};
