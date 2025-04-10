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
package org.openhab.core.addon.marketplace.internal.community;

import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.google.gson.annotations.SerializedName;

/**
 * This is a {@link SerializedNameAnnotationIntrospector}, which processes SerializedName annotations.
 *
 * @author Boris Krivonog - Initial contribution
 *
 */
@NonNullByDefault
final class SerializedNameAnnotationIntrospector extends AnnotationIntrospector {
    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    @NonNullByDefault({})
    public PropertyName findNameForDeserialization(Annotated annotated) {
        return Optional.ofNullable(annotated.getAnnotation(SerializedName.class)).map(s -> new PropertyName(s.value()))
                .orElseGet(() -> super.findNameForDeserialization(annotated));
    }

    @Override
    @NonNullByDefault({})
    public List<PropertyName> findPropertyAliases(Annotated annotated) {
        return Optional.ofNullable(annotated.getAnnotation(SerializedName.class))
                .map(s -> Stream.of(s.alternate()).map(PropertyName::new).toList())
                .orElseGet(() -> super.findPropertyAliases(annotated));
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }
}
