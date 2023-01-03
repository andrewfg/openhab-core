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
package org.openhab.core.automation.module.script.rulesupport.loader;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.script.ScriptEngine;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.OpenHAB;
import org.openhab.core.automation.module.script.ScriptDependencyTracker;
import org.openhab.core.automation.module.script.ScriptEngineContainer;
import org.openhab.core.automation.module.script.ScriptEngineManager;
import org.openhab.core.common.NamedThreadFactory;
import org.openhab.core.service.AbstractWatchService;
import org.openhab.core.service.ReadyMarker;
import org.openhab.core.service.ReadyMarkerFilter;
import org.openhab.core.service.ReadyService;
import org.openhab.core.service.StartLevelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link AbstractScriptFileWatcher} is default implementation for watching a directory for files. If a new/modified
 * file is detected, the script
 * is read and passed to the {@link ScriptEngineManager}. It needs to be sub-classed for actual use.
 *
 * @author Simon Merschjohann - Initial contribution
 * @author Kai Kreuzer - improved logging and removed thread pool
 * @author Jonathan Gilbert - added dependency tracking & per-script start levels; made reusable
 * @author Jan N. Klug - Refactored dependy tracking to script engine factories
 */
@NonNullByDefault
public abstract class AbstractScriptFileWatcher extends AbstractWatchService implements ReadyService.ReadyTracker,
        ScriptDependencyTracker.Listener, ScriptEngineManager.FactoryChangeListener {

    private static final long RECHECK_INTERVAL = 20;

    private final Logger logger = LoggerFactory.getLogger(AbstractScriptFileWatcher.class);

    private final ScriptEngineManager manager;
    private final ReadyService readyService;

    private @Nullable ScheduledExecutorService scheduler;
    private Supplier<ScheduledExecutorService> executorFactory;

    private final Set<ScriptFileReference> pending = ConcurrentHashMap.newKeySet();
    private final Set<ScriptFileReference> loaded = ConcurrentHashMap.newKeySet();

    private volatile int currentStartLevel = 0;

    public AbstractScriptFileWatcher(final ScriptEngineManager manager, final ReadyService readyService,
            final String fileDirectory) {
        super(OpenHAB.getConfigFolder() + File.separator + fileDirectory);
        this.manager = manager;
        this.readyService = readyService;
        this.executorFactory = () -> Executors
                .newSingleThreadScheduledExecutor(new NamedThreadFactory("scriptwatcher"));

        manager.addFactoryChangeListener(this);
        readyService.registerTracker(this, new ReadyMarkerFilter().withType(StartLevelService.STARTLEVEL_MARKER_TYPE));
    }

    @Override
    public void deactivate() {
        manager.removeFactoryChangeListener(this);
        readyService.unregisterTracker(this);

        ScheduledExecutorService localScheduler = scheduler;
        if (localScheduler != null) {
            localScheduler.shutdownNow();
            scheduler = null;
        }

        super.deactivate();
    }

    @Override
    public void activate() {
        File directory = new File(pathToWatch);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                logger.warn("Failed to create watched directory: {}", pathToWatch);
            }
        } else if (directory.isFile()) {
            logger.warn("Trying to watch directory {}, however it is a file", pathToWatch);
        }

        super.activate();
    }

    /**
     * Override the executor service. Can be used for testing.
     *
     * @param executorFactory supplier of ScheduledExecutorService
     */
    void setExecutorFactory(Supplier<ScheduledExecutorService> executorFactory) {
        this.executorFactory = executorFactory;
    }

    /**
     * Processes initial import of resources.
     *
     * @param rootDirectory the root directory to import initial resources from
     */
    private void importInitialResources(File rootDirectory) {
        if (rootDirectory.exists()) {
            File[] files = rootDirectory.listFiles();
            if (files != null) {
                Collection<ScriptFileReference> resources = new TreeSet<>();
                for (File f : files) {
                    if (!f.isHidden()) {
                        resources.addAll(collectResources(f));
                    }
                }
                resources.forEach(this::importFileWhenReady);
            }
        }
    }

    /**
     * Collects all resources from the specified file or directory,
     * possibly including subdirectories.
     *
     * The results will be sorted.
     *
     * @param file the file or directory to import resources from
     */
    private Collection<ScriptFileReference> collectResources(File file) {
        Collection<ScriptFileReference> resources = new TreeSet<>();
        if (!file.exists()) {
            return resources;
        }

        if (file.isDirectory()) {
            if (watchSubDirectories()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (!f.isHidden()) {
                            resources.addAll(collectResources(f));
                        }
                    }
                }
            }
        } else {
            try {
                URL url = file.toURI().toURL();
                resources.add(new ScriptFileReference(url));
            } catch (MalformedURLException e) {
                // can't happen for the 'file' protocol handler with a correctly formatted URI
                logger.debug("Can't create a URL", e);
            }
        }
        return resources;
    }

    @Override
    protected boolean watchSubDirectories() {
        return true;
    }

    @Override
    protected Kind<?> @Nullable [] getWatchEventKinds(@Nullable Path subDir) {
        return new Kind<?>[] { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY };
    }

    @Override
    protected void processWatchEvent(@Nullable WatchEvent<?> event, @Nullable Kind<?> kind, @Nullable Path path) {
        File file = path.toFile();
        if (!file.isHidden()) {
            try {
                if (ENTRY_DELETE.equals(kind)) {
                    if (file.isDirectory()) {
                        if (watchSubDirectories()) {
                            synchronized (this) {
                                String prefix = file.toURI().toURL().getPath();
                                Set<ScriptFileReference> toRemove = loaded.stream()
                                        .filter(f -> f.getScriptFileURL().getFile().startsWith(prefix))
                                        .collect(Collectors.toSet());
                                toRemove.forEach(this::removeFile);
                            }
                        }
                    } else {
                        removeFile(new ScriptFileReference(file.toURI().toURL()));
                    }
                }

                if (file.canRead() && (ENTRY_CREATE.equals(kind) || ENTRY_MODIFY.equals(kind))) {
                    collectResources(file).forEach(this::importFileWhenReady);
                }
            } catch (MalformedURLException e) {
                logger.error("malformed", e);
            }
        }
    }

    private void removeFile(ScriptFileReference ref) {
        dequeueUrl(ref);
        String scriptIdentifier = ref.getScriptIdentifier();

        manager.removeEngine(scriptIdentifier);
        loaded.remove(ref);
    }

    private synchronized void importFileWhenReady(ScriptFileReference ref) {
        if (loaded.contains(ref)) {
            this.removeFile(ref); // if already loaded, remove first
        }

        Optional<String> scriptType = ref.getScriptType();

        scriptType.ifPresent(type -> {
            if (ref.getStartLevel() <= currentStartLevel && manager.isSupported(type)) {
                importFile(ref);
            } else {
                enqueue(ref);
            }
        });
    }

    protected void importFile(ScriptFileReference ref) {
        if (createAndLoad(ref)) {
            loaded.add(ref);
        }
    }

    protected boolean createAndLoad(ScriptFileReference ref) {
        String fileName = ref.getScriptFileURL().getFile();
        Optional<String> scriptType = ref.getScriptType();
        assert scriptType.isPresent();

        try (InputStreamReader reader = new InputStreamReader(
                new BufferedInputStream(ref.getScriptFileURL().openStream()), StandardCharsets.UTF_8)) {
            logger.info("Loading script '{}'", fileName);

            String scriptIdentifier = ref.getScriptIdentifier();

            ScriptEngineContainer container = manager.createScriptEngine(scriptType.get(), scriptIdentifier);

            if (container != null) {
                container.getScriptEngine().put(ScriptEngine.FILENAME, fileName);
                manager.loadScript(container.getIdentifier(), reader);

                logger.debug("Script loaded: {}", fileName);
                return true;
            } else {
                logger.error("Script loading error, ignoring file: {}", fileName);
            }
        } catch (IOException e) {
            logger.error("Failed to load file '{}': {}", ref.getScriptFileURL().getFile(), e.getMessage());
        }

        return false;
    }

    private void enqueue(ScriptFileReference ref) {
        synchronized (pending) {
            pending.add(ref);
        }

        logger.debug("Enqueued {}", ref.getScriptIdentifier());
    }

    private void dequeueUrl(ScriptFileReference ref) {
        synchronized (pending) {
            pending.remove(ref);
        }

        logger.debug("Dequeued {}", ref.getScriptIdentifier());
    }

    private void checkFiles(int forLevel) {
        List<ScriptFileReference> newlySupported;

        synchronized (pending) {
            newlySupported = pending.stream()
                    .filter(ref -> manager.isSupported(ref.getScriptType().get()) && forLevel >= ref.getStartLevel())
                    .sorted().collect(Collectors.toList());
            pending.removeAll(newlySupported);
        }

        newlySupported.forEach(this::importFileWhenReady);
    }

    private synchronized void onStartLevelChanged(int newLevel) {
        int previousLevel = currentStartLevel;
        currentStartLevel = newLevel;

        if (previousLevel < StartLevelService.STARTLEVEL_MODEL) { // not yet started
            if (newLevel >= StartLevelService.STARTLEVEL_MODEL) { // start
                ScheduledExecutorService localScheduler = executorFactory.get();
                scheduler = localScheduler;
                localScheduler.submit(() -> importInitialResources(new File(pathToWatch)));
                localScheduler.scheduleWithFixedDelay(() -> checkFiles(currentStartLevel), 0, RECHECK_INTERVAL,
                        TimeUnit.SECONDS);
            }
        } else { // already started
            assert scheduler != null;
            if (newLevel < StartLevelService.STARTLEVEL_MODEL) { // stop
                scheduler.shutdown();
                scheduler = null;
            } else if (newLevel > previousLevel) {
                scheduler.submit(() -> checkFiles(newLevel));
            }
        }
    }

    @Override
    public void onDependencyChange(@Nullable String scriptId) {
        logger.debug("Reimporting {}...", scriptId);
        try {
            ScriptFileReference scriptFileReference = new ScriptFileReference(new URL(scriptId));
            if (loaded.contains(scriptFileReference)) {
                importFileWhenReady(scriptFileReference);
            }
        } catch (MalformedURLException ignored) {
        }
        logger.debug("Ignoring dependency change for {} as it is no file or not loaded by this file watcher", scriptId);
    }

    @Override
    public synchronized void onReadyMarkerAdded(ReadyMarker readyMarker) {
        int newLevel = Integer.parseInt(readyMarker.getIdentifier());

        if (newLevel > currentStartLevel) {
            onStartLevelChanged(newLevel);
        }
    }

    @Override
    @SuppressWarnings("PMD.EmptyWhileStmt")
    public synchronized void onReadyMarkerRemoved(ReadyMarker readyMarker) {
        int newLevel = Integer.parseInt(readyMarker.getIdentifier());

        if (currentStartLevel > newLevel) {
            while (newLevel-- > 0 && !readyService
                    .isReady(new ReadyMarker(StartLevelService.STARTLEVEL_MARKER_TYPE, Integer.toString(newLevel)))) {
            }
            onStartLevelChanged(newLevel);
        }
    }

    @Override
    public void factoryAdded(@Nullable String scriptType) {
    }

    @Override
    public void factoryRemoved(@Nullable String scriptType) {
        if (scriptType == null) {
            return;
        }
        loaded.stream().filter(ref -> scriptType.equals(ref.getScriptType().get())).forEach(this::importFileWhenReady);
    }
}