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
package org.openhab.core.config.core.internal.validation;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Utility class providing the {@link MessageKey}s for config description validation. The {@link MessageKey}
 * consists of a key to be used for internationalization and a general default text.
 *
 * @author Thomas Höfer - Initial contribution
 */
@NonNullByDefault
final class MessageKey {

    static final MessageKey PARAMETER_REQUIRED = new MessageKey("parameter_required", "The parameter is required.");

    static final MessageKey DATA_TYPE_VIOLATED = new MessageKey("data_type_violated",
            "The data type of the value ({0}) does not match with the type declaration ({1}) in the configuration description.");

    static final MessageKey MAX_VALUE_TXT_VIOLATED = new MessageKey("max_value_txt_violated",
            "The value must not consist of more than {0} characters.");
    static final MessageKey MAX_VALUE_NUMERIC_VIOLATED = new MessageKey("max_value_numeric_violated",
            "The value must not be greater than {0}.");

    static final MessageKey MIN_VALUE_TXT_VIOLATED = new MessageKey("min_value_txt_violated",
            "The value must not consist of less than {0} characters.");
    static final MessageKey MIN_VALUE_NUMERIC_VIOLATED = new MessageKey("min_value_numeric_violated",
            "The value must not be less than {0}.");

    static final MessageKey PATTERN_VIOLATED = new MessageKey("pattern_violated",
            "The value {0} does not match the pattern {1}.");

    static final MessageKey OPTIONS_VIOLATED = new MessageKey("options_violated",
            "The value {0} does not match allowed parameter options. Allowed options are: {1}");

    static final MessageKey MULTIPLE_LIMIT_VIOLATED = new MessageKey("multiple_limit_violated",
            "Only {0} elements are allowed but {1} are provided.");
    /** The key to be used for internationalization. */
    final String key;

    /** The default message. */
    final String defaultMessage;

    private MessageKey(String key, String defaultMessage) {
        this.key = key;
        this.defaultMessage = defaultMessage;
    }
}
