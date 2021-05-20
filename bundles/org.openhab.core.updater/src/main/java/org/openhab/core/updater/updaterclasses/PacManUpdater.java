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
package org.openhab.core.updater.updaterclasses;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link PacManUpdater} is the shell script for updating OpenHab on this OS resp. Package Manager.
 *
 * @author AndrewFG - Initial contribution
 */
@NonNullByDefault
public class PacManUpdater extends DebianUpdater {
    /*
     * This updater operates exactly the same way as the Debian updater. The only difference is that it uses a different
     * script in the '/scripts/YumUpdater.txt' resource (which is why we need it to have a different class name to find
     * the respective resource).
     */

    /**
     * This updater's methods are not yet implemented, so log it and throw an exception.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    protected void initializeExtendedPlaceholders() throws UnsupportedOperationException {
        // TODO complete this class
        UnsupportedOperationException e = new UnsupportedOperationException(
                "This class of updater has not yet been implemented. Please request!");
        logger.warn(e.getMessage());
        throw e;
    }
}