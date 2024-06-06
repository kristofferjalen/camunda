/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import static io.camunda.tasklist.Metrics.*;
import static io.camunda.tasklist.util.CollectionUtil.countNonNullObjects;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNullElse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.Metrics;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskImplementation;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.TaskMetricsStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.store.VariableStore;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.tasklist.webapp.es.TaskValidator;
import io.camunda.tasklist.webapp.graphql.entity.*;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.response.AssignUserTaskResponse;
import java.io.IOException;
import java.util.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

@Component
public class TaskService {

  private static final Logger LOGGER = LoggerFactory.getLogger(TaskService.class);

  @Autowired private UserReader userReader;

  @Autowired
  @Qualifier("tasklistZeebeClient")
  private ZeebeClient zeebeClient;

  @Autowired private TaskStore taskStore;
  @Autowired private VariableService variableService;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private Metrics metrics;
  @Autowired private TaskMetricsStore taskMetricsStore;
  @Autowired private AssigneeMigrator assigneeMigrator;
  @Autowired private TaskValidator taskValidator;

  public List<TaskDTO> getTasks(TaskQueryDTO query) {
    return getTasks(query, emptySet(), false);
  }

  public List<TaskDTO> getTasks(
      TaskQueryDTO query, Set<String> includeVariableNames, boolean fetchFullValuesFromDB) {
    if (countNonNullObjects(
            query.getSearchAfter(), query.getSearchAfterOrEqual(),
            query.getSearchBefore(), query.getSearchBeforeOrEqual())
        > 1) {
      throw new InvalidRequestException(
          "Only one of [searchAfter, searchAfterOrEqual, searchBefore, searchBeforeOrEqual] must be present in request.");
    }

    if (query.getPageSize() <= 0) {
      throw new InvalidRequestException("Page size cannot should be a positive number");
    }

    if (query.getImplementation() != null
        && !query.getImplementation().equals(TaskImplementation.ZEEBE_USER_TASK)
        && !query.getImplementation().equals(TaskImplementation.JOB_WORKER)) {
      throw new InvalidRequestException(
          String.format(
              "Invalid implementation, the valid values are %s and %s",
              TaskImplementation.ZEEBE_USER_TASK, TaskImplementation.JOB_WORKER));
    }

    final List<TaskSearchView> tasks = taskStore.getTasks(query.toTaskQuery());
    final Set<String> fieldNames =
        fetchFullValuesFromDB
            ? emptySet()
            : Set.of(
                "id",
                "name",
                "previewValue",
                "isValueTruncated"); // use fieldNames to not fetch fullValue from DB
    final Map<String, List<VariableDTO>> variablesPerTaskId =
        CollectionUtils.isEmpty(includeVariableNames)
            ? Collections.emptyMap()
            : variableService.getVariablesPerTaskId(
                tasks.stream()
                    .map(
                        taskView ->
                            VariableStore.GetVariablesRequest.createFrom(
                                taskView, new ArrayList<>(includeVariableNames), fieldNames))
                    .toList());
    return tasks.stream()
        .map(
            it ->
                TaskDTO.createFrom(
                    it,
                    Optional.ofNullable(variablesPerTaskId.get(it.getId()))
                        .map(list -> list.toArray(new VariableDTO[list.size()]))
                        .orElse(null),
                    objectMapper))
        .toList();
  }

  public TaskDTO getTask(String taskId) {
    return TaskDTO.createFrom(taskStore.getTask(taskId), objectMapper);
  }

  public TaskDTO assignTask(String taskId, String assignee, Boolean allowOverrideAssignment) {
    if (allowOverrideAssignment == null) {
      allowOverrideAssignment = true;
    }

    final UserDTO currentUser = getCurrentUser();
    if (StringUtils.isEmpty(assignee) && currentUser.isApiUser()) {
      throw new InvalidRequestException("Assignee must be specified");
    }

    if (StringUtils.isNotEmpty(assignee)
        && !currentUser.isApiUser()
        && !assignee.equals(currentUser.getUserId())) {
      throw new ForbiddenActionException(
          "User doesn't have the permission to assign another user to this task");
    }

    final TaskEntity taskBefore = taskStore.getTask(taskId);
    taskValidator.validateCanAssign(taskBefore, allowOverrideAssignment);

    final String taskAssignee = determineTaskAssignee(assignee);

    if (taskBefore.getImplementation().equals(TaskImplementation.ZEEBE_USER_TASK)) {
      try {
        final AssignUserTaskResponse assigneeResponse =
            zeebeClient
                .newUserTaskAssignCommand(Long.parseLong(taskId))
                .assignee(taskAssignee)
                .send()
                .join();
      } catch (ClientException exception) {
        throw new TasklistRuntimeException(exception.getMessage());
      }
    }

    final TaskEntity claimedTask = taskStore.persistTaskClaim(taskBefore, taskAssignee);
    updateClaimedMetric(claimedTask);
    return TaskDTO.createFrom(claimedTask, objectMapper);
  }

  private String determineTaskAssignee(String assignee) {
    final UserDTO currentUser = getCurrentUser();
    return StringUtils.isEmpty(assignee) && !currentUser.isApiUser()
        ? currentUser.getUserId()
        : assignee;
  }

