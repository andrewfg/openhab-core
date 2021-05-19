/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.core.updater.enums;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PackageManager} identifies the type of Linux Package Manager.
 *
 * @author Andrew Fiddian-Green - Initial contribution
 */
@NonNullByDefault
public enum PackageManager {
    UNKNOWN_PACKAGE_MANAGER,
    DEBIAN_PACKAGE_MANAGER,
    REDHAT_PACKAGE_MANAGER,
    GENTOO_PACKAGE_MANAGER;
}