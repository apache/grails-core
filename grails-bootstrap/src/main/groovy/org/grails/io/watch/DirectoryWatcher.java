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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        AbstractDirectoryWatcher directoryWatcherDelegate;
        try {
            if(System.getProperty("os.name").equals("Mac OS X")) {
                Boolean jnaAvailable = false;
                try {
                    Class.forName( "com.sun.jna.Pointer" );
                    jnaAvailable = true;
                } catch( ClassNotFoundException e ) {
                    if(LOG.isWarnEnabled()) {
                        LOG.warn("Error Initializing Native OS X File Event Watcher. Add JNA to classpath for Faster File Watching performance.");    
                    }
                    
                }
                if(jnaAvailable) {
                    directoryWatcherDelegate = (AbstractDirectoryWatcher) Class.forName("org.grails.io.watch.MacOsWatchServiceDirectoryWatcher").newInstance();
                } else {
                    directoryWatcherDelegate = (AbstractDirectoryWatcher) Class.forName("org.grails.io.watch.WatchServiceDirectoryWatcher").newInstance();                    
                }
            } else {
                directoryWatcherDelegate = (AbstractDirectoryWatcher) Class.forName("org.grails.io.watch.WatchServiceDirectoryWatcher").newInstance();                
            }
		} catch (Throwable e) {
			LOG.info("Exception while trying to load WatchServiceDirectoryWatcher (this is probably Java 6 and WatchService isn't available). Falling back to PollingDirectoryWatcher.", e);
	        directoryWatcherDelegate = new PollingDirectoryWatcher();
		}
        this.directoryWatcherDelegate = directoryWatcherDelegate;
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
    	List<String> fileExtensionsWithoutDot = new ArrayList<String>(fileExtensions.size());
    	for(String fileExtension : fileExtensions){
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
        List<String> fileExtensions = new ArrayList<String>();
        if (extension != null && extension.length() > 0) {
            int i = extension.lastIndexOf('.');
            if(i > -1) {
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