  public TaskDTO completeTask(
      String taskId, List<VariableInputDTO> variables, boolean withDraftVariableValues) {
    final Map<String, Object> variablesMap = new HashMap<>();
    requireNonNullElse(variables, Collections.<VariableInputDTO>emptyList())
        .forEach(
            variable -> variablesMap.put(variable.getName(), this.extractTypedValue(variable)));

    try {
      LOGGER.info("Starting completion of task with ID: {}", taskId);

      final TaskEntity task = taskStore.getTask(taskId);
      taskValidator.validateCanComplete(task);

      try {
        if (task.getImplementation().equals(TaskImplementation.JOB_WORKER)) {
          // complete
          CompleteJobCommandStep1 completeJobCommand =
              zeebeClient.newCompleteCommand(Long.parseLong(taskId));
          completeJobCommand = completeJobCommand.variables(variablesMap);
          completeJobCommand.send().join();
        } else {
          zeebeClient
              .newUserTaskCompleteCommand(Long.parseLong(taskId))
              .variables(variablesMap)
              .send()
              .join();
        }
      } catch (ClientException exception) {
        throw new TasklistRuntimeException(exception.getMessage());
      }

      // persist completion and variables
      final TaskEntity completedTaskEntity = taskStore.persistTaskCompletion(task);
      try {
        LOGGER.info("Start variable persistence: {}", taskId);
        variableService.persistTaskVariables(taskId, variables, withDraftVariableValues);
        deleteDraftTaskVariablesSafely(taskId);
        updateCompletedMetric(completedTaskEntity);
        LOGGER.info("Task with ID {} completed successfully.", taskId);
      } catch (Exception e) {
        LOGGER.error(
            "Task with key {} was COMPLETED but error happened after completion: {}.",
            taskId,
            e.getMessage());
      }

      return TaskDTO.createFrom(completedTaskEntity, objectMapper);
    } catch (HttpServerErrorException e) { // Track only internal server errors
      LOGGER.error("Error completing task with ID: {}. Details: {}", taskId, e.getMessage(), e);
      throw new TasklistRuntimeException("Error completing task with ID: " + taskId, e);
    }
  }

  void deleteDraftTaskVariablesSafely(String taskId) {
    try {
      LOGGER.info(
          "Start deletion of draft task variables associated with task with id='{}'", taskId);
      variableService.deleteDraftTaskVariables(taskId);
    } catch (Exception ex) {
      final String errorMessage =
          String.format(
              "Error during deletion of draft task variables associated with task with id='%s'",
              taskId);
      LOGGER.error(errorMessage, ex);
    }
  }

  private Object extractTypedValue(VariableInputDTO variable) {
    if (variable.getValue().equals("null")) {
      return objectMapper
          .nullNode(); // JSON Object null must be instanced like "null", also should not send to
      // objectMapper null values
    }

    try {
      return objectMapper.readValue(variable.getValue(), Object.class);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  public TaskDTO unassignTask(String taskId) {
    final TaskEntity taskBefore = taskStore.getTask(taskId);
    taskValidator.validateCanUnassign(taskBefore);
    final TaskEntity taskEntity = taskStore.persistTaskUnclaim(taskBefore);
    if (taskBefore.getImplementation().equals(TaskImplementation.ZEEBE_USER_TASK)) {
      try {
        zeebeClient.newUserTaskUnassignCommand(taskBefore.getKey()).send().join();
      } catch (ClientException exception) {
        taskStore.persistTaskClaim(taskBefore, taskBefore.getAssignee());
        throw new TasklistRuntimeException(exception.getMessage());
      }
    }
    return TaskDTO.createFrom(taskEntity, objectMapper);
  }

  private UserDTO getCurrentUser() {
    return userReader.getCurrentUser();
  }

  private void updateClaimedMetric(final TaskEntity task) {
    metrics.recordCounts(COUNTER_NAME_CLAIMED_TASKS, 1, getTaskMetricLabels(task));
  }

  private void updateCompletedMetric(final TaskEntity task) {
    LOGGER.info("Updating completed task metric for task with ID: {}", task.getId());
    try {
      metrics.recordCounts(COUNTER_NAME_COMPLETED_TASKS, 1, getTaskMetricLabels(task));
      assigneeMigrator.migrateUsageMetrics(getCurrentUser().getUserId());
      taskMetricsStore.registerTaskCompleteEvent(task);
    } catch (Exception e) {
      LOGGER.error("Error updating completed task metric for task with ID: {}", task.getId(), e);
      throw new TasklistRuntimeException(
          "Error updating completed task metric for task with ID: " + task.getId(), e);
    }
  }

  private String[] getTaskMetricLabels(final TaskEntity task) {
    final String keyUserId;

    if (getCurrentUser().isApiUser()) {
      if (task.getAssignee() != null) {
        keyUserId = task.getAssignee();
      } else {
        keyUserId = UserReader.DEFAULT_USER;
      }
    } else {
      keyUserId = userReader.getCurrentUserId();
    }

    return new String[] {
      TAG_KEY_BPMN_PROCESS_ID, task.getBpmnProcessId(),
      TAG_KEY_FLOW_NODE_ID, task.getFlowNodeBpmnId(),
      TAG_KEY_USER_ID, keyUserId,
      TAG_KEY_ORGANIZATION_ID, userReader.getCurrentOrganizationId()
    };
  }
}
