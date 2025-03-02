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
package org.openhab.core.items;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.registry.Identifiable;
import org.openhab.core.types.Command;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescription;

/**
 * <p>
 * This interface defines the core features of an openHAB item.
 *
 * <p>
 * Item instances are used for all stateful services and are especially important for the {@link ItemRegistry}.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public interface Item extends Identifiable<String> {

    /**
     * returns the current state of the item
     *
     * @return the current state
     */
    State getState();

    /**
     * returns the current state of the item as a specific type
     *
     * @return the current state in the requested type or
     *         null, if state cannot be provided as the requested type
     */
    <T extends State> @Nullable T getStateAs(Class<T> typeClass);

    /**
     * Returns the previous state of the item.
     * 
     * @return the previous state of the item, or null if the item has never been changed.
     */
    @Nullable
    State getLastState();

    /**
     * Returns the time the item was last updated.
     *
     * @return the time the item was last updated, or null if the item has never been updated.
     */
    @Nullable
    ZonedDateTime getLastStateUpdate();

    /**
     * Returns the time the item was last changed.
     * 
     * @return the time the item was last changed, or null if the item has never been changed.
     */
    @Nullable
    ZonedDateTime getLastStateChange();

    /**
     * returns the name of the item
     *
     * @return the name of the item
     */
    String getName();

    /**
     * returns the item type as defined by {@link ItemFactory}s
     *
     * @return the item type
     */
    String getType();

    /**
     * <p>
     * This method provides a list of all data types that can be used to update the item state
     *
     * <p>
     * Imagine e.g. a dimmer device: It's status could be 0%, 10%, 50%, 100%, but also OFF or ON and maybe
     * UNDEFINED. So the accepted data types would be in this case {@link org.openhab.core.library.types.PercentType},
     * {@linkorg.openhab.core.library.types.OnOffType} and {@link org.openhab.core.types.UnDefType}
     *
     * <p>
     * The order of data types denotes the order of preference. So in case a state needs to be converted
     * in order to be accepted, it will be attempted to convert it to a type from top to bottom. Therefore
     * the type with the least information loss should be on top of the list - in the example above the
     * {@link org.openhab.core.library.types.PercentType} carries more information than the
     * {@linkorg.openhab.core.library.types.OnOffType}, hence it is listed first.
     *
     * @return a list of data types that can be used to update the item state
     */
    List<Class<? extends State>> getAcceptedDataTypes();

    /**
     * <p>
     * This method provides a list of all command types that can be used for this item
     *
     * <p>
     * Imagine e.g. a dimmer device: You could ask it to dim to 0%, 10%, 50%, 100%, but also to turn OFF or ON.
     * So the accepted command types would be in this case {@link org.openhab.core.library.types.PercentType},
     * {@linkorg.openhab.core.library.types.OnOffType}
     *
     *
     * @return a list of all command types that can be used for this item
     */
    List<Class<? extends Command>> getAcceptedCommandTypes();

    /**
     * Returns a list of the names of the groups this item belongs to.
     *
     * @return list of item group names
     */
    List<String> getGroupNames();

    /**
     * Returns a set of tags. If the item is not tagged, an empty set is returned.
     *
     * @return set of tags.
     */
    Set<String> getTags();

    /**
     * Returns the label of the item or null if no label is set.
     *
     * @return item label or null
     */
    @Nullable
    String getLabel();

    /**
     * Returns true if the item's tags contains the specific tag, otherwise false.
     *
     * @param tag a tag whose presence in the item's tags is to be tested.
     * @return true if the item's tags contains the specific tag, otherwise false.
     */
    boolean hasTag(String tag);

    /**
     * Returns the category of the item or null if no category is set.
     *
     * @return category or null
     */
    @Nullable
    String getCategory();

    /**
     * Returns the first provided state description (uses the default locale).
     * If options are defined on the channel, they are included in the returned state description.
     *
     * @return state description (can be null)
     */
    @Nullable
    StateDescription getStateDescription();

    /**
     * Returns the first provided state description for a given locale.
     * If options are defined on the channel, they are included in the returned state description.
     *
     * @param locale locale (can be null)
     * @return state description (can be null)
     */
    @Nullable
    StateDescription getStateDescription(@Nullable Locale locale);

    /**
     * Returns the {@link CommandDescription} for this item. In case no dedicated {@link CommandDescription} is
     * provided the {@link org.openhab.core.types.StateOption}s from the {@link StateDescription} will be served
     * as valid {@link org.openhab.core.types.CommandOption}s.
     *
     * @return the {@link CommandDescription} for the default locale (can be null).
     */
    default @Nullable CommandDescription getCommandDescription() {
        return getCommandDescription(null);
    }

    /**
     * Returns the {@link CommandDescription} for the given locale. In case no dedicated {@link CommandDescription} is
     * provided the {@link org.openhab.core.types.StateOption}s from the {@link StateDescription} will be served as
     * valid
     * {@link org.openhab.core.types.CommandOption}s.
     *
     * @param locale locale (can be null)
     * @return command description (can be null)
     */
    @Nullable
    CommandDescription getCommandDescription(@Nullable Locale locale);
}
