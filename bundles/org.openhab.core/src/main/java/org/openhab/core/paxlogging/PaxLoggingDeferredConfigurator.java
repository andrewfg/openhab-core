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
package org.openhab.core.paxlogging;

import java.lang.reflect.Method;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.ops4j.pax.logging.PaxLoggingService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a {@link FrameworkListener} component that sets the deferred Pax Logging configuration (if any) once the
 * OSGi framework has fully started and the console is stable.
 * <p>
 * Eliminates console "Terminal has been closed" bug that otherwise occurs on initial start up or after cache clean.
 * <p>
 * It expects that the {@code org.ops4j.pax.logging.log4j2.internal.PaxLoggingServiceImpl} will have had a fragment
 * override applied by openHAB core, and that the fragment is loaded, and has a {@code applyDeferredConfiguration()}
 * method. This is called via reflection to set the configuration that was provided by Karaf during startup, at a
 * later (deferred) time than it would otherwise have been set.
 * 
 * @author Andrew Fiddian-Green - Initial contribution
 */
@NonNullByDefault
@Component(immediate = true)
public class PaxLoggingDeferredConfigurator implements FrameworkListener {

    // method that we call in our PaxLoggingServiceImpl fragment override to set the deferred configuration
    private static final String TARGET_METHOD = "setDeferredConfiguration";

    private final Logger logger = LoggerFactory.getLogger(PaxLoggingDeferredConfigurator.class);
    private final BundleContext context;

    @Activate
    public PaxLoggingDeferredConfigurator(BundleContext context) {
        this.context = context;
        context.addFrameworkListener(this);
    }

    @Deactivate
    public void deactivate() {
        context.removeFrameworkListener(this);
    }

    @Override
    public void frameworkEvent(@Nullable FrameworkEvent event) {
        if (event == null || event.getType() != FrameworkEvent.STARTED) {
            return;
        }
        ServiceReference<PaxLoggingService> reference = context.getServiceReference(PaxLoggingService.class);
        if (reference == null) {
            logger.debug("PaxLoggingService reference missing");
            return;
        }
        PaxLoggingService service = context.getService(reference);
        if (service == null) {
            logger.debug("PaxLoggingService missing");
            return;
        }
        try {
            Method method = service.getClass().getMethod(TARGET_METHOD);
            method.invoke(service);
        } catch (NoSuchMethodException e) {
            logger.debug("PaxLoggingService.{}() missing", TARGET_METHOD);
        } catch (Exception e) {
            logger.debug("PaxLoggingService.{}() error", TARGET_METHOD, e);
        } finally {
            context.ungetService(reference);
        }
    }
}
