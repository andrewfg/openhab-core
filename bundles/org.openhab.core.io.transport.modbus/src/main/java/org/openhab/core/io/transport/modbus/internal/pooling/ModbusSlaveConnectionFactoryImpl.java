/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.io.transport.modbus.internal.pooling;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.io.transport.modbus.endpoint.EndpointPoolConfiguration;
import org.openhab.core.io.transport.modbus.endpoint.ModbusIPSlaveEndpoint;
import org.openhab.core.io.transport.modbus.endpoint.ModbusSerialSlaveEndpoint;
import org.openhab.core.io.transport.modbus.endpoint.ModbusSlaveEndpoint;
import org.openhab.core.io.transport.modbus.endpoint.ModbusSlaveEndpointVisitor;
import org.openhab.core.io.transport.modbus.endpoint.ModbusTCPSlaveEndpoint;
import org.openhab.core.io.transport.modbus.endpoint.ModbusUDPSlaveEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.wimpi.modbus.net.ModbusSlaveConnection;
import net.wimpi.modbus.net.SerialConnection;
import net.wimpi.modbus.net.TCPMasterConnection;
import net.wimpi.modbus.net.UDPMasterConnection;

/**
 * ModbusSlaveConnectionFactoryImpl responsible of the lifecycle of modbus slave connections
 *
 * The actual pool uses instance of this class to create and destroy connections as-needed.
 *
 * The overall functionality goes as follow
 * - create: create connection object but do not connect it yet
 * - destroyObject: close connection and free all resources. Called by the pool when the pool is being closed or the
 * object is invalidated.
 * - activateObject: prepare connection to be used. In practice, connect if disconnected
 * - passivateObject: passivate connection before returning it back to the pool. Currently, passivateObject closes all
 * IP-based connections every now and then (reconnectAfterMillis). Serial connections we keep open.
 * - wrap: wrap created connection to pooled object wrapper class. It tracks usage statistics and last connection time.
 *
 * Note that the implementation must be thread safe.
 *
 * @author Sami Salonen - Initial contribution
 */
