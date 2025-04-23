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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Implementation of a {@link AbstractDirectoryWatcher} that uses polling.
 * This implementation is used where {@link java.nio.WatchService} isn't available (pre Java 7).
 * @author Craig Andrews
 * @since 2.4
 * @see WatchServiceDirectoryWatcher
 * @see DirectoryWatcher
 */
class PollingDirectoryWatcher extends AbstractDirectoryWatcher {

    private Collection<String> extensions = new ConcurrentLinkedQueue<String>();

    private Map<File, Long> lastModifiedMap = new ConcurrentHashMap<File, Long>();
    private Map<File, Collection<String>> directoryToExtensionsMap = new ConcurrentHashMap<File, Collection<String>>();
    private Map<File, Long> directoryWatch = new ConcurrentHashMap<File, Long>();

	@Override
	public void run() {
        int count = 0;
        while (active) {
            Set<File> files = lastModifiedMap.keySet();
            for (File file : files) {
                long currentLastModified = file.lastModified();
                Long cachedTime = lastModifiedMap.get(file);
                if (currentLastModified > cachedTime) {
                    lastModifiedMap.put(file, currentLastModified);
                    fireOnChange(file);
                }
            }
            try {
                count++;
                if (count > 2) {
                    count = 0;
                    checkForNewFiles();
                }
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                // ignore
            }
        }
	}

	@Override
	public void addWatchFile(File fileToWatch) {
        lastModifiedMap.put(fileToWatch, fileToWatch.lastModified());
	}

	@Override
	public void addWatchDirectory(File dir, List<String> fileExtensions) {
		if(!isValidDirectoryToMonitor(dir)){
			return;
		}
        trackDirectoryExtensions(dir, fileExtensions);
        cacheFilesForDirectory(dir, fileExtensions, false);
	}

    private void trackDirectoryExtensions(File dir, List<String> fileExtensions) {
        Collection<String> existingExtensions = directoryToExtensionsMap.get(dir);
        if(existingExtensions == null) {
            directoryToExtensionsMap.put(dir, new ArrayList<String>(fileExtensions));
        }
        else {
            existingExtensions.addAll(fileExtensions);
        }
    }

    private void checkForNewFiles() {
        for (File directory : directoryWatch.keySet()) {
            final Long currentTimestamp = directoryWatch.get(directory);

            if (currentTimestamp < directory.lastModified()) {
                Collection<String> extensions = directoryToExtensionsMap.get(directory);
                if(extensions == null) {
                    extensions = this.extensions;
                }
                cacheFilesForDirectory(directory, extensions, true);
            }
        }
    }

    private void cacheFilesForDirectory(File directory, Collection<String> fileExtensions, boolean fireEvent) {
        addExtensions(fileExtensions);

        directoryWatch.put(directory, directory.lastModified());
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
        	if(isValidDirectoryToMonitor(file)) {
                cacheFilesForDirectory(file, fileExtensions, fireEvent);
        	}
            else if (isValidFileToMonitor(file, fileExtensions)) {
                if (!lastModifiedMap.containsKey(file) && fireEvent) {
                    fireOnNew(file);
                }
                lastModifiedMap.put(file, file.lastModified());
            }
        }
    }

    private void addExtensions(Collection<String> toAdd) {
        for (String extension : toAdd) {
            extension = removeStartingDotIfPresent(extension);
            if (!extensions.contains(extension)) {
                extensions.add(extension);
            }
        }
    }

    private String removeStartingDotIfPresent(String extension) {
        if (extension.startsWith(".")) {
            extension = extension.substring(1);
        }
        return extension;
    }

}
