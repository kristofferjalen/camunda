/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator} from '@playwright/test';
import {Paths} from 'modules/Routes';
import {convertToQueryString} from '../utils/convertToQueryString';
import {DeleteResourceModal} from './components/DeleteResourceModal';

type OptionalFilter =
  | 'Process Instance Key'
  | 'Decision Instance Key(s)'
  | 'Evaluation Date Range';

export class Decisions {
  private page: Page;

  readonly decisionNameFilter: Locator;
  readonly decisionVersionFilter: Locator;
  readonly decisionViewer: Locator;
  readonly decisionInstanceKeysFilter: Locator;
  readonly deleteResourceButton: Locator;
  readonly deleteResourceModal: InstanceType<typeof DeleteResourceModal>;

  constructor(page: Page) {
    this.page = page;
    this.deleteResourceModal = new DeleteResourceModal(page, {
      name: /Delete DRD/i,
    });

    this.decisionNameFilter = page.getByRole('combobox', {
      name: 'Name',
    });
    this.decisionVersionFilter = page.getByRole('combobox', {
      name: 'Version',
    });
    this.decisionInstanceKeysFilter = page.getByLabel(
      /^decision instance key\(s\)$/i,
    );
    this.decisionViewer = page.getByTestId('decision-viewer');

    this.deleteResourceButton = page.getByRole('button', {
      name: 'Delete Decision Definition',
    });
  }

  async selectDecision(option: string) {
    await this.decisionNameFilter.click();
    await this.page
      .getByRole('region', {name: /filter/i})
      .getByRole('option', {name: option, exact: true})
      .click();
  }

  async selectVersion(option: string) {
    await this.decisionVersionFilter.click();
    await this.page
      .getByRole('region', {name: /filter/i})
      .getByRole('option', {name: option, exact: true})
      .click();
  }

  async displayOptionalFilter(filterName: OptionalFilter) {
    await this.page.getByRole('button', {name: 'More Filters'}).click();
    await this.page
      .getByRole('menuitem', {
        name: filterName,
      })
      .click();
  }

  async navigateToDecisions({
    searchParams,
    options,
  }: {
    searchParams?: Parameters<typeof convertToQueryString>[0];
    options?: Parameters<Page['goto']>[1];
  }) {
    if (searchParams === undefined) {
      await this.page.goto('.' + Paths.decisions());
      return;
    }

    await this.page.goto(
      `.${Paths.decisions()}?${convertToQueryString(searchParams)}`,
      options,
    );
  }

  async clearComboBox() {
    await this.page
      .getByRole('button', {
        name: 'Clear selected item',
      })
      .click();
  }
}
