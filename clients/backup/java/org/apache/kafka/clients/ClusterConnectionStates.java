/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.clients;

import java.util.HashSet;
import java.util.Set;

import java.util.stream.Collectors;

import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.utils.ExponentialBackoff;
import org.apache.kafka.common.utils.LogContext;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The state of our connection to each node in the cluster.
 */
final class ClusterConnectionStates
{
    final static int RECONNECT_BACKOFF_EXP_BASE = 2;
    final static double RECONNECT_BACKOFF_JITTER = 0.2;
    final static int CONNECTION_SETUP_TIMEOUT_EXP_BASE = 2;
    final static double CONNECTION_SETUP_TIMEOUT_JITTER = 0.2;
    private final Map<String, NodeConnectionState> nodeState;
    private final Logger log;
    private Set<String> connectingNodes;
    private ExponentialBackoff reconnectBackoff;
    private ExponentialBackoff connectionSetupTimeout;

    public ClusterConnectionStates(long reconnectBackoffMs, long reconnectBackoffMaxMs, long connectionSetupTimeoutMs, long connectionSetupTimeoutMaxMs, LogContext logContext)
    {
        this.log = logContext.logger(ClusterConnectionStates.class);
        this.reconnectBackoff = new ExponentialBackoff(reconnectBackoffMs, RECONNECT_BACKOFF_EXP_BASE, reconnectBackoffMaxMs, RECONNECT_BACKOFF_JITTER);
        this.connectionSetupTimeout = new ExponentialBackoff(connectionSetupTimeoutMs, CONNECTION_SETUP_TIMEOUT_EXP_BASE, connectionSetupTimeoutMaxMs, CONNECTION_SETUP_TIMEOUT_JITTER);
        this.nodeState = new HashMap<>();
        this.connectingNodes = new HashSet<>();
    }

    /**
     * Return true iff we can currently initiate a new connection. This will be the case if we are not
     * connected and haven't been connected for at least the minimum reconnection backoff period.
     *
     * @param id  the connection id to check
     * @param now the current time in ms
     * @return true if we can initiate a new connection
     */
    public boolean canConnect(String id, long now)
    {
        NodeConnectionState state = nodeState.get(id);
        if (state == null)
        {
            return true;
        } else
        {
            return state.state.isDisconnected() && now - state.lastConnectAttemptMs >= state.reconnectBackoffMs;
        }
    }

    /**
     * Return true if we are disconnected from the given node and can't re-establish a connection yet.
     *
     * @param id  the connection to check
     * @param now the current time in ms
     */
    public boolean isBlackedOut(String id, long now)
    {
        NodeConnectionState state = nodeState.get(id);
        return state != null && state.state.isDisconnected() && now - state.lastConnectAttemptMs < state.reconnectBackoffMs;
    }

    /**
     * Returns the number of milliseconds to wait, based on the connection state, before attempting to send data. When
     * disconnected, this respects the reconnect backoff time. When connecting or connected, this handles slow/stalled
     * connections.
     *
     * @param id  the connection to check
     * @param now the current time in ms
     */
    public long connectionDelay(String id, long now)
    {
        NodeConnectionState state = nodeState.get(id);
        if (state == null)
        {
            return 0;
        }
        if (state.state.isDisconnected())
        {
            long timeWaited = now - state.lastConnectAttemptMs;
            return Math.max(state.reconnectBackoffMs - timeWaited, 0);
        } else
        {
            // When connecting or connected, we should be able to delay indefinitely since other events (connection or
            // data acked) will cause a wakeup once data can be sent.
            return Long.MAX_VALUE;
        }
    }

    /**
     * Return true if a specific connection establishment is currently underway
     *
     * @param id The id of the node to check
     */
    public boolean isConnecting(String id)
    {
        NodeConnectionState state = nodeState.get(id);
        return state != null && state.state == ConnectionState.CONNECTING;
    }

    /**
     * Check whether a connection is either being established or awaiting API version information.
     *
     * @param id The id of the node to check
     * @return true if the node is either connecting or has connected and is awaiting API versions, false otherwise
     */
    public boolean isPreparingConnection(String id)
    {
        NodeConnectionState state = nodeState.get(id);
        return state != null && (state.state == ConnectionState.CONNECTING || state.state == ConnectionState.CHECKING_API_VERSIONS);
    }

