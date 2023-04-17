/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {within, screen} from '@testing-library/testcafe';
import {t} from 'testcafe';
import {IS_COMBOBOX_ENABLED} from '../../../src/modules/feature-flags';

class ProcessesPage {
  Filters = {
    active: {
      field: within(
        screen.queryByTestId('filter-active').shadowRoot()
      ).queryByRole('checkbox'),
    },

    incidents: {
      field: within(
        screen.queryByTestId('filter-incidents').shadowRoot()
      ).queryByRole('checkbox'),
    },

    runningInstances: {
      field: within(
        screen.queryByTestId('filter-running-instances').shadowRoot()
      ).queryByRole('checkbox'),
    },

    finishedInstances: {
      field: within(
        screen.queryByTestId('filter-finished-instances').shadowRoot()
      ).queryByRole('checkbox'),
    },

    completed: {
      field: within(
        screen.queryByTestId('filter-completed').shadowRoot()
      ).queryByRole('checkbox'),
    },

    canceled: {
      field: within(
        screen.queryByTestId('filter-canceled').shadowRoot()
      ).queryByRole('checkbox'),
    },

    errorMessage: {
      field: screen.queryByTestId('optional-filter-errorMessage'),
      value: within(
        screen.queryByTestId('optional-filter-errorMessage').shadowRoot()
      ).queryByRole('textbox'),
    },

    parentInstanceId: {
      field: screen.queryByTestId('optional-filter-parentInstanceId'),
      value: within(
        screen.queryByTestId('optional-filter-parentInstanceId').shadowRoot()
      ).queryByRole('textbox'),
    },

    startDate: {
      field: screen.queryByTestId('optional-filter-startDateRange'),
      value: within(
        screen.queryByTestId('optional-filter-startDate').shadowRoot()
      ).queryByRole('textbox'),
    },

    endDate: {
      field: screen.queryByTestId('optional-filter-endDateRange'),
      value: within(
        screen.queryByTestId('optional-filter-endDate').shadowRoot()
      ).queryByRole('textbox'),
    },

    operationId: {
      field: screen.queryByTestId('optional-filter-operationId'),
      value: within(
        screen.queryByTestId('optional-filter-operationId').shadowRoot()
      ).queryByRole('textbox'),
    },

    variableName: {
      field: screen.queryByTestId('optional-filter-variable-name'),
      value: within(
        screen.queryByTestId('optional-filter-variable-name').shadowRoot()
      ).queryByRole('textbox'),
    },

    variableValue: {
      field: screen.queryByTestId('optional-filter-variable-value'),
      value: within(
        screen.queryByTestId('optional-filter-variable-value').shadowRoot()
      ).queryByRole('textbox'),
    },

    instanceIds: {
      field: screen.queryByTestId('optional-filter-ids'),
      value: within(
        screen.queryByTestId('optional-filter-ids').shadowRoot()
      ).queryByRole('textbox'),
    },

    processName: {
      field: IS_COMBOBOX_ENABLED
        ? screen.queryByLabelText('Process')
        : screen.queryByTestId('filter-process-name'),
    },

    processVersion: {
      field: screen.queryByTestId('filter-process-version'),
    },

    flowNode: {
      field: screen.queryByTestId('filter-flow-node'),
    },
  };

  resetFiltersButton = screen.queryByRole('button', {name: /reset filters/i});
  selectAllInstancesCheckbox = screen.queryByTitle('Select all instances');

  typeText = async (
    field: Selector | SelectorPromise,
    text: string,
    options?: TypeActionOptions
  ) => {
    await t.typeText(
      within(field.shadowRoot()).queryByRole('textbox'),
      text,
      options
    );
  };

  selectProcess = async (name: string) => {
    if (IS_COMBOBOX_ENABLED) {
      await t.click(screen.queryByLabelText('Process'));
      await t.click(screen.queryByRole('option', {name}));
    } else {
      await t.click(
        within(
          screen.queryByTestId('cm-flyout-process-name').shadowRoot()
        ).queryByText(name)
      );
    }
  };

  selectVersion = async (version: string) => {
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-process-version').shadowRoot()
      ).queryByText(version)
    );
  };

  selectFlowNode = async (name: string) => {
    await t.click(
      within(
        screen.queryByTestId('cm-flyout-flow-node').shadowRoot()
      ).queryByText(name)
    );
  };
}

export const processesPage = new ProcessesPage();
