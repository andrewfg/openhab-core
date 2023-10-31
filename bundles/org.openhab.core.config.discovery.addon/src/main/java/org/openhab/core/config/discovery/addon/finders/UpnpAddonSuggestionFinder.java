/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.core.config.discovery.addon.finders;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jupnp.UpnpService;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.types.DeviceType;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.openhab.core.addon.AddonInfo;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a {@link UpnpAddonSuggestionFinder} for finding suggested Addons via UPnP.
 *
 * @author Andrew Fiddian-Green - Initial contribution
 */
@NonNullByDefault
@Component(service = AddonSuggestionFinder.class, name = UpnpAddonSuggestionFinder.SERVICE_NAME)
public class UpnpAddonSuggestionFinder extends BaseAddonSuggestionFinder implements RegistryListener {

    public static final String SERVICE_TYPE = "upnp";
    public static final String SERVICE_NAME = SERVICE_TYPE + ADDON_SUGGESTION_FINDER;

    private static final String DEVICE_TYPE = "deviceType";
    private static final String MANUFACTURER = "manufacturer";
    private static final String MANUFACTURER_URL = "manufacturerURL";
    private static final String MODEL_NAME = "modelName";
    private static final String MODEL_NUMBER = "modelNumber";
    private static final String MODEL_DESCRIPTION = "modelDescription";
    private static final String MODEL_URL = "modelURL";
    private static final String SERIAL_NUMBER = "serialNumber";
    private static final String FRIENDLY_NAME = "friendlyName";
    private static final String UDN = "udn";

    private static final Set<String> SUPPORTED_PROPERTIES = Set.of(DEVICE_TYPE, MANUFACTURER, MANUFACTURER_URL,
            MODEL_NAME, MODEL_NUMBER, MODEL_DESCRIPTION, MODEL_URL, SERIAL_NUMBER, FRIENDLY_NAME);

    private final Logger logger = LoggerFactory.getLogger(UpnpAddonSuggestionFinder.class);
    private final Map<String, RemoteDevice> devices = new ConcurrentHashMap<>();
    private final UpnpService upnpService;

    private static void logProperty(List<String> propertyList, String name, @Nullable String value) {
        if (value != null) {
            propertyList.add(name + "=" + value);
        }
    }

    @Activate
    public UpnpAddonSuggestionFinder(@Reference UpnpService upnpService) {
        this.upnpService = upnpService;
        this.upnpService.getRegistry().addListener(this);
    }

    public void addDevice(@Nullable RemoteDevice device) {
        if (device != null && device.getIdentity() != null && device.getIdentity().getUdn() != null) {
            String udn = device.getIdentity().getUdn().getIdentifierString();
            if (devices.put(udn, device) == null && logger.isTraceEnabled()) {
                List<String> properties = new ArrayList<>();
                logProperty(properties, UDN, udn);

                DeviceType devType = device.getType();
                if (devType != null) {
                    logProperty(properties, DEVICE_TYPE, devType.getType());
                }

                DeviceDetails devDetails = device.getDetails();
                if (devDetails != null) {
                    logProperty(properties, SERIAL_NUMBER, devDetails.getSerialNumber());
                    logProperty(properties, FRIENDLY_NAME, devDetails.getFriendlyName());

                    ManufacturerDetails mfrDetails = devDetails.getManufacturerDetails();
                    if (mfrDetails != null) {
                        URI mfrUri = mfrDetails.getManufacturerURI();
                        logProperty(properties, MANUFACTURER, mfrDetails.getManufacturer());
                        if (mfrUri != null) {
                            logProperty(properties, MANUFACTURER_URL, mfrUri.toString());
                        }
                    }

                    ModelDetails modDetails = devDetails.getModelDetails();
                    if (modDetails != null) {
                        URI modUri = modDetails.getModelURI();
                        logProperty(properties, MODEL_NAME, modDetails.getModelName());
                        logProperty(properties, MODEL_NUMBER, modDetails.getModelNumber());
                        logProperty(properties, MODEL_DESCRIPTION, modDetails.getModelDescription());
                        if (modUri != null) {
                            logProperty(properties, MODEL_URL, modUri.toString());
                        }
                    }
                }
                logger.trace("UPnP device properties={}", properties);
            }
        }
    }

    @Deactivate
    public void close() {
        devices.clear();
        super.close();
    }

