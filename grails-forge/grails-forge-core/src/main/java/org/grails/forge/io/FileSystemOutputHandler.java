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
package org.grails.forge.io;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.grails.forge.application.Project;
import org.grails.forge.template.Template;
import org.grails.forge.template.Writable;

public class FileSystemOutputHandler implements OutputHandler {

    File applicationDirectory;
    private final ConsoleOutput console;
    private final Instant lastModified;

    public FileSystemOutputHandler(Project project, boolean inplace, ConsoleOutput console) throws IOException {
        this.console = console;
        File baseDirectory = getDefaultBaseDirectory();
        if (inplace) {
            applicationDirectory = baseDirectory;
        } else {
            applicationDirectory = new File(baseDirectory, project.getName()).getCanonicalFile();
        }
        if (applicationDirectory.exists() && !inplace) {
            throw new IllegalArgumentException("Cannot create the project because the target directory already exists");
        }
        lastModified = OutputUtils.createLastModified(null);
    }

    public FileSystemOutputHandler(File directory, ConsoleOutput console) throws IOException {
        this.console = console;
        this.applicationDirectory = directory;
        lastModified = OutputUtils.createLastModified(null);
    }

    /**
     * Resolve the default base directory.
     * @return The base directory
     * @throws IOException If it cannot be resolved
     */
    public static File getDefaultBaseDirectory() throws IOException {
        String userDir = System.getProperty("user.dir");
        return new File(Objects.requireNonNullElse(userDir, "")).getCanonicalFile();
    }

    @Override
    public String getOutputLocation() {
        return applicationDirectory.getAbsolutePath();
    }

    @Override
    public boolean exists(String path) {
        return new File(applicationDirectory, path).exists();
    }

    @Override
    public void write(String path, Template contents) throws IOException {
        File targetFile = write(path, (Writable) contents);

        if (contents.isExecutable()) {
            if (!targetFile.setExecutable(true, true)) {
                console.warning("Failed to set " + path + " to be executable");
            }
        }
    }

    public File write(String path, Writable contents) throws IOException {
        if ('/' != File.separatorChar) {
            path = path.replace('/', File.separatorChar);
        }

        File targetFile = new File(applicationDirectory, path);
        Path base = applicationDirectory.toPath().toAbsolutePath().normalize();
        Path parent = targetFile.getParentFile().toPath().toAbsolutePath().normalize();

        // 1) Determine which parent directories don't exist yet
        List<Path> createdDirs = new ArrayList<>();
        if (!parent.startsWith(base)) {
            throw new IOException("Refusing to write outside base directory: " + parent);
        }
        Path p = base;
        for (Path seg : base.relativize(parent)) {
            p = p.resolve(seg);
            if (Files.notExists(p)) {
                createdDirs.add(p);
            }
        }

        // 2) Create the needed directories
        Files.createDirectories(parent);

        // 3) Write the file
        Files.deleteIfExists(targetFile.toPath());
        try (OutputStream os = Files.newOutputStream(targetFile.toPath())) {
            contents.write(os);
        }

        // Should we set a specific mtime (SOURCE_DATE_EPOCH)
        if (lastModified != null) {
            // 4) Set the file mtime
            FileTime mtime = FileTime.from(lastModified);
            Files.setLastModifiedTime(targetFile.toPath(), mtime);

            // 5) Set the mtime on only the directories we created
            // Do this after writing the file, since step 3 bumps the parent dir's mtime.
            for (int i = createdDirs.size() - 1; i >= 0; i--) {
                try {
                    Files.setLastModifiedTime(createdDirs.get(i), mtime);
                } catch (IOException ignore) {
                    // Non-fatal: some file systems may restrict touching dir times
                    console.warning("Could not set mtime for dir: " + createdDirs.get(i));
                }
            }
        }

        return targetFile;
    }

    @Override
    public void close() {

    }
}
