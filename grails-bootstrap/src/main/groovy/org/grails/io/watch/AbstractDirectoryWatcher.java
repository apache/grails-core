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
import java.util.Collection;
import java.util.List;

/**
 * Backend for {@link DirectoryWatcher}
 *
 * @author Craig Andrews
 * @see WatchServiceDirectoryWatcher
 * @see PollingDirectoryWatcher
 * @see DirectoryWatcher
 * @since 2.4
 */
abstract class AbstractDirectoryWatcher implements Runnable {

    protected long sleepTime = 1000;
    protected volatile boolean active = true; //must be volatile as it's read by multiple threads and the value should be reflected in all of them
    private List<DirectoryWatcher.FileChangeListener> listeners = new ArrayList<DirectoryWatcher.FileChangeListener>();

    /**
     * Sets whether to stop the directory watcher
     *
     * @param active False if you want to stop watching
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Sets the amount of time to sleep between checks
     *
     * @param sleepTime The sleep time
     */
    public void setSleepTime(long sleepTime) {
        this.sleepTime = sleepTime;
    }

    /**
     * Adds a file listener that can react to change events
     *
     * @param listener The file listener
     */
    public void addListener(DirectoryWatcher.FileChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a file listener from the current list
     *
     * @param listener The file listener
     */
    public void removeListener(DirectoryWatcher.FileChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Adds a file to the watch list
     *
     * @param fileToWatch The file to watch
     */
    public abstract void addWatchFile(File fileToWatch);

    /**
     * Adds a directory to watch for the given file and extensions.
     * No String in the fileExtensions list can start with a dot (DirectoryWatcher guarantees that)
     *
     * @param dir            The directory
     * @param fileExtensions The extensions
     */
    public abstract void addWatchDirectory(File dir, List<String> fileExtensions);

    protected void fireOnChange(File file) {
        for (DirectoryWatcher.FileChangeListener listener : listeners) {
            listener.onChange(file);
        }
    }

    protected void fireOnNew(File file) {
        for (DirectoryWatcher.FileChangeListener listener : listeners) {
            listener.onNew(file);
        }
    }

    protected boolean isValidDirectoryToMonitor(File file) {
        return file.isDirectory() && !file.isHidden() && !file.getName().startsWith(".");
    }

    protected boolean isValidFileToMonitor(File file, Collection<String> fileExtensions) {
        String name = file.getName();
        String path = file.getAbsolutePath();
        boolean isSvnFile = path.indexOf(File.separator + DirectoryWatcher.SVN_DIR_NAME + File.separator) > 0;
        return !isSvnFile &&
                !file.isDirectory() &&
                !file.isHidden() &&
                !file.getName().startsWith(".") &&
                (fileExtensions.contains("*") || fileExtensions.contains(getFilenameExtension(name)));
    }

    /**
     * Extract the filename extension from the given path,
     * e.g. "mypath/myfile.txt" -> "txt".
     *
     * @param path the file path (may be {@code null})
     * @return the extracted filename extension, or {@code null} if none
     */
    public static String getFilenameExtension(String path) {
        if (path == null) {
            return null;
        }
        int extIndex = path.lastIndexOf(".");
        if (extIndex == -1) {
            return null;
        }
        return path.substring(extIndex + 1);
    }
}
