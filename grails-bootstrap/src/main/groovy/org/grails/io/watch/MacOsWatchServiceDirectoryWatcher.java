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

import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a {@link AbstractDirectoryWatcher} that uses {@link java.nio.WatchService}.
 * This implementation is used for Java 7 and later.
 * @author Eric Helgeson
 * @author David Estes
 * @since 3.2
 * @see DirectoryWatcher
 */
class MacOsWatchServiceDirectoryWatcher extends AbstractDirectoryWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(MacOsWatchServiceDirectoryWatcher.class);
    private Map<WatchKey, List<String>> watchKeyToExtensionsMap = new ConcurrentHashMap<WatchKey, List<String>>();
    private Set<Path> individualWatchedFiles = new HashSet<Path>();

    private final WatchService watchService;

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    public MacOsWatchServiceDirectoryWatcher(){
        try {
            watchService = new MacOSXListeningWatchService();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (active) {
            try {
                WatchKey watchKey;
                try {
                    watchKey = watchService.take();
                } catch (InterruptedException x) {
                    return;
                }
                for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
                    WatchEvent.Kind<?> kind = watchEvent.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        // TODO how is this supposed to be handled? I think the best thing to do is ignore it, but I'm not positive
                        LOG.warn("WatchService Overflow occurred");
                        continue;
                    }
                    WatchEvent<Path> pathWatchEvent = cast(watchEvent);
                    Path child = pathWatchEvent.context();
                    File childFile = child.toFile();
                    if(individualWatchedFiles.contains(child) || individualWatchedFiles.contains(child.normalize())){
                        if(kind == StandardWatchEventKinds.ENTRY_CREATE){
                            fireOnNew(childFile);
                        }else if(kind == StandardWatchEventKinds.ENTRY_MODIFY){
                            fireOnChange(childFile);
                        }else if(kind == StandardWatchEventKinds.ENTRY_DELETE){
                            // do nothing... there's no way to communicate deletions
                        }
                    }else{
                        List<String> fileExtensions = watchKeyToExtensionsMap.get(watchKey);
                        if(fileExtensions==null){
                            // this event didn't match a file in individualWatchedFiles so it's a not an individual file we're interested in
                            // this event also didn't match a directory that we're interested in (if it did, fileExtentions wouldn't be null)
                            // so it must be event for a file we're not interested in. An example of how this can happen is:
                            // there's a directory with files in it like this:
                            // /images/a.png
                            // /images/b.png
                            // by using the addWatchFile method, /images/a.png is watched.
                            // Now, /images/b.png is changed. Because java.nio.file.WatchService watches directories, it gets a WatchEvent
                            // for /images/b.png. But we aren't interested in that.
                            LOG.debug("WatchService received an event for a file/directory that it's not interested in.");
                        }else{
                            if(kind==StandardWatchEventKinds.ENTRY_CREATE){
                                // new directory created, so watch its contents
                                addWatchDirectory(child,fileExtensions);
                                if(childFile.isDirectory() && childFile.exists()) {
                                    final File[] files = childFile.listFiles();
                                    if(files != null) {
                                        for (File newFile : files) {
                                            if(isValidFileToMonitor(newFile, fileExtensions)) {
                                                fireOnNew(newFile);
                                            }
                                        }
                                    }
                                }
                            }
                            if(isValidFileToMonitor(childFile,fileExtensions)){
                                if(kind == StandardWatchEventKinds.ENTRY_CREATE){
                                    fireOnNew(childFile);
                                }else if(kind == StandardWatchEventKinds.ENTRY_MODIFY){
                                    fireOnChange(childFile);
                                }else if(kind == StandardWatchEventKinds.ENTRY_DELETE){
                                    // do nothing... there's no way to communicate deletions
                                }
                            }
                        }
                    }
                }
                watchKey.reset();
            } catch (Exception e) {
                LOG.error(e.toString());
                // ignore
            }
        }
        try {
            watchService.close();
        } catch (IOException e) {
            LOG.debug("Exception while closing watchService", e);
        }
    }

    @Override
    public void addWatchFile(File fileToWatch) {
        if(!isValidFileToMonitor(fileToWatch, Arrays.asList("*"))) return;
        try {
            if(!fileToWatch.exists()) return;
            Path pathToWatch = fileToWatch.toPath().toAbsolutePath();
            individualWatchedFiles.add(pathToWatch);
            WatchablePath watchPath = new WatchablePath(pathToWatch);
            Kind[] events = new Kind[] { StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY };
            watchPath.register(watchService, events);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addWatchDirectory(File dir, final List<String> fileExtensions) {
        Path dirPath = dir.toPath();
        addWatchDirectory(dirPath, fileExtensions);
    }

    private void addWatchDirectory(Path dir, final List<String> fileExtensions) {
        if(!isValidDirectoryToMonitor(dir.toFile())){
            return;
        }
        try {
            //add the subdirectories too
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
                {
                    if(!isValidDirectoryToMonitor(dir.toFile())){
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    WatchablePath watchPath = new WatchablePath(dir);
                    Kind[] events = new Kind[] { StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY };
                    WatchKey watchKey = watchPath.register(watchService, events);
                    final List<String> originalFileExtensions = watchKeyToExtensionsMap.get(watchKey);
                    if(originalFileExtensions==null){
                        watchKeyToExtensionsMap.put(watchKey, fileExtensions);
                    }else{
                        final HashSet<String> newFileExtensions = new HashSet<String>(originalFileExtensions);
                        newFileExtensions.addAll(fileExtensions);
                        watchKeyToExtensionsMap.put(watchKey, Collections.unmodifiableList(new ArrayList(newFileExtensions)));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
