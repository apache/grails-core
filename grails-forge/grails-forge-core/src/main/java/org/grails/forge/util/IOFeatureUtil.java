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
package org.grails.forge.util;

import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.template.URLTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Feature Utility class for IO operations.
 *
 * @author Puneet Behl
 * @since 6.0.0
 */
public class IOFeatureUtil {

    static final String JAR_EXTENSION = ".jar";

    /**
     * This method walk each file in the path and adds the {@link URLTemplate} to the {@link GeneratorContext}
     *
     * @param path The {@link Path} to the file
     * @param addTemplate The {@link java.util.function.BiFunction} to add {@link URLTemplate} to the {@link GeneratorContext}
     * @throws IOException if an I/O error occurs
     * @throws ProviderNotFoundException if a provider supporting the URI scheme is not installed
     */
    public static void walk(final Path path, BiFunction<String, URLTemplate, GeneratorContext> addTemplate) throws IOException, ProviderNotFoundException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        int sep = path.toString().lastIndexOf("!/");
        if (sep == -1) {
            walkFiles(path, classLoader, addTemplate);
        } else {
            final String jarPath = path.toString().substring(0, sep);
            if (jarPath.endsWith(JAR_EXTENSION)) {
                ClassLoader loader = null;
                try (FileSystem zipFs = FileSystems.newFileSystem(Paths.get(URI.create(jarPath)), loader)) {
                    walkFiles(zipFs.getPath(path.toString().substring(sep + 1)), classLoader, addTemplate);
                }
            }
        }
    }

    private static void walkFiles(final Path path, final ClassLoader classLoader, BiFunction<String, URLTemplate, GeneratorContext> addTemplate) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                paths.filter(Files::isRegularFile)
                        .map(file -> path.getParent().relativize(file).toString())
                        .forEach(relativePath -> addTemplate.apply(relativePath, new URLTemplate(relativePath, classLoader.getResource(relativePath))));
            }
        }
    }
}
