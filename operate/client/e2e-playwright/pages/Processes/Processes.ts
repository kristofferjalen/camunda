/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {convertToQueryString} from '../../utils/convertToQueryString';
import {Paths} from 'modules/Routes';
import {DeleteResourceModal} from '../components/DeleteResourceModal';
import MigrationModal from '../components/MigrationModal';
import MoveModificationModal from '../components/MoveModificationModal';
import {Diagram} from '../components/Diagram';

type OptionalFilter =
  | 'Variable'
  | 'Process Instance Key(s)'
  | 'Parent Process Instance Key'
  | 'Operation Id'
  | 'Error Message'
  | 'Start Date Range'
  | 'End Date Range'
  | 'Failed job but retries left';

export class Processes {
  private page: Page;
  readonly deleteResourceModal: InstanceType<typeof DeleteResourceModal>;
  readonly migrationModal: InstanceType<typeof MigrationModal>;
  readonly moveModificationModal: InstanceType<typeof MoveModificationModal>;
  readonly diagram: InstanceType<typeof Diagram>;
  readonly activeCheckbox: Locator;
  readonly incidentsCheckbox: Locator;
  readonly runningInstancesCheckbox: Locator;
  readonly completedCheckbox: Locator;
  readonly canceledCheckbox: Locator;
  readonly finishedInstancesCheckbox: Locator;
  readonly processNameFilter: Locator;
  readonly processVersionFilter: Locator;
  readonly processInstanceKeysFilter: Locator;
  readonly parentProcessInstanceKey: Locator;
  readonly flowNodeFilter: Locator;
  readonly operationSpinner: Locator;
  readonly operationIdFilter: Locator;
  readonly resetFiltersButton: Locator;
  readonly errorMessageFilter: Locator;
  readonly startDateFilter: Locator;
  readonly variableNameFilter: Locator;
  readonly variableValueFilter: Locator;
  readonly deleteResourceButton: Locator;
  readonly migrateButton: Locator;
  readonly moveButton: Locator;
  readonly processInstancesTable: Locator;

  constructor(page: Page) {
    this.page = page;
    this.deleteResourceModal = new DeleteResourceModal(page, {
      name: /Delete Process Definition/i,
    });
    this.migrationModal = new MigrationModal(page);
    this.moveModificationModal = new MoveModificationModal(page);
    this.diagram = new Diagram(page);
    this.activeCheckbox = page.getByRole('checkbox', {name: 'Active'});
    this.incidentsCheckbox = page.getByRole('checkbox', {name: 'Incidents'});
    this.runningInstancesCheckbox = page.getByRole('checkbox', {
      name: 'Running Instances',
    });
    this.completedCheckbox = page.getByRole('checkbox', {name: 'Completed'});
    this.canceledCheckbox = page.getByRole('checkbox', {name: 'Canceled'});
    this.finishedInstancesCheckbox = page.getByRole('checkbox', {
      name: 'Finished Instances',
    });

    this.processNameFilter = page.getByRole('combobox', {
      name: 'Name',
    });

    this.processVersionFilter = page.getByRole('combobox', {
      name: 'Version',
    });

    this.processInstanceKeysFilter = page.getByLabel(
      /^process instance key\(s\)$/i,
    );

    this.parentProcessInstanceKey = page.getByRole('textbox', {
      name: /parent process instance key/i,
    });

    this.flowNodeFilter = page.getByRole('combobox', {
      name: /flow node/i,
    });

    this.operationSpinner = page.getByTestId('operation-spinner');
    this.operationIdFilter = page.getByRole('textbox', {
      name: /operation id/i,
    });
    this.resetFiltersButton = page.getByRole('button', {
      name: /reset filters/i,
    });

    this.errorMessageFilter = page.getByRole('textbox', {
      name: /error message/i,
    });

    this.startDateFilter = page.getByRole('textbox', {
      name: /start date range/i,
    });

    this.variableNameFilter = page.getByRole('textbox', {
      name: /name/i,
    });

    this.variableValueFilter = page.getByRole('textbox', {
      name: /value/i,
    });

    this.deleteResourceButton = page.getByRole('button', {
      name: 'Delete Process Definition',
    });

    this.processInstancesTable = page.getByRole('region', {
      name: /process instances panel/i,
    });

    this.migrateButton = this.processInstancesTable.getByRole('button', {
      name: /^migrate$/i,
    });

    this.moveButton = this.processInstancesTable.getByRole('button', {
      name: /^move$/i,
    });
  }

  async validateCheckedState({
    checked,
    unChecked,
  }: {
    checked: Array<Locator>;
    unChecked: Array<Locator>;
  }) {
    checked.forEach(async (filter) => {
      await expect(filter).toBeChecked();
    });
    unChecked.forEach(async (filter) => {
      await expect(filter).not.toBeChecked();
    });
  }

  async displayOptionalFilter(filterName: OptionalFilter) {
    await this.page.getByRole('button', {name: 'More Filters'}).click();
    await this.page
      .getByRole('menuitem', {
        name: filterName,
      })
      .click();
  }

  async removeOptionalFilter(filterName: OptionalFilter) {
    await this.page.getByLabel(filterName, {exact: true}).hover();
    await this.page.getByLabel(`Remove ${filterName} Filter`).click();
  }

  async selectProcess(option: string) {
    await this.processNameFilter.click();
    await this.page
      .getByRole('region', {name: /filter/i})
      .getByRole('option', {name: option, exact: true})
      .click();
  }

  async selectVersion(option: string) {
    await this.processVersionFilter.click();
    await this.page
      .getByRole('region', {name: /filter/i})
      .getByRole('option', {name: option, exact: true})
      .click();
  }

  async selectFlowNode(option: string) {
    await this.flowNodeFilter.click();
    await this.page.getByRole('option', {name: option}).click();
  }

  async navigateToProcesses({
    searchParams,
    options,
  }: {
    searchParams?: Parameters<typeof convertToQueryString>[0];
    options?: Parameters<Page['goto']>[1];
  }) {
    if (searchParams === undefined) {
      await this.page.goto('.' + Paths.processes());
      return;
    }

    await this.page.goto(
      `.${Paths.processes()}?${convertToQueryString(searchParams)}`,
      options,
    );
  }

  pickDateTimeRange = async ({
    fromDay,
    toDay,
    fromTime,
    toTime,
  }: {
    fromDay: string;
    toDay: string;
    fromTime?: string;
    toTime?: string;
  }) => {
    await expect(this.page.getByRole('dialog')).toBeVisible();

    const date = new Date();

    const monthName = date.toLocaleString('default', {month: 'long'});
    const year = date.getFullYear();

    await this.page.getByText('From date').click();
    await this.page.getByLabel(`${monthName} ${fromDay}, ${year}`).click();
    await this.page.getByLabel(`${monthName} ${toDay}, ${year}`).click();

    if (fromTime !== undefined) {
      await this.page.getByTestId('fromTime').clear();
      await this.page.getByTestId('fromTime').type(fromTime);
    }

    if (toTime !== undefined) {
      await this.page.getByTestId('toTime').clear();
      await this.page.getByTestId('toTime').type(toTime);
    }
  };

  getNthProcessInstanceCheckbox = (index: number) => {
    return this.processInstancesTable
      .getByRole('row', {name: /select row/i})
      .nth(index)
      .locator('label');
  };
}
