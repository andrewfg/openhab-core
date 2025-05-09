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
package org.openhab.core.internal.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.registry.AbstractRegistry;
import org.openhab.core.common.registry.Provider;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemNotUniqueException;
import org.openhab.core.items.ItemProvider;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.ItemStateConverter;
import org.openhab.core.items.ItemUtil;
import org.openhab.core.items.ManagedItemProvider;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataAwareItem;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.service.CommandDescriptionService;
import org.openhab.core.service.ReadyService;
import org.openhab.core.service.StateDescriptionService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main implementing class of the {@link ItemRegistry} interface. It
 * keeps track of all declared items of all item providers and keeps their
 * current state in memory. This is the central point where states are kept and
 * thus is a core part for all stateful services.
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Stefan Bußweiler - Migration to new event mechanism
 * @author Laurent Garnier - handle new DefaultStateDescriptionFragmentProvider
 */
@NonNullByDefault
@Component(immediate = true)
public class ItemRegistryImpl extends AbstractRegistry<Item, String, ItemProvider>
        implements ItemRegistry, RegistryChangeListener<Metadata> {

    private final Logger logger = LoggerFactory.getLogger(ItemRegistryImpl.class);

    private @Nullable StateDescriptionService stateDescriptionService;
    private @Nullable CommandDescriptionService commandDescriptionService;
    private final MetadataRegistry metadataRegistry;
    private final DefaultStateDescriptionFragmentProvider defaultStateDescriptionFragmentProvider;

    private @Nullable ItemStateConverter itemStateConverter;

    @Activate
    public ItemRegistryImpl(final @Reference MetadataRegistry metadataRegistry,
            final @Reference DefaultStateDescriptionFragmentProvider defaultStateDescriptionFragmentProvider) {
        super(ItemProvider.class);
        this.metadataRegistry = metadataRegistry;
        this.defaultStateDescriptionFragmentProvider = defaultStateDescriptionFragmentProvider;
    }

    @Activate
    protected void activate(final ComponentContext componentContext) {
        super.activate(componentContext.getBundleContext());
        metadataRegistry.addRegistryChangeListener(this);
    }

    @Override
    @Deactivate
    protected void deactivate() {
        metadataRegistry.removeRegistryChangeListener(this);
        super.deactivate();
    }

    @Override
    public Item getItem(String name) throws ItemNotFoundException {
        final Item item = get(name);
        if (item == null) {
            throw new ItemNotFoundException(name);
        } else {
            return item;
        }
    }

    @Override
    public Item getItemByPattern(String name) throws ItemNotFoundException, ItemNotUniqueException {
        Collection<Item> items = getItems(name);

        if (items.isEmpty()) {
            throw new ItemNotFoundException(name);
        }

        if (items.size() > 1) {
            throw new ItemNotUniqueException(name, items);
        }

        return items.iterator().next();
    }

    @Override
    public Collection<Item> getItems() {
        return getAll();
    }

    @Override
    public Collection<Item> getItemsOfType(String type) {
        Collection<Item> matchedItems = new ArrayList<>();

        for (Item item : getItems()) {
            if (item.getType().equals(type)) {
                matchedItems.add(item);
            }
        }

        return matchedItems;
    }

    @Override
    public Collection<Item> getItems(String pattern) {
        String regex = pattern.replace("?", ".?").replace("*", ".*?");
        Collection<Item> matchedItems = new ArrayList<>();

        for (Item item : getItems()) {
            if (item.getName().matches(regex)) {
                matchedItems.add(item);
            }
        }

        return matchedItems;
    }

    private void addToGroupItems(Item item, List<String> groupItemNames) {
        for (String groupName : groupItemNames) {
            try {
                if (getItem(groupName) instanceof GroupItem groupItem) {
                    groupItem.addMember(item);
                }
            } catch (ItemNotFoundException e) {
                // the group might not yet be registered, let's ignore this
            }
        }
    }

    private void replaceInGroupItems(Item oldItem, Item newItem, List<String> groupItemNames) {
        for (String groupName : groupItemNames) {
            try {
                if (getItem(groupName) instanceof GroupItem groupItem) {
                    groupItem.replaceMember(oldItem, newItem);
                }
            } catch (ItemNotFoundException e) {
                // the group might not yet be registered, let's ignore this
            }
        }
    }

    /**
     * An item should be initialized, which means that the event publisher is
     * injected and its implementation is notified that it has just been
     * created, so it can perform any task it needs to do after its creation.
     *
     * @param item the item to initialize
     * @throws IllegalArgumentException if the item has no valid name
     */
    private void initializeItem(Item item) throws IllegalArgumentException {
        ItemUtil.assertValidItemName(item.getName());

        injectServices(item);

        if (item instanceof GroupItem groupItem) {
            // fill group with its members
            addMembersToGroupItem(groupItem);
        }

        // add the item to all relevant groups
        addToGroupItems(item, item.getGroupNames());

        defaultStateDescriptionFragmentProvider.onItemAdded(item);
    }

    private void injectServices(Item item) {
        if (item instanceof GenericItem genericItem) {
            genericItem.setEventPublisher(getEventPublisher());
            genericItem.setStateDescriptionService(stateDescriptionService);
            genericItem.setCommandDescriptionService(commandDescriptionService);
            genericItem.setItemStateConverter(itemStateConverter);
        }
        if (item instanceof MetadataAwareItem metadataAwareItem) {
            metadataRegistry.stream().filter(m -> m.getUID().getItemName().equals(item.getName()))
                    .forEach(metadataAwareItem::addedMetadata);
        }
    }

    private void addMembersToGroupItem(GroupItem groupItem) {
        for (Item i : getItems()) {
            if (i.getGroupNames().contains(groupItem.getName())) {
                groupItem.addMember(i);
            }
        }
    }

    private void removeFromGroupItems(Item item, List<String> groupItemNames) {
        for (String groupName : groupItemNames) {
            try {
                if (getItem(groupName) instanceof GroupItem groupItem) {
                    groupItem.removeMember(item);
                }
            } catch (ItemNotFoundException e) {
                // the group might not yet be registered, let's ignore this
            }
        }
    }

    @Override
    protected void onAddElement(Item element) throws IllegalArgumentException {
        initializeItem(element);
    }

    @Override
    protected void onRemoveElement(Item element) {
        if (element instanceof GenericItem genericItem) {
            genericItem.dispose();
        }
        removeFromGroupItems(element, element.getGroupNames());
        defaultStateDescriptionFragmentProvider.onItemRemoved(element);
    }

    @Override
    protected void beforeUpdateElement(Item existingElement) {
        if (existingElement instanceof GenericItem genericItem) {
            genericItem.dispose();
        }
    }

    @Override
    protected void onUpdateElement(Item oldItem, Item item) {
        // don't use #initialize and retain order of items in groups:
        List<String> oldNames = oldItem.getGroupNames();
        List<String> newNames = item.getGroupNames();
        List<String> commonNames = oldNames.stream().filter(newNames::contains).toList();

        removeFromGroupItems(oldItem, oldNames.stream().filter(name -> !commonNames.contains(name)).toList());
        replaceInGroupItems(oldItem, item, commonNames);
        addToGroupItems(item, newNames.stream().filter(name -> !commonNames.contains(name)).toList());
        if (item instanceof GroupItem groupItem) {
            addMembersToGroupItem(groupItem);
        }
        injectServices(item);

        defaultStateDescriptionFragmentProvider.onItemRemoved(oldItem);
        defaultStateDescriptionFragmentProvider.onItemAdded(item);
    }

    @Override
    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setEventPublisher(EventPublisher eventPublisher) {
        super.setEventPublisher(eventPublisher);
        for (Item item : getItems()) {
            ((GenericItem) item).setEventPublisher(eventPublisher);
        }
    }

    @Override
    protected void unsetEventPublisher(EventPublisher eventPublisher) {
        super.unsetEventPublisher(eventPublisher);
        for (Item item : getItems()) {
            ((GenericItem) item).setEventPublisher(null);
        }
    }

    @Override
    @Reference
    protected void setReadyService(ReadyService readyService) {
        super.setReadyService(readyService);
    }

    @Override
    protected void unsetReadyService(ReadyService readyService) {
        super.unsetReadyService(readyService);
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setItemStateConverter(ItemStateConverter itemStateConverter) {
        this.itemStateConverter = itemStateConverter;
        for (Item item : getItems()) {
            ((GenericItem) item).setItemStateConverter(itemStateConverter);
        }
    }

    protected void unsetItemStateConverter(ItemStateConverter itemStateConverter) {
        this.itemStateConverter = null;
        for (Item item : getItems()) {
            ((GenericItem) item).setItemStateConverter(null);
        }
    }

    @Override
    public Collection<Item> getItemsByTag(String... tags) {
        List<Item> filteredItems = new ArrayList<>();
        for (Item item : getItems()) {
            if (itemHasTags(item, tags)) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    private boolean itemHasTags(Item item, String... tags) {
        for (String tag : tags) {
            if (!item.hasTag(tag)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Item> Collection<T> getItemsByTag(Class<T> typeFilter, String... tags) {
        Collection<T> filteredItems = new ArrayList<>();

        Collection<Item> items = getItemsByTag(tags);
        for (Item item : items) {
            if (typeFilter.isInstance(item)) {
                filteredItems.add((T) item);
            }
        }
        return filteredItems;
    }

    @Override
    public Collection<Item> getItemsByTagAndType(String type, String... tags) {
        List<Item> filteredItems = new ArrayList<>();
        for (Item item : getItemsOfType(type)) {
            if (itemHasTags(item, tags)) {
                filteredItems.add(item);
            }
        }
        return filteredItems;
    }

    @Override
    public @Nullable Item remove(String itemName, boolean recursive) {
        return ((ManagedItemProvider) getManagedProvider()
                .orElseThrow(() -> new IllegalStateException("ManagedProvider is not available")))
                .remove(itemName, recursive);
    }

    @Override
    protected void notifyListenersAboutAddedElement(Item element) {
        postEvent(ItemEventFactory.createAddedEvent(element));
        super.notifyListenersAboutAddedElement(element);
    }

    @Override
    protected void notifyListenersAboutRemovedElement(Item element) {
        postEvent(ItemEventFactory.createRemovedEvent(element));
        super.notifyListenersAboutRemovedElement(element);
    }

    @Override
    protected void notifyListenersAboutUpdatedElement(Item oldElement, Item element) {
        postEvent(ItemEventFactory.createUpdateEvent(element, oldElement));
        super.notifyListenersAboutUpdatedElement(oldElement, element);
    }

    @Override
    public void removed(Provider<Item> provider, Item element) {
        super.removed(provider, element);
        if (provider instanceof ManagedItemProvider) {
            // remove our metadata for that item
            logger.debug("Item {} was removed, trying to clean up corresponding metadata", element.getUID());
            metadataRegistry.removeItemMetadata(element.getName());
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void setStateDescriptionService(StateDescriptionService stateDescriptionService) {
        this.stateDescriptionService = stateDescriptionService;

        for (Item item : getItems()) {
            ((GenericItem) item).setStateDescriptionService(stateDescriptionService);
        }
    }

    public void unsetStateDescriptionService(StateDescriptionService stateDescriptionService) {
        this.stateDescriptionService = null;

        for (Item item : getItems()) {
            ((GenericItem) item).setStateDescriptionService(null);
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    public void setCommandDescriptionService(CommandDescriptionService commandDescriptionService) {
        this.commandDescriptionService = commandDescriptionService;

        for (Item item : getItems()) {
            ((GenericItem) item).setCommandDescriptionService(commandDescriptionService);
        }
    }

    public void unsetCommandDescriptionService(CommandDescriptionService commandDescriptionService) {
        this.commandDescriptionService = null;

        for (Item item : getItems()) {
            ((GenericItem) item).setCommandDescriptionService(null);
        }
    }

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void setManagedProvider(ManagedItemProvider provider) {
        super.setManagedProvider(provider);
    }

    protected void unsetManagedProvider(ManagedItemProvider provider) {
        super.unsetManagedProvider(provider);
    }

    @Override
    public void added(Metadata element) {
        String itemName = element.getUID().getItemName();
        Item item = get(itemName);
        if (item instanceof MetadataAwareItem metadataAwareItem) {
            metadataAwareItem.addedMetadata(element);
        }
    }

    @Override
    public void removed(Metadata element) {
        String itemName = element.getUID().getItemName();
        Item item = get(itemName);
        if (item instanceof MetadataAwareItem metadataAwareItem) {
            metadataAwareItem.removedMetadata(element);
        }
    }

    @Override
    public void updated(Metadata oldElement, Metadata element) {
        String itemName = element.getUID().getItemName();
        Item item = get(itemName);
        if (item instanceof MetadataAwareItem metadataAwareItem) {
            metadataAwareItem.updatedMetadata(oldElement, element);
        }
    }
}
