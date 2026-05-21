/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.core.thing.binding.builder;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusBadgeDecoratorStyle;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;

/**
 * {@link ThingStatusInfoBuilder} is responsible for creating {@link ThingStatusInfo}s.
 *
 * @author Stefan Bußweiler - Initial contribution
 * @author Dennis Nobel - Added null checks
 * @author Andrew Fiddian-Green - Add thing status badge decorator style
 */
@NonNullByDefault
public class ThingStatusInfoBuilder {

    private final ThingStatus status;

    private ThingStatusDetail statusDetail;

    private @Nullable String description;

    private ThingStatusBadgeDecoratorStyle decoratorStyle;

    private ThingStatusInfoBuilder(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description,
            ThingStatusBadgeDecoratorStyle decoratorStyle) {
        this.status = status;
        this.statusDetail = statusDetail;
        this.description = description;
        this.decoratorStyle = decoratorStyle;
    }

    /**
     * Creates a status info builder for the given status and detail.
     * Applies the prior blue dot badge decorator style as the default.
     *
     * @param status the status (must not be null)
     * @param statusDetail the detail of the status (must not be null)
     * @return status info builder
     */
    public static ThingStatusInfoBuilder create(ThingStatus status, ThingStatusDetail statusDetail) {
        return new ThingStatusInfoBuilder(status, statusDetail, null, ThingStatusBadgeDecoratorStyle.INFORMATION);
    }

    /**
     * Creates a status info builder for the given status.
     *
     * @param status the status (must not be null)
     * @return status info builder
     */
    public static ThingStatusInfoBuilder create(ThingStatus status) {
        return create(status, ThingStatusDetail.NONE);
    }

    /**
     * Appends a description to the status to build.
     *
     * @param description the description
     * @return status info builder
     */
    public ThingStatusInfoBuilder withDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    /**
     * Appends a status detail to the status to build.
     *
     * @param statusDetail the status detail
     * @return status info builder
     */
    public ThingStatusInfoBuilder withStatusDetail(ThingStatusDetail statusDetail) {
        this.statusDetail = statusDetail;
        return this;
    }

    /**
     * Applies a thing status badge decorator style to the status to build.
     *
     * @param decoratorStyle the badge decorator style
     * @return status info builder
     */
    public ThingStatusInfoBuilder withThingStatusBadgeDecoratorStyle(ThingStatusBadgeDecoratorStyle decoratorStyle) {
        this.decoratorStyle = decoratorStyle;
        return this;
    }

    /**
     * Builds and returns the status info.
     *
     * @return status info
     */
    public ThingStatusInfo build() {
        return new ThingStatusInfo(status, statusDetail, description, decoratorStyle);
    }
}