    /**
     * Enter the connecting state for the given connection, moving to a new resolved address if necessary.
     *
     * @param id              the id of the connection
     * @param now             the current time in ms
     * @param host            the host of the connection, to be resolved internally if needed
     * @param clientDnsLookup the mode of DNS lookup to use when resolving the {@code host}
     */
    public void connecting(String id, long now, String host, ClientDnsLookup clientDnsLookup)
    {
        NodeConnectionState connectionState = nodeState.get(id);
        if (connectionState != null && connectionState.host().equals(host))
        {
            connectionState.lastConnectAttemptMs = now;
            connectionState.state = ConnectionState.CONNECTING;
            // Move to next resolved address, or if addresses are exhausted, mark node to be re-resolved
            connectionState.moveToNextAddress();
            connectingNodes.add(id);
            return;
        } else if (connectionState != null)
        {
            log.info("Hostname for node {} changed from {} to {}.", id, connectionState.host(), host);
        }

        // Create a new NodeConnectionState if nodeState does not already contain one
        // for the specified id or if the hostname associated with the node id changed.
        nodeState.put(id, new NodeConnectionState(ConnectionState.CONNECTING, now, reconnectBackoff.backoff(0), connectionSetupTimeout.backoff(0), host, clientDnsLookup));
        connectingNodes.add(id);
    }

    /**
     * Returns a resolved address for the given connection, resolving it if necessary.
     *
     * @param id the id of the connection
     * @throws UnknownHostException if the address was not resolvable
     */
    public InetAddress currentAddress(String id) throws UnknownHostException
    {
        return nodeState(id).currentAddress();
    }

    /**
     * Enter the disconnected state for the given node.
     *
     * @param id  the connection we have disconnected
     * @param now the current time in ms
     */
    public void disconnected(String id, long now)
    {
        NodeConnectionState nodeState = nodeState(id);
        nodeState.lastConnectAttemptMs = now;
        updateReconnectBackoff(nodeState);
        if (nodeState.state == ConnectionState.CONNECTING)
        {
            updateConnectionSetupTimeout(nodeState);
            connectingNodes.remove(id);
        } else
        {
            resetConnectionSetupTimeout(nodeState);
        }
        nodeState.state = ConnectionState.DISCONNECTED;
    }

    /**
     * Indicate that the connection is throttled until the specified deadline.
     *
     * @param id                  the connection to be throttled
     * @param throttleUntilTimeMs the throttle deadline in milliseconds
     */
    public void throttle(String id, long throttleUntilTimeMs)
    {
        NodeConnectionState state = nodeState.get(id);
        // The throttle deadline should never regress.
        if (state != null && state.throttleUntilTimeMs < throttleUntilTimeMs)
        {
            state.throttleUntilTimeMs = throttleUntilTimeMs;
        }
    }

    /**
     * Return the remaining throttling delay in milliseconds if throttling is in progress. Return 0, otherwise.
     *
     * @param id  the connection to check
     * @param now the current time in ms
     */
    public long throttleDelayMs(String id, long now)
    {
        NodeConnectionState state = nodeState.get(id);
        if (state != null && state.throttleUntilTimeMs > now)
        {
            return state.throttleUntilTimeMs - now;
        } else
        {
            return 0;
        }
    }

    /**
     * Return the number of milliseconds to wait, based on the connection state and the throttle time, before
     * attempting to send data. If the connection has been established but being throttled, return throttle delay.
     * Otherwise, return connection delay.
     *
     * @param id  the connection to check
     * @param now the current time in ms
     */
    public long pollDelayMs(String id, long now)
    {
        long throttleDelayMs = throttleDelayMs(id, now);
        if (isConnected(id) && throttleDelayMs > 0)
        {
            return throttleDelayMs;
        } else
        {
            return connectionDelay(id, now);
        }
    }

    /**
     * Enter the checking_api_versions state for the given node.
     *
     * @param id the connection identifier
     */
    public void checkingApiVersions(String id)
    {
        NodeConnectionState nodeState = nodeState(id);
        nodeState.state = ConnectionState.CHECKING_API_VERSIONS;
        resetReconnectBackoff(nodeState);
        resetConnectionSetupTimeout(nodeState);
        connectingNodes.remove(id);
    }

    /**
     * Enter the ready state for the given node.
     *
     * @param id the connection identifier
     */
    public void ready(String id)
    {
        NodeConnectionState nodeState = nodeState(id);
        nodeState.state = ConnectionState.READY;
        nodeState.authenticationException = null;
        resetReconnectBackoff(nodeState);
        resetConnectionSetupTimeout(nodeState);
        connectingNodes.remove(id);
    }

