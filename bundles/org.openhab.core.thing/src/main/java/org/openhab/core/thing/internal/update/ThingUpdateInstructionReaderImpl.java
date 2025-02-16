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
package org.openhab.core.thing.internal.update;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.config.core.ConfigDescriptionRegistry;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.openhab.core.thing.internal.update.dto.XmlAddChannel;
import org.openhab.core.thing.internal.update.dto.XmlInstructionSet;
import org.openhab.core.thing.internal.update.dto.XmlRemoveChannel;
import org.openhab.core.thing.internal.update.dto.XmlThingType;
import org.openhab.core.thing.internal.update.dto.XmlUpdateChannel;
import org.openhab.core.thing.internal.update.dto.XmlUpdateDescriptions;
import org.openhab.core.thing.type.ChannelTypeRegistry;
import org.openhab.core.util.BundleResolver;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ThingUpdateInstructionReaderImpl} is an implementation of {@link ThingUpdateInstructionReader}
 *
 * @author Jan N. Klug - Initial contribution
 */
@Component(service = ThingUpdateInstructionReader.class)
@NonNullByDefault
public class ThingUpdateInstructionReaderImpl implements ThingUpdateInstructionReader {
    private final Logger logger = LoggerFactory.getLogger(ThingUpdateInstructionReaderImpl.class);
    private final BundleResolver bundleResolver;
    private final ChannelTypeRegistry channelTypeRegistry;
    private final ConfigDescriptionRegistry configDescriptionRegistry;

    @Activate
    public ThingUpdateInstructionReaderImpl(@Reference BundleResolver bundleResolver,
            @Reference ChannelTypeRegistry channelTypeRegistry,
            @Reference ConfigDescriptionRegistry configDescriptionRegistry) {
        this.bundleResolver = bundleResolver;
        this.channelTypeRegistry = channelTypeRegistry;
        this.configDescriptionRegistry = configDescriptionRegistry;
    }

    @Override
    public Map<UpdateInstructionKey, List<ThingUpdateInstruction>> readForFactory(ThingHandlerFactory factory) {
        Bundle bundle = bundleResolver.resolveBundle(factory.getClass());
        if (bundle == null) {
            logger.error(
                    "Could not get bundle for '{}', thing type updates will fail. If this occurs outside of tests, it is a bug.",
                    factory.getClass());
            return Map.of();
        }

        Map<UpdateInstructionKey, List<ThingUpdateInstruction>> updateInstructions = new HashMap<>();
        Enumeration<URL> entries = bundle.findEntries("OH-INF/update", "*.xml", true);
        if (entries != null) {
            while (entries.hasMoreElements()) {
                URL url = entries.nextElement();
                try {
                    JAXBContext context = JAXBContext.newInstance(XmlUpdateDescriptions.class);
                    Unmarshaller u = context.createUnmarshaller();
                    XmlUpdateDescriptions updateDescriptions = (XmlUpdateDescriptions) u.unmarshal(url);

                    for (XmlThingType thingType : updateDescriptions.getThingType()) {
                        ThingTypeUID thingTypeUID = new ThingTypeUID(thingType.getUid());
                        UpdateInstructionKey key = new UpdateInstructionKey(factory, thingTypeUID);
                        List<ThingUpdateInstruction> instructions = new ArrayList<>();
                        List<XmlInstructionSet> instructionSets = thingType.getInstructionSet().stream()
                                .sorted(Comparator.comparing(XmlInstructionSet::getTargetVersion)).toList();
                        for (XmlInstructionSet instructionSet : instructionSets) {
                            int targetVersion = instructionSet.getTargetVersion();
                            for (Object instruction : instructionSet.getInstructions()) {
                                if (instruction instanceof XmlAddChannel addChannelType) {
                                    instructions.add(new UpdateChannelInstructionImpl(targetVersion, addChannelType,
                                            channelTypeRegistry, configDescriptionRegistry));
                                } else if (instruction instanceof XmlUpdateChannel updateChannelType) {
                                    instructions.add(new UpdateChannelInstructionImpl(targetVersion, updateChannelType,
                                            channelTypeRegistry, configDescriptionRegistry));
                                } else if (instruction instanceof XmlRemoveChannel removeChannelType) {
                                    instructions
                                            .add(new RemoveChannelInstructionImpl(targetVersion, removeChannelType));
                                } else {
                                    logger.warn("Instruction type '{}' is unknown.", instruction.getClass());
                                }
                            }
                        }
                        updateInstructions.put(key, instructions);
                    }
                    logger.trace("Reading update instructions from '{}'", url.getPath());
                } catch (IllegalArgumentException | JAXBException e) {
                    logger.warn("Failed to parse update instructions from '{}':", url, e);
                }
            }
        }

        return updateInstructions;
    }
}
