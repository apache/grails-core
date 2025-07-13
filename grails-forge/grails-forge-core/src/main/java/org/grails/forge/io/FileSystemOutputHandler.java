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

import org.grails.forge.application.Project;
import org.grails.forge.template.Template;
import org.grails.forge.template.Writable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

public class FileSystemOutputHandler implements OutputHandler {

    File applicationDirectory;
    private final ConsoleOutput console;

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
    }

    public FileSystemOutputHandler(File directory, ConsoleOutput console) throws IOException {
        this.console = console;
        this.applicationDirectory = directory;
    }

    /**
     * Resolve the default base directory.
     * @return The base directory
     * @throws IOException If it cannot be resolved
     */
    public static File getDefaultBaseDirectory() throws IOException {
        File baseDirectory;
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            baseDirectory = new File(userDir).getCanonicalFile();
        } else {
            baseDirectory = new File("").getCanonicalFile();
        }
        return baseDirectory;
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
        targetFile.getParentFile().mkdirs();
        targetFile.createNewFile();

        try (OutputStream os = Files.newOutputStream(targetFile.toPath())) {
            contents.write(os);
        }
        return targetFile;
    }

    @Override
    public void close() {

    }
}