    /**
     * Enter the authentication failed state for the given node.
     *
     * @param id        the connection identifier
     * @param now       the current time in ms
     * @param exception the authentication exception
     */
    public void authenticationFailed(String id, long now, AuthenticationException exception)
    {
        NodeConnectionState nodeState = nodeState(id);
        nodeState.authenticationException = exception;
        nodeState.state = ConnectionState.AUTHENTICATION_FAILED;
        nodeState.lastConnectAttemptMs = now;
        updateReconnectBackoff(nodeState);
    }

    /**
     * Return true if the connection is in the READY state and currently not throttled.
     *
     * @param id  the connection identifier
     * @param now the current time in ms
     */
    public boolean isReady(String id, long now)
    {
        return isReady(nodeState.get(id), now);
    }

    private boolean isReady(NodeConnectionState state, long now)
    {
        return state != null && state.state == ConnectionState.READY && state.throttleUntilTimeMs <= now;
    }

    /**
     * Return true if there is at least one node with connection in the READY state and not throttled. Returns false
     * otherwise.
     *
     * @param now the current time in ms
     */
    public boolean hasReadyNodes(long now)
    {
        for (Map.Entry<String, NodeConnectionState> entry : nodeState.entrySet())
        {
            if (isReady(entry.getValue(), now))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the connection has been established
     *
     * @param id The id of the node to check
     */
    public boolean isConnected(String id)
    {
        NodeConnectionState state = nodeState.get(id);
        return state != null && state.state.isConnected();
    }

    /**
     * Return true if the connection has been disconnected
     *
     * @param id The id of the node to check
     */
    public boolean isDisconnected(String id)
    {
        NodeConnectionState state = nodeState.get(id);
        return state != null && state.state.isDisconnected();
    }

    /**
     * Return authentication exception if an authentication error occurred
     *
     * @param id The id of the node to check
     */
    public AuthenticationException authenticationException(String id)
    {
        NodeConnectionState state = nodeState.get(id);
        return state != null ? state.authenticationException : null;
    }

    /**
     * Resets the failure count for a node and sets the reconnect backoff to the base
     * value configured via reconnect.backoff.ms
     *
     * @param nodeState The node state object to update
     */
    private void resetReconnectBackoff(NodeConnectionState nodeState)
    {
        nodeState.failedAttempts = 0;
        nodeState.reconnectBackoffMs = reconnectBackoff.backoff(0);
    }

    /**
     * Resets the failure count for a node and sets the connection setup timeout to the base
     * value configured via socket.connection.setup.timeout.ms
     *
     * @param nodeState The node state object to update
     */
    private void resetConnectionSetupTimeout(NodeConnectionState nodeState)
    {
        nodeState.failedConnectAttempts = 0;
        nodeState.connectionSetupTimeoutMs = connectionSetupTimeout.backoff(0);
    }

    /**
     * Increment the failure counter, update the node reconnect backoff exponentially,
     * and record the current timestamp.
     * The delay is reconnect.backoff.ms * 2**(failures - 1) * (+/- 20% random jitter)
     * Up to a (pre-jitter) maximum of reconnect.backoff.max.ms
     *
     * @param nodeState The node state object to update
     */
    private void updateReconnectBackoff(NodeConnectionState nodeState)
    {
        nodeState.reconnectBackoffMs = reconnectBackoff.backoff(nodeState.failedAttempts);
        nodeState.failedAttempts++;
    }

    /**
     * Increment the failure counter and update the node connection setup timeout exponentially.
     * The delay is socket.connection.setup.timeout.ms * 2**(failures) * (+/- 20% random jitter)
     * Up to a (pre-jitter) maximum of reconnect.backoff.max.ms
     *
     * @param nodeState The node state object to update
     */
    private void updateConnectionSetupTimeout(NodeConnectionState nodeState)
    {
        nodeState.failedConnectAttempts++;
        nodeState.connectionSetupTimeoutMs = connectionSetupTimeout.backoff(nodeState.failedConnectAttempts);
    }

    /**
     * Remove the given node from the tracked connection states. The main difference between this and `disconnected`
     * is the impact on `connectionDelay`: it will be 0 after this call whereas `reconnectBackoffMs` will be taken
     * into account after `disconnected` is called.
     *
     * @param id the connection to remove
     */
    public void remove(String id)
    {
        nodeState.remove(id);
    }

    /**
     * Get the state of a given connection.
     *
     * @param id the id of the connection
     * @return the state of our connection
     */
    public ConnectionState connectionState(String id)
    {
        return nodeState(id).state;
    }

    /**
     * Get the state of a given node.
     *
     * @param id the connection to fetch the state for
     */
    private NodeConnectionState nodeState(String id)
    {
        NodeConnectionState state = this.nodeState.get(id);
        if (state == null)
        {
            throw new IllegalStateException("No entry found for connection " + id);
        }
        return state;
    }

    /**
     * Get the id set of nodes which are in CONNECTING state
     */
    // package private for testing only
    Set<String> connectingNodes()
    {
        return this.connectingNodes;
    }

    /**
     * Get the timestamp of the latest connection attempt of a given node
     *
     * @param id the connection to fetch the state for
     */
    public long lastConnectAttemptMs(String id)
    {
        NodeConnectionState nodeState = this.nodeState.get(id);
        return nodeState == null ? 0 : nodeState.lastConnectAttemptMs;
    }

    /**
     * Get the current socket connection setup timeout of the given node.
     * The base value is defined via socket.connection.setup.timeout.
     *
     * @param id the connection to fetch the state for
     */
    public long connectionSetupTimeoutMs(String id)
    {
        NodeConnectionState nodeState = this.nodeState(id);
        return nodeState.connectionSetupTimeoutMs;
    }

    /**
     * Test if the connection to the given node has reached its timeout
     *
     * @param id  the connection to fetch the state for
     * @param now the current time in ms
     */
    public boolean isConnectionSetupTimeout(String id, long now)
    {
        NodeConnectionState nodeState = this.nodeState(id);
        if (nodeState.state != ConnectionState.CONNECTING)
        {
            throw new IllegalStateException("Node " + id + " is not in connecting state");
        }
        return now - lastConnectAttemptMs(id) > connectionSetupTimeoutMs(id);
    }

    /**
     * Return the Set of nodes whose connection setup has timed out.
     *
     * @param now the current time in ms
     */
    public Set<String> nodesWithConnectionSetupTimeout(long now)
    {
        return connectingNodes.stream().filter(id -> isConnectionSetupTimeout(id, now)).collect(Collectors.toSet());
    }

    /**
     * The state of our connection to a node.
     */
    private static class NodeConnectionState
    {

        ConnectionState state;
        AuthenticationException authenticationException;
        long lastConnectAttemptMs;
        long failedAttempts;
        long failedConnectAttempts;
        long reconnectBackoffMs;
        long connectionSetupTimeoutMs;
        // Connection is being throttled if current time < throttleUntilTimeMs.
        long throttleUntilTimeMs;
        private List<InetAddress> addresses;
        private int addressIndex;
        private final String host;
        private final ClientDnsLookup clientDnsLookup;

        private NodeConnectionState(ConnectionState state, long lastConnectAttempt, long reconnectBackoffMs, long connectionSetupTimeoutMs, String host, ClientDnsLookup clientDnsLookup)
        {
            this.state = state;
            this.addresses = Collections.emptyList();
            this.addressIndex = -1;
            this.authenticationException = null;
            this.lastConnectAttemptMs = lastConnectAttempt;
            this.failedAttempts = 0;
            this.reconnectBackoffMs = reconnectBackoffMs;
            this.connectionSetupTimeoutMs = connectionSetupTimeoutMs;
            this.throttleUntilTimeMs = 0;
            this.host = host;
            this.clientDnsLookup = clientDnsLookup;
        }

        public String host()
        {
            return host;
        }

        /**
         * Fetches the current selected IP address for this node, resolving {@link #host()} if necessary.
         *
         * @return the selected address
         * @throws UnknownHostException if resolving {@link #host()} fails
         */
        private InetAddress currentAddress() throws UnknownHostException
        {
            if (addresses.isEmpty())
            {
                // (Re-)initialize list
                addresses = ClientUtils.resolve(host, clientDnsLookup);
                addressIndex = 0;
            }

            return addresses.get(addressIndex);
        }

        /**
         * Jumps to the next available resolved address for this node. If no other addresses are available, marks the
         * list to be refreshed on the next {@link #currentAddress()} call.
         */
        private void moveToNextAddress()
        {
            if (addresses.isEmpty())
            {
                return; // Avoid div0. List will initialize on next currentAddress() call
            }

            addressIndex = (addressIndex + 1) % addresses.size();
            if (addressIndex == 0)
            {
                addresses = Collections.emptyList(); // Exhausted list. Re-resolve on next currentAddress() call
            }
        }

        public String toString()
        {
            return "NodeState(" + state + ", " + lastConnectAttemptMs + ", " + failedAttempts + ", " + throttleUntilTimeMs + ")";
        }
    }
}
