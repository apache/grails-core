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
package org.grails.gradle.plugin.views;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stands in for the forked view compilers, which the plugin's tests cannot depend on, and records
 * what a compile task starts it with - the directory of the views, the views it finds beneath it
 * with their sizes, as the real compilers find them by the directory whatever the task's filters,
 * the environment it is told to compile for and the one it would otherwise have inherited - to
 * {@code compiler.txt} in the destination directory, with whatever else the stand-in records of the
 * run. Leaves that file alone where it is already there, as the real page compiler leaves a page
 * alone whose class is newer than its source, and appends what each run found to {@code runs.txt}.
 * Each compiler's class names its {@code main} at this.
 */
public final class RecordingForkedCompiler {

    private RecordingForkedCompiler() {
    }

    /**
     * Records the run.
     *
     * @param source the directory of the views, as the task passes it
     * @param destination the destination directory, as the task passes it
     * @param recorded further {@code name=value} lines the stand-in records of what it was started with
     */
    public static void record(String source, String destination, String... recorded) throws IOException {
        Path target = Paths.get(destination);
        Files.createDirectories(target);
        Path compiled = target.resolve("compiler.txt");
        String environment = System.getProperty("grails.env");
        boolean keptEarlier = Files.exists(compiled);
        if (!keptEarlier) {
            List<String> lines = new ArrayList<>(List.of(
                    "source=" + source,
                    "pages=" + String.join(",", pagesBeneath(Paths.get(source))),
                    "grailsEnvironment=" + environment,
                    "inheritedEnvironment=" + System.getenv("GRAILS_ENV")));
            lines.addAll(List.of(recorded));
            Files.write(compiled, lines, StandardCharsets.UTF_8);
        }
        Files.write(target.resolve("runs.txt"),
                List.of("grailsEnvironment=" + environment + " keptEarlier=" + keptEarlier),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Each file beneath {@code source} by its path under it and its size, in order. */
    private static List<String> pagesBeneath(Path source) throws IOException {
        List<String> pages = new ArrayList<>();
        if (Files.isDirectory(source)) {
            try (Stream<Path> files = Files.walk(source)) {
                files.filter(Files::isRegularFile).sorted().forEach(file ->
                        pages.add(source.relativize(file).toString().replace(File.separatorChar, '/') + ":" + file.toFile().length()));
            }
        }
        return pages;
    }
}
