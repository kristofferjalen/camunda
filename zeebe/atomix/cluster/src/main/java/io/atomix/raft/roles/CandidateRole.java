/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.roles;

import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.InternalAppendRequest;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.utils.VoteQuorum;
import io.atomix.utils.concurrent.Scheduled;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Candidate state. */
public final class CandidateRole extends ActiveRole {

  private Scheduled currentTimer;

  private int votingRound = 0;

  public CandidateRole(final RaftContext context) {
    super(context);
  }

  @Override
  public synchronized CompletableFuture<RaftRole> start() {
    if (raft.getCluster().isSingleMemberCluster()) {
      log.info("Single member cluster. Transitioning directly to leader.");
      raft.setTerm(raft.getTerm() + 1);
      raft.setLastVotedFor(raft.getCluster().getLocalMember().memberId());
      raft.transition(RaftServer.Role.LEADER);
      return CompletableFuture.completedFuture(this);
    }
    return super.start().thenRun(this::startElection).thenApply(v -> this);
  }

  @Override
  public synchronized CompletableFuture<Void> stop() {
    return super.stop().thenRun(this::cancelElection);
  }

  @Override
  public RaftServer.Role role() {
    return RaftServer.Role.CANDIDATE;
  }

  /** Cancels the election. */
  private void cancelElection() {
    raft.checkThread();
    if (currentTimer != null) {
      log.debug("Cancelling election");
      currentTimer.cancel();
    }
  }

  /** Starts the election. */
  void startElection() {
    log.info("Starting election");
    sendVoteRequests();
  }

  /** Resets the election timer. */
  private void sendVoteRequests() {
    votingRound++;
    raft.checkThread();

    // Because of asynchronous execution, the candidate state could have already been closed. In
    // that case,
    // simply skip the election.
    if (!isRunning()) {
      return;
    }

    // Cancel the current timer task and purge the election timer of cancelled tasks.
    if (currentTimer != null) {
      currentTimer.cancel();
    }

    // When the election timer is reset, increment the current term and
    // restart the election.
    raft.setTerm(raft.getTerm() + 1);
    raft.setLastVotedFor(raft.getCluster().getLocalMember().memberId());

    final AtomicBoolean complete = new AtomicBoolean();
    final var votingMembers = raft.getCluster().getVotingMembers();

    // Send vote requests to all nodes. The vote request that is sent
    // to this node will be automatically successful.
    // First check if the quorum is null. If the quorum isn't null then that
    // indicates that another vote is already going on.
    final var quorum =
        raft.getCluster()
            .getVoteQuorum(
                elected -> {
                  if (!isRunning()) {
                    return;
                  }

                  complete.set(true);
                  if (elected) {
                    raft.transition(RaftServer.Role.LEADER);
                  } else {
                    raft.transition(RaftServer.Role.FOLLOWER);
                  }
                });

    final Duration delay =
        raft.getElectionTimeout()
            .plus(
                Duration.ofMillis(
                    raft.getRandom().nextInt((int) raft.getElectionTimeout().toMillis())));
    currentTimer =
        raft.getThreadContext()
            .schedule(
                delay,
                () -> {
                  if (!complete.get()) {
                    // When the election times out, clear the previous majority vote
                    // check and restart the election.
                    quorum.cancel();

                    final var shouldRetry = votingRound <= 1;
                    if (shouldRetry) {
                      // Attempt one more election to reduce election delay. If transition to
                      // follower, then this member has to wait for another election timeout before
                      // starting the election.
                      log.debug("Election timed out. Restarting election.");
                      sendVoteRequests();
                      votingRound++;
                    } else {
                      // Transition to follower and re-send poll requests to become candidate again.
                      // This delays the election because now this member has to wait for another
                      // electionTimeout to send the poll requests. But assuming this is not the
                      // common case, this delay is acceptable. The other option is to immediately
                      // send new vote request here, but this resulted in an election loop in a very
                      // specific scenario https://github.com/camunda/camunda/issues/11665
                      log.debug("Second round of election timed out. Transitioning to follower.");
                      raft.transition(Role.FOLLOWER);
                    }
                  }
                });

    // First, load the last log entry to get its term. We load the entry
    // by its index since the index is required by the protocol.
    final IndexedRaftLogEntry lastEntry = raft.getLog().getLastEntry();

    final long lastTerm;
    if (lastEntry != null) {
      lastTerm = lastEntry.term();
    } else {
      lastTerm = 0;
    }

    log.debug("Requesting votes for term {}", raft.getTerm());

    // Once we got the last log term, iterate through each current member
    // of the cluster and vote each member for a vote.
    for (final var member : votingMembers) {
      log.debug("Requesting vote from {} for term {}", member, raft.getTerm());
      final VoteRequest request =
          VoteRequest.builder()
              .withTerm(raft.getTerm())
              .withCandidate(raft.getCluster().getLocalMember().memberId())
              .withLastLogIndex(lastEntry != null ? lastEntry.index() : 0)
              .withLastLogTerm(lastTerm)
              .build();

      sendVoteRequestToMember(complete, quorum, member, request);
    }
  }

