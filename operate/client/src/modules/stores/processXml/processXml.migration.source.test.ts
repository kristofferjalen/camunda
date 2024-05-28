/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from '@testing-library/react';
import {processXmlStore} from './processXml.migration.source';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';

describe('stores/processXml/processXml.list', () => {
  afterEach(() => {
    processXmlStore.reset();
  });

  it('should filter selectable flow nodes', async () => {
    mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual([
      'checkPayment',
      'requestForPayment',
      'shippingSubProcess',
      'shipArticles',
      'confirmDelivery',
    ]);
  });
});
