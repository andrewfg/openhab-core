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
package org.openhab.core.voice;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A {@link KSEvent} fired when the {@link KSService} spots a keyword.
 *
 * @author Kelly Davis - Initial contribution
 * @author Yannick Schaus - Removed AudioSource information
 */
@NonNullByDefault
public class KSpottedEvent implements KSEvent {
}
