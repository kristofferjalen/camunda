/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '@playwright/test';
import schema from './resources/bigForm.json' assert {type: 'json'};

const MOCK_TASK = {
  id: 'task123',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  processDefinitionId: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  processDefinitionKey: '2251799813685255',
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: null,
  candidateUsers: null,
  context: null,
};

test.describe('form-js integration', () => {
  test('check if Carbonization is working', async ({page}) => {
    page.setViewportSize({
      width: 1920,
      height: 10000,
    });
    await page.route(/^.*\/v1.*$/i, (route) => {
      if (route.request().url().includes('v1/tasks/task123/variables/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/tasks/search')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify([MOCK_TASK]),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/tasks/task123')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify(MOCK_TASK),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/forms/userTaskForm_1')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: 'userTaskForm_3j0n396',
            processDefinitionKey: '2251799813685255',
            schema: JSON.stringify(schema),
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      if (route.request().url().includes('v1/internal/users/current')) {
        return route.fulfill({
          status: 200,
          body: JSON.stringify({
            userId: 'demo',
            displayName: 'demo',
            permissions: ['READ', 'WRITE'],
            salesPlanType: null,
            roles: null,
            c8Links: [],
            tenants: [],
          }),
          headers: {
            'content-type': 'application/json',
          },
        });
      }

      return route.continue();
    });

    await page.goto(`/${MOCK_TASK.id}/`, {
      waitUntil: 'networkidle',
    });

    await expect(page.locator('.fjs-container')).toHaveScreenshot();
  });
});
