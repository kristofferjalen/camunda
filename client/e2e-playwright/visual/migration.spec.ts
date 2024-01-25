/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockStatistics,
  mockResponses,
} from '../mocks/processes.mocks';
import {open} from 'modules/mocks/diagrams';

test.describe('migration view', () => {
  for (const theme of ['light', 'dark']) {
    test(`initial migration view - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: true,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: open('LotsOfTasks.bpmn'),
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          process: 'LotsOfTasks',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.getNthProcessInstanceCheckbox(0).click();
      await processesPage.migrateButton.click();
      await processesPage.migrationModal.confirmButton.click();

      await expect(page).toHaveScreenshot();
    });
  }
});
