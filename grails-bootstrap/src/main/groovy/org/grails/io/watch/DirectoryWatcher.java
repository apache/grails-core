/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.io.watch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to watch directories for changes.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class DirectoryWatcher extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryWatcher.class);

    private final AbstractDirectoryWatcher directoryWatcherDelegate;

    public static final String SVN_DIR_NAME = ".svn";

    /**
     * Constructor. Automatically selects the best means of watching for file system changes.
     */
    public DirectoryWatcher() {
        setDaemon(true);
        this.directoryWatcherDelegate = createDelegate();
    }

    /**
     * Selects the best available watcher implementation.
     *
     * <p>On macOS the native FSEvents based watcher is preferred, but it requires the optional
     * {@code io.methvin:directory-watcher} dependency. When that isn't available the JDK
     * {@link java.nio.file.WatchService} is used instead. Polling is only a last resort.</p>
     *
     * @return the watcher to delegate to
     */
    private static AbstractDirectoryWatcher createDelegate() {
        if (System.getProperty("os.name").equals("Mac OS X")) {
            AbstractDirectoryWatcher macOsWatcher = createMacOsWatcher();
            if (macOsWatcher != null) {
                return macOsWatcher;
            }
        }
        try {
            return new WatchServiceDirectoryWatcher();
        } catch (Throwable e) {
            LOG.warn("Could not create a WatchService based directory watcher. Falling back to PollingDirectoryWatcher.", e);
            return new PollingDirectoryWatcher();
        }
    }

    /**
     * @return the native macOS watcher, or {@code null} if it is unavailable
     */
    private static AbstractDirectoryWatcher createMacOsWatcher() {
        try {
            // MacOsWatchServiceDirectoryWatcher delegates to io.methvin's MacOSXListeningWatchService,
            // an optional dependency of this module. Probe for that class rather than for JNA: JNA is
            // frequently present transitively (Testcontainers, docker-java, ...) without the watcher
            // library, and using it as the signal sends those applications down a load that can't succeed.
            // A LinkageError means the class is present but its own dependencies (JNA) are not, which is
            // equally unusable, so treat both as simply unavailable.
            Class.forName("io.methvin.watchservice.MacOSXListeningWatchService");
        } catch (ClassNotFoundException | LinkageError e) {
            // Warn rather than debug: the JDK supplies no native WatchService on macOS, so the fallback
            // polls and file changes take seconds to be noticed. The message names the one dependency
            // that fixes that, and adding it silences this. The cause is debug detail, not the point.
            LOG.warn("Native macOS file event watching is unavailable, so the JDK WatchService is used instead, " +
                    "which polls on macOS. Add 'io.methvin:directory-watcher' to the classpath for event driven file watching.");
            LOG.debug("io.methvin.watchservice.MacOSXListeningWatchService could not be loaded.", e);
            return null;
        }
        try {
            return (AbstractDirectoryWatcher) Class.forName("org.grails.io.watch.MacOsWatchServiceDirectoryWatcher").getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            LOG.warn("Could not create the native macOS directory watcher. Falling back to the JDK WatchService.", e);
            return null;
        }
    }

    /**
     * Sets whether to stop the directory watcher
     *
     * @param active False if you want to stop watching
     */
    public void setActive(boolean active) {
        directoryWatcherDelegate.setActive(active);
    }

    /**
     * Sets the amount of time to sleep between checks
     *
     * @param sleepTime The sleep time
     */
    public void setSleepTime(long sleepTime) {
        directoryWatcherDelegate.setSleepTime(sleepTime);
    }

    /**
     * Adds a file listener that can react to change events
     *
     * @param listener The file listener
     */
    public void addListener(FileChangeListener listener) {
        directoryWatcherDelegate.addListener(listener);
    }

    /**
     * Removes a file listener from the current list
     *
     * @param listener The file listener
     */
    public void removeListener(FileChangeListener listener) {
        directoryWatcherDelegate.removeListener(listener);
    }

    /**
     * Adds a file to the watch list
     *
     * @param fileToWatch The file to watch
     */
    public void addWatchFile(File fileToWatch) {
        directoryWatcherDelegate.addWatchFile(fileToWatch);
    }

    /**
     * Adds a directory to watch for the given file and extensions.
     *
     * @param dir The directory
     * @param fileExtensions The extensions
     */
    public void addWatchDirectory(File dir, List<String> fileExtensions) {
        List<String> fileExtensionsWithoutDot = new ArrayList<>(fileExtensions.size());
        for (String fileExtension : fileExtensions) {
            fileExtensionsWithoutDot.add(removeStartingDotIfPresent(fileExtension));
        }
        directoryWatcherDelegate.addWatchDirectory(dir, fileExtensions);
    }

    /**
     * Adds a directory to watch for the given file. All files and subdirectories in the directory will be watched.
     *
     * @param dir The directory
     */
    public void addWatchDirectory(File dir) {
        addWatchDirectory(dir, "*");
    }

    /**
     * Adds a directory to watch for the given file and extensions.
     *
     * @param dir The directory
     * @param extension The extension
     */
    public void addWatchDirectory(File dir, String extension) {
        extension = removeStartingDotIfPresent(extension);
        List<String> fileExtensions = new ArrayList<>();
        if (extension != null && extension.length() > 0) {
            int i = extension.lastIndexOf('.');
            if (i > -1) {
                extension = extension.substring(i + 1);
            }
            fileExtensions.add(extension);
        }
        else {
            fileExtensions.add("*");
        }
        addWatchDirectory(dir, fileExtensions);
    }

    /**
     * Interface for FileChangeListeners
     */
    public static interface FileChangeListener {
        /**
         * Fired when a file changes
         *
         * @param file The file that changed
         */
        void onChange(File file);

        /**
         * Fired when a new file is created
         *
         * @param file The file that was created
         */
        void onNew(File file);
    }

    @Override
    public void run() {
        directoryWatcherDelegate.run();
    }

    private String removeStartingDotIfPresent(String extension) {
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        return extension;
    }
}