@NonNullByDefault
public class ModbusSlaveConnectionFactoryImpl
        extends BaseKeyedPooledObjectFactory<ModbusSlaveEndpoint, @Nullable ModbusSlaveConnection> {

    class PooledConnection extends DefaultPooledObject<@Nullable ModbusSlaveConnection> {

        private volatile long lastConnected;
        private volatile @Nullable ModbusSlaveEndpoint endpoint;

        public PooledConnection(@Nullable ModbusSlaveConnection object) {
            super(object);
        }

        public long getLastConnected() {
            return lastConnected;
        }

        public void setLastConnected(ModbusSlaveEndpoint endpoint, long lastConnected) {
            this.endpoint = endpoint;
            this.lastConnected = lastConnected;
        }

        /**
         *
         * Reset connection if it is too old or fulfills some of the other criteria
         *
         * @param activityName ongoing activity calling this method. For logging
         * @return whether connection was reseted
         */
        public boolean maybeResetConnection(String activityName) {
            ModbusSlaveEndpoint localEndpoint = endpoint;
            if (localEndpoint == null) {
                // We have not connected yet, abort
                // Without endpoint we have no age parameters available (endpointPoolConfigs &
                // disconnectIfConnectedBefore)
                return false;
            }
            long localLastConnected = lastConnected;

            ModbusSlaveConnection connection = getObject();
            if (connection == null) {
                return false;
            }

            EndpointPoolConfiguration configuration = getEndpointPoolConfiguration(localEndpoint);
            long reconnectAfterMillis = configuration.getReconnectAfterMillis();
            long connectionAgeMillis = System.currentTimeMillis() - localLastConnected;
            long disconnectIfConnectedBeforeMillis = disconnectIfConnectedBefore.getOrDefault(localEndpoint, -1L);
            boolean disconnectSinceTooOldConnection = disconnectIfConnectedBeforeMillis >= 0L
                    && localLastConnected <= disconnectIfConnectedBeforeMillis;
            boolean shouldBeDisconnected = (reconnectAfterMillis == 0
                    || (reconnectAfterMillis > 0 && connectionAgeMillis > reconnectAfterMillis)
                    || disconnectSinceTooOldConnection);
            if (shouldBeDisconnected) {
                logger.trace(
                        "({}) Connection {} (endpoint {}) age {}ms is over the reconnectAfterMillis={}ms limit or has been connection time ({}) is after the \"disconnectBeforeConnectedMillis\"={} -> disconnecting.",
                        activityName, connection, localEndpoint, connectionAgeMillis, reconnectAfterMillis,
                        localLastConnected, disconnectIfConnectedBeforeMillis);
                connection.resetConnection();
                return true;
            } else {
                logger.trace(
                        "({}) Connection {} (endpoint {}) age ({}ms) is below the reconnectAfterMillis ({}ms) limit and connection time ({}) is after the \"disconnectBeforeConnectedMillis\"={}. Keep the connection open.",
                        activityName, connection, localEndpoint, connectionAgeMillis, reconnectAfterMillis,
                        localLastConnected, disconnectIfConnectedBeforeMillis);
                return false;
            }
        }
    }

    private final Logger logger = LoggerFactory.getLogger(ModbusSlaveConnectionFactoryImpl.class);
    private volatile Map<ModbusSlaveEndpoint, EndpointPoolConfiguration> endpointPoolConfigs = new ConcurrentHashMap<>();
    private volatile Map<ModbusSlaveEndpoint, Long> lastPassivateMillis = new ConcurrentHashMap<>();
    private volatile Map<ModbusSlaveEndpoint, Long> lastConnectMillis = new ConcurrentHashMap<>();
    private volatile Map<ModbusSlaveEndpoint, Long> disconnectIfConnectedBefore = new ConcurrentHashMap<>();
    private final Function<ModbusSlaveEndpoint, EndpointPoolConfiguration> defaultPoolConfigurationFactory;

    public ModbusSlaveConnectionFactoryImpl(
            Function<ModbusSlaveEndpoint, EndpointPoolConfiguration> defaultPoolConfigurationFactory) {
        this.defaultPoolConfigurationFactory = defaultPoolConfigurationFactory;
    }

    private @Nullable InetAddress getInetAddress(ModbusIPSlaveEndpoint key) {
        try {
            return InetAddress.getByName(key.getAddress());
        } catch (UnknownHostException e) {
            logger.warn("KeyedPooledModbusSlaveConnectionFactory: Unknown host: {}. Connection creation failed.",
                    e.getMessage());
            return null;
        }
    }

    @Override
    public @Nullable ModbusSlaveConnection create(ModbusSlaveEndpoint endpoint) throws Exception {
        return endpoint.accept(new ModbusSlaveEndpointVisitor<@Nullable ModbusSlaveConnection>() {
            @Override
            public @Nullable ModbusSlaveConnection visit(ModbusSerialSlaveEndpoint modbusSerialSlavePoolingKey) {
                SerialConnection connection = new SerialConnection(modbusSerialSlavePoolingKey.getSerialParameters());
                logger.trace("Created connection {} for endpoint {}", connection, modbusSerialSlavePoolingKey);
                return connection;
            }

            @Override
            public @Nullable ModbusSlaveConnection visit(ModbusTCPSlaveEndpoint key) {
                InetAddress address = getInetAddress(key);
                if (address == null) {
                    return null;
                }
                int connectTimeoutMillis = getEndpointPoolConfiguration(key).getConnectTimeoutMillis();
                TCPMasterConnection connection = new TCPMasterConnection(address, key.getPort(), connectTimeoutMillis,
                        key.getRtuEncoded());
                logger.trace("Created connection {} for endpoint {}", connection, key);
                return connection;
            }

            @Override
            public @Nullable ModbusSlaveConnection visit(ModbusUDPSlaveEndpoint key) {
                InetAddress address = getInetAddress(key);
                if (address == null) {
                    return null;
                }
                UDPMasterConnection connection = new UDPMasterConnection(address, key.getPort());
                logger.trace("Created connection {} for endpoint {}", connection, key);
                return connection;
            }
        });
    }

    @Override
    public PooledObject<@Nullable ModbusSlaveConnection> wrap(@Nullable ModbusSlaveConnection connection) {
        return new PooledConnection(connection);
    }

    @Override
    public void destroyObject(ModbusSlaveEndpoint endpoint,
            @Nullable PooledObject<@Nullable ModbusSlaveConnection> obj) {
        if (obj == null) {
            return;
        }
        ModbusSlaveConnection connection = obj.getObject();
        if (connection == null) {
            return;
        }
        logger.trace("destroyObject for connection {} and endpoint {} -> closing the connection", connection, endpoint);
        connection.resetConnection();
    }

    @Override
    public void activateObject(ModbusSlaveEndpoint endpoint,
            @Nullable PooledObject<@Nullable ModbusSlaveConnection> obj) throws Exception {
        if (obj == null) {
            return;
        }
        ModbusSlaveConnection connection = obj.getObject();
        if (connection == null) {
            return;
        }
        try {
            EndpointPoolConfiguration config = getEndpointPoolConfiguration(endpoint);
            if (!connection.isConnected()) {
                tryConnect(endpoint, obj, connection, config);
            }

            long waited = waitAtleast(lastPassivateMillis.get(endpoint), config.getInterTransactionDelayMillis());
            logger.trace(
                    "Waited {}ms (interTransactionDelayMillis {}ms) before giving returning connection {} for endpoint {}, to ensure delay between transactions.",
                    waited, config.getInterTransactionDelayMillis(), obj.getObject(), endpoint);
        } catch (InterruptedException e) {
            // Someone wants to cancel us, reset the connection and abort
            if (connection.isConnected()) {
                connection.resetConnection();
            }
        } catch (Exception e) {
            logger.warn("Error connecting connection {} for endpoint {}: {}", obj.getObject(), endpoint,
                    e.getMessage());
        }
    }

    @Override
    public void passivateObject(ModbusSlaveEndpoint endpoint,
            @Nullable PooledObject<@Nullable ModbusSlaveConnection> obj) {
        if (obj == null) {
            return;
        }
        ModbusSlaveConnection connection = obj.getObject();
        logger.trace("Passivating connection {} for endpoint {}...", connection, endpoint);
        lastPassivateMillis.put(endpoint, System.currentTimeMillis());
        ((PooledConnection) obj).maybeResetConnection("passivate");
        logger.trace("...Passivated connection {} for endpoint {}", obj.getObject(), endpoint);
    }

    @Override
    public boolean validateObject(ModbusSlaveEndpoint key, @Nullable PooledObject<@Nullable ModbusSlaveConnection> p) {
        @SuppressWarnings("null") // p.getObject() cannot be null due to short-circuiting boolean condition
        boolean valid = p != null && p.getObject() != null && p.getObject().isConnected();
        ModbusSlaveConnection slaveConnection = p != null ? p.getObject() : null;
        logger.trace("Validating endpoint {} connection {} -> {}", key, slaveConnection, valid);
        return valid;
    }

    /**
     * Configure general connection settings with a given endpoint
     *
     * @param endpoint endpoint to configure
     * @param config configuration for the endpoint. Use null to reset the configuration to default settings.
     */
    public void setEndpointPoolConfiguration(ModbusSlaveEndpoint endpoint, @Nullable EndpointPoolConfiguration config) {
        if (config == null) {
            endpointPoolConfigs.remove(endpoint);
        } else {
            endpointPoolConfigs.put(endpoint, config);
        }
    }

    /**
     * Get general configuration settings applied to a given endpoint
     *
     * Note that default configuration settings are returned in case the endpoint has not been configured.
     *
     * @param endpoint endpoint to query
     * @return general connection settings of the given endpoint
     */
    public EndpointPoolConfiguration getEndpointPoolConfiguration(ModbusSlaveEndpoint endpoint) {
        return Optional.ofNullable(endpointPoolConfigs.get(endpoint))
                .orElseGet(() -> defaultPoolConfigurationFactory.apply(endpoint));
    }

    private void tryConnect(ModbusSlaveEndpoint endpoint, PooledObject<@Nullable ModbusSlaveConnection> obj,
            ModbusSlaveConnection connection, EndpointPoolConfiguration config) throws Exception {
        if (connection.isConnected()) {
            return;
        }
        int tryIndex = 0;
        Long lastConnect = lastConnectMillis.get(endpoint);
        int maxTries = config.getConnectMaxTries();
        do {
            try {
                long waited = waitAtleast(lastConnect,
                        Math.max(config.getInterConnectDelayMillis(), config.getInterTransactionDelayMillis()));
                if (waited > 0) {
                    logger.trace(
                            "Waited {}ms (interConnectDelayMillis {}ms, interTransactionDelayMillis {}ms) before "
                                    + "connecting disconnected connection {} for endpoint {}, to allow delay "
                                    + "between connections re-connects",
                            waited, config.getInterConnectDelayMillis(), config.getInterTransactionDelayMillis(),
                            obj.getObject(), endpoint);
                }
                connection.connect();

                logger.trace("Waiting {}ms for connection to warm up...", config.getAfterConnectionDelayMillis());
                if (config.getAfterConnectionDelayMillis() > 0) {
                    try {
                        Thread.sleep(config.getAfterConnectionDelayMillis());
                    } catch (InterruptedException e) {
                        // sleep interrupted, continue with the method execution (only fast operations)
                    }
                }
                long curTime = System.currentTimeMillis();
                ((PooledConnection) obj).setLastConnected(endpoint, curTime);
                lastConnectMillis.put(endpoint, curTime);
                break;
            } catch (InterruptedException e) {
                logger.info("Connect try {}/{} failed: {}. Aborting since interrupted. Connection {}. Endpoint {}.",
                        tryIndex, maxTries, e.getMessage(), connection, endpoint);
                throw e;
            } catch (Exception e) {
                tryIndex++;
                logger.debug("Connect try {}/{} failed: {}. Connection {}. Endpoint {}", tryIndex, maxTries,
                        e.getMessage(), connection, endpoint);
                if (tryIndex >= maxTries) {
                    logger.warn("Connect reached max tries {}, throwing last error: {}. Connection {}. Endpoint {}",
                            maxTries, e.getMessage(), connection, endpoint);
                    throw e;
                }
                lastConnect = System.currentTimeMillis();
            }
        } while (true);
    }

    /**
     * Sleep until <code>waitMillis</code> has passed from <code>lastOperation</code>
     *
     * @param lastOperation last time operation was executed, or null if it has not been executed
     * @param waitMillis
     * @return milliseconds slept
     * @throws InterruptedException
     */
    public static long waitAtleast(@Nullable Long lastOperation, long waitMillis) throws InterruptedException {
        if (lastOperation == null) {
            return 0;
        }
        long millisSinceLast = System.currentTimeMillis() - lastOperation;
        long millisToWaitStill = Math.min(waitMillis, Math.max(0, waitMillis - millisSinceLast));
        try {
            Thread.sleep(millisToWaitStill);
        } catch (InterruptedException e) {
            LoggerFactory.getLogger(ModbusSlaveConnectionFactoryImpl.class).debug("wait interrupted", e);
            throw e;
        }
        return millisToWaitStill;
    }

    /**
     * Disconnect returning connections which have been connected before certain time
     *
     * @param disconnectBeforeConnectedMillis disconnected connections that have been connected before this time
     */
    public void disconnectOnReturn(ModbusSlaveEndpoint endpoint, long disconnectBeforeConnectedMillis) {
        disconnectIfConnectedBefore.put(endpoint, disconnectBeforeConnectedMillis);
    }
}
