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
package org.openhab.core.thing.binding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.events.ChannelDescriptionChangedEvent;
import org.openhab.core.thing.events.ChannelDescriptionChangedEvent.CommonChannelDescriptionField;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.types.StateOption;
import org.osgi.framework.BundleContext;

/**
 * Tests for {@link BaseDynamicStateDescriptionProvider}.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@NonNullByDefault
class BaseDynamicStateDescriptionProviderTest {

    private static final ThingTypeUID THING_TYPE_UID = new ThingTypeUID("binding:type");
    private static final ThingUID THING_UID = new ThingUID(THING_TYPE_UID, "id");
    private static final ChannelUID CHANNEL_UID = new ChannelUID(THING_UID, "channel");

    @Mock
    @NonNullByDefault({})
    EventPublisher eventPublisherMock;

    @Mock
    @NonNullByDefault({})
    ItemChannelLinkRegistry itemChannelLinkRegistryMock;

    class TestBaseDynamicStateDescriptionProvider extends BaseDynamicStateDescriptionProvider {

        public TestBaseDynamicStateDescriptionProvider() {
            this.bundleContext = mock(BundleContext.class);
            this.eventPublisher = eventPublisherMock;
            this.itemChannelLinkRegistry = itemChannelLinkRegistryMock;
        }
    }

    private @NonNullByDefault({}) BaseDynamicStateDescriptionProvider subject;

    @BeforeEach
    public void setup() {
        when(itemChannelLinkRegistryMock.getLinkedItemNames(CHANNEL_UID)).thenReturn(Set.of("item1", "item2"));

        subject = new TestBaseDynamicStateDescriptionProvider();
    }

    @Test
    public void setStatePatternPublishesEvent() {
        subject.setStatePattern(CHANNEL_UID, "%s");

        ArgumentCaptor<Event> capture = ArgumentCaptor.forClass(Event.class);
        verify(eventPublisherMock, times(1)).post(capture.capture());

        Event event = capture.getValue();
        assertInstanceOf(ChannelDescriptionChangedEvent.class, event);
        ChannelDescriptionChangedEvent cdce = (ChannelDescriptionChangedEvent) event;
        assertEquals(CommonChannelDescriptionField.PATTERN, cdce.getField());

        // check the event is not published again
        subject.setStatePattern(CHANNEL_UID, "%s");

        verify(eventPublisherMock, times(1)).post(capture.capture());
    }

    @Test
    public void setStateOptionsPublishesEvent() {
        subject.setStateOptions(CHANNEL_UID, List.of(new StateOption("offline", "Offline")));

        ArgumentCaptor<Event> capture = ArgumentCaptor.forClass(Event.class);
        verify(eventPublisherMock, times(1)).post(capture.capture());

        Event event = capture.getValue();
        assertInstanceOf(ChannelDescriptionChangedEvent.class, event);
        ChannelDescriptionChangedEvent cdce = (ChannelDescriptionChangedEvent) event;
        assertEquals(CommonChannelDescriptionField.STATE_OPTIONS, cdce.getField());

        // check the event is not published again
        subject.setStateOptions(CHANNEL_UID, List.of(new StateOption("offline", "Offline")));

        verify(eventPublisherMock, times(1)).post(capture.capture());
    }
}
