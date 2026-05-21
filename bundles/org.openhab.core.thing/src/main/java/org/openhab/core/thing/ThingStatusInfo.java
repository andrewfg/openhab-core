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
package org.openhab.core.thing;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@link ThingStatusInfo} represents status information of a thing which consists of
 * <ul>
 * <li>the status itself </il>
 * <li>detail of the status</il>
 * <li>and a description of the status</il>
 * </ul>
 *
 * @author Stefan Bußweiler - Initial contribution
 * @author Dennis Nobel - Added null checks
 * @author Andrew Fiddian-Green - Add thing status badge decorator style
 */
@NonNullByDefault
public class ThingStatusInfo {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final ThingStatus status;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final ThingStatusDetail statusDetail;

    private @Nullable String description;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final ThingStatusBadgeDecoratorStyle decoratorStyle;

    /**
     * Default constructor for deserialization e.g. by Gson.
     */
    protected ThingStatusInfo() {
        status = ThingStatus.UNKNOWN;
        statusDetail = ThingStatusDetail.NONE;
        decoratorStyle = ThingStatusBadgeDecoratorStyle.INFORMATION; // default style for backward compatibility
    }

    /**
     * Constructs a status info.
     *
     * @param status the status (must not be null)
     * @param statusDetail the detail of the status (must not be null)
     * @param description the description of the status
     * @param decoratorStyle the badge decorator style
     */
    public ThingStatusInfo(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description,
            ThingStatusBadgeDecoratorStyle decoratorStyle) {
        this.status = status;
        this.statusDetail = statusDetail;
        this.description = description;
        this.decoratorStyle = decoratorStyle;
    }

    /**
     * Gets the status itself.
     *
     * @return the status (not null)
     */
    public ThingStatus getStatus() {
        return status;
    }

    /**
     * Gets the detail of the status.
     *
     * @return the status detail (not null)
     */
    public ThingStatusDetail getStatusDetail() {
        return statusDetail;
    }

    /**
     * Gets the description of the status.
     *
     * @return the description
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Gets the badge decorator style.
     *
     * @return the badge decorator style
     */
    public ThingStatusBadgeDecoratorStyle getStatusBadgeDecoratorStyle() {
        return decoratorStyle;
    }

    private boolean useDescription() {
        return description != null && !description.isBlank();
    }

    private boolean useDecorator() {
        return status == ThingStatus.ONLINE && useDescription();
    }

    @Override
    public String toString() {
        return status + (statusDetail == ThingStatusDetail.NONE ? "" : " (" + statusDetail + ")")
                + (useDescription() ? ": " + description : "") + (useDecorator() ? " [" + decoratorStyle + "]" : "");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        String description = this.description;
        result = prime * result + (description == null ? 0 : description.hashCode());
        result = prime * result + status.hashCode();
        result = prime * result + statusDetail.hashCode();
        if (useDecorator()) {
            result = prime * result + decoratorStyle.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ThingStatusInfo other = (ThingStatusInfo) obj;
        if (status != other.status) {
            return false;
        }
        if (statusDetail != other.statusDetail) {
            return false;
        }
        if (!Objects.equals(description, other.description)) {
            return false;
        }
        if (useDecorator()) {
            return decoratorStyle == other.decoratorStyle;
        }
        return true;
    }
}