    @Override
    public Set<AddonInfo> getSuggestedAddons() {
        Set<AddonInfo> result = new HashSet<>();
        addonCandidates.forEach(candidate -> {
            candidate.getDiscoveryMethods().stream().filter(method -> SERVICE_TYPE.equals(method.getServiceType()))
                    .forEach(method -> {
                        Map<String, Pattern> matchProperties = method.getMatchProperties().stream().collect(
                                Collectors.toMap(property -> property.getName(), property -> property.getPattern()));

                        Set<String> propNames = new HashSet<>(matchProperties.keySet());
                        propNames.removeAll(SUPPORTED_PROPERTIES);
                        if (!propNames.isEmpty()) {
                            logger.warn("Addon '{}' addon.xml file contains unsupported 'match-property' [{}]",
                                    candidate.getUID(), String.join(",", propNames));
                        } else {
                            devices.values().stream().forEach(device -> {
                                String deviceType = null;
                                String serialNumber = null;
                                String friendlyName = null;
                                String manufacturer = null;
                                String manufacturerURL = null;
                                String modelName = null;
                                String modelNumber = null;
                                String modelDescription = null;
                                String modelURL = null;

                                DeviceType devType = device.getType();
                                if (devType != null) {
                                    deviceType = devType.getType();
                                }

                                DeviceDetails devDetails = device.getDetails();
                                if (devDetails != null) {
                                    friendlyName = devDetails.getFriendlyName();
                                    serialNumber = devDetails.getSerialNumber();

                                    ManufacturerDetails mfrDetails = devDetails.getManufacturerDetails();
                                    if (mfrDetails != null) {
                                        URI mfrUri = mfrDetails.getManufacturerURI();
                                        manufacturer = mfrDetails.getManufacturer();
                                        manufacturerURL = mfrUri != null ? mfrUri.toString() : null;
                                    }

                                    ModelDetails modDetails = devDetails.getModelDetails();
                                    if (modDetails != null) {
                                        URI modUri = modDetails.getModelURI();
                                        modelName = modDetails.getModelName();
                                        modelDescription = modDetails.getModelDescription();
                                        modelNumber = modDetails.getModelNumber();
                                        modelURL = modUri != null ? modUri.toString() : null;
                                    }
                                }

                                if (propertyMatches(matchProperties, DEVICE_TYPE, deviceType)
                                        && propertyMatches(matchProperties, MANUFACTURER, manufacturer)
                                        && propertyMatches(matchProperties, MANUFACTURER_URL, manufacturerURL)
                                        && propertyMatches(matchProperties, MODEL_NAME, modelName)
                                        && propertyMatches(matchProperties, MODEL_NUMBER, modelNumber)
                                        && propertyMatches(matchProperties, MODEL_DESCRIPTION, modelDescription)
                                        && propertyMatches(matchProperties, MODEL_URL, modelURL)
                                        && propertyMatches(matchProperties, SERIAL_NUMBER, serialNumber)
                                        && propertyMatches(matchProperties, FRIENDLY_NAME, friendlyName)) {
                                    result.add(candidate);
                                    logger.debug("Addon '{}' will be suggested (via UPnP)", candidate.getUID());
                                }
                            });
                        }
                    });
        });
        return result;
    }

    @Override
    public void afterShutdown() {
    }

    @Override
    public void beforeShutdown(@Nullable Registry registry) {
    }

    @Override
    public void localDeviceAdded(@Nullable Registry registry, @Nullable LocalDevice localDevice) {
    }

    @Override
    public void localDeviceRemoved(@Nullable Registry registry, @Nullable LocalDevice localDevice) {
    }

    @Override
    public void remoteDeviceAdded(@Nullable Registry registry, @Nullable RemoteDevice remoteDevice) {
        addDevice(remoteDevice);
    }

    @Override
    public void remoteDeviceDiscoveryFailed(@Nullable Registry registry, @Nullable RemoteDevice remoteDevice,
            @Nullable Exception exception) {
    }

    @Override
    public void remoteDeviceDiscoveryStarted(@Nullable Registry registry, @Nullable RemoteDevice remoteDevice) {
    }

    @Override
    public void remoteDeviceRemoved(@Nullable Registry registry, @Nullable RemoteDevice remoteDevice) {
    }

    @Override
    public void remoteDeviceUpdated(@Nullable Registry registry, @Nullable RemoteDevice remoteDevice) {
        addDevice(remoteDevice);
    }
}
