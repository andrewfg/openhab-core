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
package org.openhab.core.io.transport.modbus.endpoint;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Endpoint for TCP slaves
 *
 * @author Sami Salonen - Initial contribution
 *
 */
@NonNullByDefault
public class ModbusTCPSlaveEndpoint extends ModbusIPSlaveEndpoint {

    private boolean rtuEncoded;

    public ModbusTCPSlaveEndpoint(String address, int port, boolean rtuEncoded) {
        super(address, port);
        this.rtuEncoded = rtuEncoded;
    }

    public boolean getRtuEncoded() {
        return rtuEncoded;
    }

    @Override
    public <R> R accept(ModbusSlaveEndpointVisitor<R> factory) {
        return factory.visit(this);
    }
}
