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

import io.micronaut.core.util.StringUtils;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.grails.forge.application.Project;
import org.grails.forge.template.Template;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ZipOutputHandler implements OutputHandler {

    private final ZipArchiveOutputStream zipOutputStream;
    private final File zip;
    private final String directory;

    public ZipOutputHandler(Project project) throws IOException {
        File baseDirectory = new File(".").getCanonicalFile();
        Path zipPath = Paths.get(baseDirectory.getPath(), project.getName() + ".zip");
        zip = zipPath.toAbsolutePath().normalize().toFile();
        if (zip.exists()) {
            throw new IllegalArgumentException("Cannot create the project because the target zip file already exists");
        }
        zip.createNewFile();
        zipOutputStream = new ZipArchiveOutputStream(Files.newOutputStream(zip.toPath()));
        directory = project.getName();
    }

    public ZipOutputHandler(OutputStream outputStream) {
        zip = null;
        zipOutputStream = new ZipArchiveOutputStream(outputStream);
        directory = null;
    }

    public ZipOutputHandler(String projectName, OutputStream outputStream) {
        zip = null;
        zipOutputStream = new ZipArchiveOutputStream(outputStream);
        directory = projectName;
    }

    @Override
    public String getOutputLocation() {
        if (zip == null) {
            return null;
        } else {
            return zip.getAbsolutePath();
        }
    }

    @Override
    public boolean exists(String path) {
        return false;
    }

    @Override
    public void write(String path, Template contents) throws IOException {
        ZipArchiveEntry zipEntry = new ZipArchiveEntry(directory != null ? StringUtils.prependUri(directory, path) : path);
        if (contents.isExecutable()) {
            zipEntry.setUnixMode(UnixStat.FILE_FLAG | 0755);
        }
        zipOutputStream.putArchiveEntry(zipEntry);
        contents.write(zipOutputStream);
        zipOutputStream.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        zipOutputStream.finish();
        zipOutputStream.close();
    }
}
