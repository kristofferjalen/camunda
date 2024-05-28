/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams;

import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.storage.log.RaftLogReader;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.netty.util.NetUtil;
import org.agrona.CloseHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class FlowControlConfigurationTest {

  private static final int PARTITION_ID = 1;
  @Rule public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  private RaftLogReader journalReader;
  private BrokerAdminService brokerAdminService;
  private ZeebeClient client;

  @Before
  public void setup() {
    final RaftPartition raftPartition =
        brokerRule
            .getBroker()
            .getBrokerContext()
            .getPartitionManager()
            .getRaftPartition(PARTITION_ID);
    journalReader = raftPartition.getServer().openReader();
    brokerAdminService = brokerRule.getBroker().getBrokerContext().getBrokerAdminService();

    final String contactPoint = NetUtil.toSocketAddressString(brokerRule.getGatewayAddress());
    final ZeebeClientBuilder zeebeClientBuilder =
        ZeebeClient.newClientBuilder().usePlaintext().gatewayAddress(contactPoint);
    client = zeebeClientBuilder.build();
  }

  @After
  public void after() {
    CloseHelper.closeAll(client, journalReader);
  }

  @Test
  public void shouldConfigureAppendLimit() {
    // given

    // when
    //    brokerAdminService.configureFlowControl(null);

    // then
    //    final long processedIndex = journalReader.seekToAsqn(snapshotId.getProcessedPosition());
    //    final long expectedSnapshotIndex = processedIndex - 1;
    //
    //    assertThat(snapshotId.getIndex()).isEqualTo(expectedSnapshotIndex);
  }
}
