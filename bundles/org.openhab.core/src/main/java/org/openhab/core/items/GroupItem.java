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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.service.CommandDescriptionService;
import org.openhab.core.service.StateDescriptionService;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class GroupItem extends GenericItem implements StateChangeListener, MetadataAwareItem {

    public static final String TYPE = "Group";

    private final Logger logger = LoggerFactory.getLogger(GroupItem.class);

    protected @Nullable final Item baseItem;
    protected @Nullable GroupFunction function;
    protected final CopyOnWriteArrayList<Item> members;

    /**
     * Creates a plain GroupItem
     *
     * @param name name of the group
     */
    public GroupItem(String name) {
        this(name, null, null);
    }

    public GroupItem(String name, @Nullable Item baseItem) {
        // only baseItem but no function set -> use Equality
        this(name, baseItem, new GroupFunction.Equality());
    }

    /**
     * Creates a GroupItem with function
     *
     * @param name name of the group
     * @param baseItem type of items in the group
     * @param function function to calculate group status out of member status
     */
    public GroupItem(String name, @Nullable Item baseItem, @Nullable GroupFunction function) {
        super(TYPE, name);

        // we only allow GroupItem with BOTH, baseItem AND function set, or NONE of them set
        if (baseItem == null || function == null) {
            this.baseItem = null;
            this.function = null;
        } else {
            this.baseItem = baseItem;
            this.function = function;
        }

        members = new CopyOnWriteArrayList<>();
    }

    @Override
    public void dispose() {
        super.dispose();
        removeAllMembers();
    }

    /**
     * Returns the base item of this {@link GroupItem}. This method is only
     * intended to allow instance checks of the underlying BaseItem. It must
     * not be changed in any way.
     *
     * @return the base item of this GroupItem
     */
    public @Nullable Item getBaseItem() {
        return baseItem;
    }

    /**
     * Returns the function of this {@link GroupItem}.
     *
     * @return the function of this GroupItem
     */
    public @Nullable GroupFunction getFunction() {
        return function;
    }

    /**
     * Returns the direct members of this {@link GroupItem} regardless if these
     * members are {@link GroupItem}s as well.
     *
     * @return the direct members of this {@link GroupItem}
     */
    public Set<Item> getMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(members));
    }

    /**
     * Returns the direct members of this {@link GroupItem} and recursively all members of the potentially contained
     * {@link GroupItem}s as well. The {@link GroupItem}s itself aren't contained.
     * The returned items are unique.
     *
     * @return all members of this and all contained {@link GroupItem}s
     */
    public Set<Item> getAllMembers() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(getMembers((Item i) -> !(i instanceof GroupItem))));
    }

    /**
     * Returns the direct members of this {@link GroupItem} and recursively all members of the potentially contained
     * {@link GroupItem}s as well. The {@link GroupItem}s itself are contained if they can have a state.
     * The returned items are unique.
     *
     * @return all members of this and all contained {@link GroupItem}s
     */
    public Set<Item> getAllStateMembers() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(getMembers((Item i) -> !(i instanceof GroupItem) || hasOwnState((GroupItem) i))));
    }

    private void collectMembers(Collection<Item> allMembers, Collection<Item> members) {
        for (Item member : members) {
            if (allMembers.contains(member)) {
                continue;
            }
            allMembers.add(member);
            if (member instanceof GroupItem item) {
                collectMembers(allMembers, item.members);
            }
        }
    }

    /**
     * Retrieves ALL members of this group and filters it with the given Predicate
     *
     * @param filterItem Predicate with settings to filter member list
     * @return Set of member items filtered by filterItem
     */
    public Set<Item> getMembers(Predicate<Item> filterItem) {
        Set<Item> allMembers = new LinkedHashSet<>();
        collectMembers(allMembers, members);
        return allMembers.stream().filter(filterItem).collect(Collectors.toSet());
    }

    /**
     * Adds the given item to the members of this group item.
     *
     * @param item the item to be added (must not be null)
     * @throws IllegalArgumentException if the given item is null
     */
    public void addMember(Item item) {
        boolean added = members.addIfAbsent(item);

        // in case membership is constructed programmatically this sanitizes
        // the group names on the item:
        if (added && item instanceof GenericItem genericItem) {
            genericItem.addGroupName(getName());
        }
        registerStateListener(item);
    }

    private void registerStateListener(Item item) {
        if (item instanceof GenericItem genericItem) {
            genericItem.addStateChangeListener(this);
        }
    }

    private void unregisterStateListener(Item old) {
        if (old instanceof GenericItem genericItem) {
            genericItem.removeStateChangeListener(this);
        }
    }

    public void replaceMember(Item oldItem, Item newItem) {
        int index = members.indexOf(oldItem);
        if (index > -1) {
            Item old = members.set(index, newItem);
            unregisterStateListener(old);
        }
        registerStateListener(newItem);
    }

    /**
     * Removes the given item from the members of this group item.
     *
     * @param item the item to be removed (must not be null)
     * @throws IllegalArgumentException if the given item is null
     */
    public void removeMember(Item item) {
        members.remove(item);
        unregisterStateListener(item);
    }

    /**
     * Removes all items from the members of this group item.
     */
    public void removeAllMembers() {
        for (Item member : getMembers()) {
            unregisterStateListener(member);
        }
        members.clear();
    }

    /**
     * The accepted data types of a group item is the same as of the underlying base item.
     * If none is defined, the intersection of all sets of accepted data types of all group
     * members is used instead.
     *
     * @return the accepted data types of this group item
     */
    @Override
    public List<Class<? extends State>> getAcceptedDataTypes() {
        if (baseItem instanceof Item item) {
            return item.getAcceptedDataTypes();
        } else {
            List<Class<? extends State>> acceptedDataTypes = null;

            for (Item item : members) {
                if (acceptedDataTypes == null) {
                    acceptedDataTypes = new ArrayList<>(item.getAcceptedDataTypes());
                } else {
                    acceptedDataTypes.retainAll(item.getAcceptedDataTypes());
                }
            }
            return acceptedDataTypes == null ? List.of() : Collections.unmodifiableList(acceptedDataTypes);
        }
    }

    /**
     * The accepted command types of a group item is the same as of the underlying base item.
     * If none is defined, the intersection of all sets of accepted command types of all group
     * members is used instead.
     *
     * @return the accepted command types of this group item
     */
    @Override
    public List<Class<? extends Command>> getAcceptedCommandTypes() {
        if (baseItem instanceof Item item) {
            return item.getAcceptedCommandTypes();
        } else {
            List<Class<? extends Command>> acceptedCommandTypes = null;

            for (Item item : members) {
                if (acceptedCommandTypes == null) {
                    acceptedCommandTypes = new ArrayList<>(item.getAcceptedCommandTypes());
                } else {
                    acceptedCommandTypes.retainAll(item.getAcceptedCommandTypes());
                }
            }
            return acceptedCommandTypes == null ? List.of() : Collections.unmodifiableList(acceptedCommandTypes);
        }
    }

    public void send(Command command) {
        if (getAcceptedCommandTypes().contains(command.getClass())) {
            internalSend(command);
        } else {
            logger.warn("Command '{}' has been ignored for group '{}' as it is not accepted.", command, getName());
        }
    }

    @Override
    protected void internalSend(Command command) {
        EventPublisher eventPublisher = this.eventPublisher;
        if (eventPublisher != null) {
            for (Item member : members) {
                // try to send the command to the bus
                eventPublisher.post(ItemEventFactory.createCommandEvent(member.getName(), command));
            }
        }
    }

    @Override
    public <T extends State> @Nullable T getStateAs(Class<T> typeClass) {
        // if a group does not have a function it cannot have a state
        @Nullable
        T newState = null;
        if (function instanceof GroupFunction groupFunction) {
            newState = groupFunction.getStateAs(getStateMembers(getMembers()), typeClass);
        }

        Item baseItem = this.baseItem;
        if (newState == null && baseItem instanceof GenericItem item) {
            // we use the transformation method from the base item
            item.setState(state);
            newState = baseItem.getStateAs(typeClass);
        }
        if (newState == null) {
            newState = super.getStateAs(typeClass);
        }
        return newState;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        sb.append(" (");
        sb.append("Type=");
        sb.append(getClass().getSimpleName());
        sb.append(", ");
        if (getBaseItem() instanceof Item item) {
            sb.append("BaseType=");
            sb.append(item.getClass().getSimpleName());
            sb.append(", ");
        }
        sb.append("Members=");
        sb.append(members.size());
        sb.append(", ");
        sb.append("State=");
        sb.append(getState());
        sb.append(", ");
        sb.append("Label=");
        sb.append(getLabel());
        sb.append(", ");
        sb.append("Category=");
        sb.append(getCategory());
        if (!getTags().isEmpty()) {
            sb.append(", ");
            sb.append("Tags=[");
            sb.append(String.join(", ", getTags()));
            sb.append("]");
        }
        if (!getGroupNames().isEmpty()) {
            sb.append(", ");
            sb.append("Groups=[");
            sb.append(String.join(", ", getGroupNames()));
            sb.append("]");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void stateChanged(Item item, State oldState, State newState) {
    }

    @Override
    public void stateUpdated(Item item, State state) {
        State oldState = this.state;
        State newState = oldState;
        ItemStateConverter itemStateConverter = this.itemStateConverter;
        ZonedDateTime lastStateUpdate = this.lastStateUpdate;
        ZonedDateTime lastStateChange = this.lastStateChange;
        if (function instanceof GroupFunction groupFunction && baseItem != null && itemStateConverter != null) {
            State calculatedState = groupFunction.calculate(getStateMembers(getMembers()));
            newState = itemStateConverter.convertToAcceptedState(calculatedState, baseItem);
            setState(newState);
            sendGroupStateUpdatedEvent(item.getName(), newState, lastStateUpdate);
        }
        if (!oldState.equals(newState)) {
            sendGroupStateChangedEvent(item.getName(), newState, oldState, lastStateUpdate, lastStateChange);
        }
    }

    @Override
    public void setState(State state) {
        ZonedDateTime now = ZonedDateTime.now();
        State oldState = this.state;
        Item baseItem = this.baseItem;
        if (baseItem instanceof GenericItem item) {
            item.setState(state);
            this.state = baseItem.getState();
        } else {
            this.state = state;
        }
        notifyListeners(oldState, state);
        if (!oldState.equals(state)) {
            lastStateChange = now;
        }
        lastStateUpdate = now;
    }

    @Override
    public void setStateDescriptionService(@Nullable StateDescriptionService stateDescriptionService) {
        super.setStateDescriptionService(stateDescriptionService);
        if (baseItem instanceof GenericItem item) {
            item.setStateDescriptionService(stateDescriptionService);
        }
    }

    @Override
    public void setCommandDescriptionService(@Nullable CommandDescriptionService commandDescriptionService) {
        super.setCommandDescriptionService(commandDescriptionService);
        if (baseItem instanceof GenericItem item) {
            item.setCommandDescriptionService(commandDescriptionService);
        }
    }

    private void sendGroupStateUpdatedEvent(String memberName, State state, @Nullable ZonedDateTime lastStateUpdate) {
        EventPublisher eventPublisher1 = this.eventPublisher;
        if (eventPublisher1 != null) {
            eventPublisher1.post(
                    ItemEventFactory.createGroupStateUpdatedEvent(getName(), memberName, state, lastStateUpdate, null));
        }
    }

    private void sendGroupStateChangedEvent(String memberName, State newState, State oldState,
            @Nullable ZonedDateTime lastStateUpdate, @Nullable ZonedDateTime lastStateChange) {
        EventPublisher eventPublisher1 = this.eventPublisher;
        if (eventPublisher1 != null) {
            eventPublisher1.post(ItemEventFactory.createGroupStateChangedEvent(getName(), memberName, newState,
                    oldState, lastStateUpdate, lastStateChange));
        }
    }

    private Set<Item> getStateMembers(Set<Item> items) {
        Set<Item> result = new HashSet<>();
        collectStateMembers(result, items);

        // filter out group items w/o state. we had those in to detect cyclic membership.
        return result.stream().filter(i -> !isGroupItem(i) || hasOwnState((GroupItem) i)).collect(Collectors.toSet());
    }

    private void collectStateMembers(Set<Item> result, Set<Item> items) {
        for (Item item : items) {
            if (result.contains(item)) {
                continue;
            }
            if (!isGroupItem(item) || (isGroupItem(item) && hasOwnState((GroupItem) item))) {
                result.add(item);
            } else {
                result.add(item); // also add group items w/o state to detect cyclic membership.
                collectStateMembers(result, ((GroupItem) item).getMembers());
            }
        }
    }

    private boolean isGroupItem(Item item) {
        return item instanceof GroupItem;
    }

    private boolean hasOwnState(GroupItem item) {
        return item.getFunction() != null && item.getBaseItem() != null;
    }

    @Override
    public void addedMetadata(Metadata metadata) {
        if (baseItem instanceof MetadataAwareItem metadataAwareItem) {
            metadataAwareItem.addedMetadata(metadata);
        }
    }

    @Override
    public void updatedMetadata(Metadata oldMetadata, Metadata newMetadata) {
        if (baseItem instanceof MetadataAwareItem metadataAwareItem) {
            metadataAwareItem.updatedMetadata(oldMetadata, newMetadata);
        }
    }

    @Override
    public void removedMetadata(Metadata metadata) {
        if (baseItem instanceof MetadataAwareItem metadataAwareItem) {
            metadataAwareItem.removedMetadata(metadata);
        }
    }
}