  private void sendVoteRequestToMember(
      final AtomicBoolean complete,
      final VoteQuorum quorum,
      final RaftMember member,
      final VoteRequest request) {
    raft.getProtocol()
        .vote(member.memberId(), request)
        .whenCompleteAsync(
            (response, error) -> {
              raft.checkThread();
              if (isRunning() && !complete.get()) {
                onVoteResponse(complete, quorum, member, request, response, error);
              }
            },
            raft.getThreadContext());
  }

  private void onVoteResponse(
      final AtomicBoolean complete,
      final VoteQuorum quorum,
      final RaftMember member,
      final VoteRequest request,
      final VoteResponse response,
      final Throwable error) {
    if (error != null) {
      onVoteResponseError(complete, quorum, member, request, error);
    } else {
      if (response.term() > raft.getTerm()) {
        log.debug("Received greater term from {}", member);
        raft.setTerm(response.term());
        complete.set(true);
        raft.transition(RaftServer.Role.FOLLOWER);
      } else if (!response.voted()) {
        log.debug("Received rejected vote from {}", member);
        quorum.fail(member.memberId());
      } else if (response.term() != raft.getTerm()) {
        log.debug("Received successful vote for a different term from {}", member);
        quorum.fail(member.memberId());
      } else {
        log.debug("Received successful vote from {}", member);
        quorum.succeed(member.memberId());
      }
    }
  }

  private void onVoteResponseError(
      final AtomicBoolean complete,
      final VoteQuorum quorum,
      final RaftMember member,
      final VoteRequest request,
      final Throwable error) {
    if (error.getCause() instanceof NoRemoteHandler) {
      log.debug(
          "Member {} is not ready to receive vote requests, will retry later.", member, error);
      if (isRunning() && !complete.get()) {
        raft.getThreadContext()
            .schedule(
                Duration.ofMillis(150),
                () -> sendVoteRequestToMember(complete, quorum, member, request));
      }
    } else {
      log.warn(error.getMessage());
      quorum.fail(member.memberId());
    }
  }

  @Override
  public CompletableFuture<AppendResponse> onAppend(final InternalAppendRequest request) {
    raft.checkThread();

    // If the request indicates a term that is greater than the current term then
    // assign that term and leader to the current context and step down as a candidate.
    if (request.term() >= raft.getTerm()) {
      raft.setTerm(request.term());
      raft.transition(RaftServer.Role.FOLLOWER);
    }
    return super.onAppend(request);
  }

  @Override
  public CompletableFuture<VoteResponse> onVote(final VoteRequest request) {
    raft.checkThread();
    logRequest(request);

    // If the request indicates a term that is greater than the current term then
    // assign that term and leader to the current context and step down as a candidate.
    if (updateTermAndLeader(request.term(), null)) {
      final CompletableFuture<VoteResponse> future = super.onVote(request);
      raft.transition(RaftServer.Role.FOLLOWER);
      return future;
    }

    // If the vote request is not for this candidate then reject the vote.
    if (request.candidate() == raft.getCluster().getLocalMember().memberId()) {
      return CompletableFuture.completedFuture(
          logResponse(
              VoteResponse.builder()
                  .withStatus(RaftResponse.Status.OK)
                  .withTerm(raft.getTerm())
                  .withVoted(true)
                  .build()));
    } else {
      return CompletableFuture.completedFuture(
          logResponse(
              VoteResponse.builder()
                  .withStatus(RaftResponse.Status.OK)
                  .withTerm(raft.getTerm())
                  .withVoted(false)
                  .build()));
    }
  }
}
