/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.response;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OpenSearchSnapshotInfo {

  private String snapshot;
  private String uuid;

  private SnapshotState state;

  private List<Object> failures = List.of();

  private Long startTimeInMillis;

  private Map<String, Object> metadata = Map.of();

  public String getSnapshot() {
    return snapshot;
  }

  public OpenSearchSnapshotInfo setSnapshot(final String snapshot) {
    this.snapshot = snapshot;
    return this;
  }

  public String getUuid() {
    return uuid;
  }

  public OpenSearchSnapshotInfo setUuid(final String uuid) {
    this.uuid = uuid;
    return this;
  }

  public SnapshotState getState() {
    return state;
  }

  public OpenSearchSnapshotInfo setState(final SnapshotState state) {
    this.state = state;
    return this;
  }

  public List<Object> getFailures() {
    return failures;
  }

  public OpenSearchSnapshotInfo setFailures(final List<Object> failures) {
    this.failures = failures;
    return this;
  }

  public Long getStartTimeInMillis() {
    return startTimeInMillis;
  }

  public OpenSearchSnapshotInfo setStartTimeInMillis(final Long startTimeInMillis) {
    this.startTimeInMillis = startTimeInMillis;
    return this;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public OpenSearchSnapshotInfo setMetadata(final Map<String, Object> metadata) {
    this.metadata = metadata;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final OpenSearchSnapshotInfo that = (OpenSearchSnapshotInfo) o;
    return Objects.equals(snapshot, that.snapshot)
        && Objects.equals(uuid, that.uuid)
        && Objects.equals(state, that.state)
        && Objects.equals(failures, that.failures)
        && Objects.equals(startTimeInMillis, that.startTimeInMillis)
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(snapshot, uuid, state, failures, startTimeInMillis, metadata);
  }

  @Override
  public String toString() {
    return "SnapshotInfo{"
        + "snapshot='"
        + snapshot
        + '\''
        + ", uuid='"
        + uuid
        + '\''
        + ", state='"
        + state
        + '\''
        + ", failures="
        + failures
        + ", startTimeInMillis="
        + startTimeInMillis
        + ", metadata="
        + metadata
        + '}';
  }
}
