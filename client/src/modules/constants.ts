/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const TYPE = {
  GATEWAY_EXCLUSIVE: 'GATEWAY_EXCLUSIVE',
  GATEWAY_EVENT_BASED: 'GATEWAY_EVENT_BASED',
  GATEWAY_PARALLEL: 'GATEWAY_PARALLEL',
  EVENT_START: 'START',
  EVENT_END: 'END',
  EVENT_MESSAGE: 'EVENT_MESSAGE',
  EVENT_TIMER: 'EVENT_TIMER',
  EVENT_ERROR: 'EVENT_ERROR',
  EVENT_INTERMEDIATE_CATCH: 'INTERMEDIATE_CATCH',
  EVENT_INTERMEDIATE_THROW: 'INTERMEDIATE_THROW',
  EVENT_BOUNDARY_INTERRUPTING: 'BOUNDARY_INTERRUPTING',
  EVENT_BOUNDARY_NON_INTERRUPTING: 'BOUNDARY_NON_INTERRUPTING',

  TASK_DEFAULT: 'TASK_DEFAULT',
  TASK_SERVICE: 'TASK_SERVICE',
  TASK_RECEIVE: 'TASK_RECEIVE',
  TASK_SEND: 'TASK_SEND',
  TASK_SUBPROCESS: 'TASK_SUBPROCESS',
  TASK_CALL_ACTIVITY: 'TASK_CALL_ACTIVITY',
  TASK_USER: 'TASK_USER',
  TASK_BUSINESS_RULE: 'TASK_BUSINESS_RULE',
  TASK_SCRIPT: 'TASK_SCRIPT',
  TASK_MANUAL: 'TASK_MANUAL',

  EVENT_SUBPROCESS: 'EVENT_SUBPROCESS',

  PROCESS: 'PROCESS',
  MULTI_INSTANCE_BODY: 'MULTI_INSTANCE_BODY',
} as const;

const MULTI_INSTANCE_TYPE = {
  PARALLEL: 'MULTI_PARALLEL',
  SEQUENTIAL: 'MULTI_SEQUENTIAL',
};

const OPERATION_STATE = {
  SCHEDULED: 'SCHEDULED',
  LOCKED: 'LOCKED',
  SENT: 'SENT',
  COMPLETED: 'COMPLETED',
};

const ACTIVE_OPERATION_STATES = [
  OPERATION_STATE.SCHEDULED,
  OPERATION_STATE.LOCKED,
  OPERATION_STATE.SENT,
];

const FLOWNODE_TYPE_HANDLE = {
  'bpmn:StartEvent': TYPE.EVENT_START,
  'bpmn:EndEvent': TYPE.EVENT_END,
  'bpmn:IntermediateCatchEvent': TYPE.EVENT_INTERMEDIATE_CATCH,
  'bpmn:IntermediateThrowEvent': TYPE.EVENT_INTERMEDIATE_THROW,
  'bpmn:MessageEventDefinition': TYPE.EVENT_MESSAGE,
  'bpmn:ErrorEventDefinition': TYPE.EVENT_ERROR,
  'bpmn:TimerEventDefinition': TYPE.EVENT_TIMER,
  'bpmn:EventBasedGateway': TYPE.GATEWAY_EVENT_BASED,
  'bpmn:ParallelGateway': TYPE.GATEWAY_PARALLEL,
  'bpmn:ExclusiveGateway': TYPE.GATEWAY_EXCLUSIVE,
  'bpmn:SubProcess': TYPE.TASK_SUBPROCESS,
  'bpmn:ServiceTask': TYPE.TASK_SERVICE,
  'bpmn:UserTask': TYPE.TASK_USER,
  'bpmn:BusinessRuleTask': TYPE.TASK_BUSINESS_RULE,
  'bpmn:ScriptTask': TYPE.TASK_SCRIPT,
  'bpmn:ReceiveTask': TYPE.TASK_RECEIVE,
  'bpmn:SendTask': TYPE.TASK_SEND,
  'bpmn:ManualTask': TYPE.TASK_MANUAL,
  'bpmn:CallActivity': TYPE.TASK_CALL_ACTIVITY,
} as const;

const SORT_ORDER = {
  ASC: 'asc',
  DESC: 'desc',
} as const;

const FLOW_NODE_STATE_OVERLAY_ID = 'flow-node-state';
const STATISTICS_OVERLAY_ID = 'flow-nodes-statistics';

const PAGE_TITLE = {
  LOGIN: 'Operate: Log In',
  DASHBOARD: 'Operate: Dashboard',
  INSTANCES: 'Operate: Process Instances',
  INSTANCE: (instanceId: string, processName: string) =>
    `Operate: Process Instance ${instanceId} of ${processName}`,
  DECISION_INSTANCES: 'Operate: Decision Instances',
  DECISION_INSTANCE: (id: string, name: string) =>
    `Operate: Decision Instance ${id} of ${name}`,
};

const PILL_TYPE = {
  TIMESTAMP: 'TIMESTAMP',
  FILTER: 'FILTER',
} as const;

const INCIDENTS_BAR_HEIGHT = 42;

const HEADER_HEIGHT = 57;

const INSTANCE_SELECTION_MODE = {
  INCLUDE: 'INCLUDE',
  EXCLUDE: 'EXCLUDE',
  ALL: 'ALL',
} as const;

export {
  TYPE,
  MULTI_INSTANCE_TYPE,
  ACTIVE_OPERATION_STATES,
  FLOWNODE_TYPE_HANDLE,
  SORT_ORDER,
  FLOW_NODE_STATE_OVERLAY_ID,
  STATISTICS_OVERLAY_ID,
  PAGE_TITLE,
  PILL_TYPE,
  INCIDENTS_BAR_HEIGHT,
  INSTANCE_SELECTION_MODE,
  HEADER_HEIGHT,
};
