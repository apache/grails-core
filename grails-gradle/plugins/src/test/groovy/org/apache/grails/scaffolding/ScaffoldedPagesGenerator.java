/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.scaffolding;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stands in for grails-scaffolding's generator, which the plugin's tests cannot depend on, and
 * records what {@code GenerateScaffoldedViewsTask} hands it: for each domain class in the plan,
 * every template in each planned directory is copied to
 * {@code grails-scaffolded/<domain class>/<directory>/<template path>} under the output directory,
 * where each directory came from to {@code origins/<directory>}, and the page encoding to
 * {@code encoding.txt}; each page is reported as written, and a template whose text contains
 * {@code FAIL} as one that could not be expanded instead. How the real generator expands and names a page is tested in
 * grails-scaffolding.
 */
public final class ScaffoldedPagesGenerator {

    /** The version of the exchange the task speaks, so that it runs this. */
    public static final int PROTOCOL = org.grails.gradle.plugin.scaffolding.GenerateScaffoldedViewsTask.GENERATOR_PROTOCOL;

    private ScaffoldedPagesGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path output = Paths.get(args[2]);
        List<String> report = new ArrayList<>();
        Files.createDirectories(output.resolve("origins"));
        Files.write(output.resolve("encoding.txt"), args[3].getBytes(StandardCharsets.UTF_8));
        for (String line : Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", 2);
            Files.write(output.resolve("origins").resolve(Paths.get(fields[0]).getFileName()), fields[1].getBytes(StandardCharsets.UTF_8));
        }
        for (String line : Files.readAllLines(Paths.get(args[0]), StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t");
            for (int dir = 1; dir < fields.length; dir++) {
                Path templates = Paths.get(fields[dir]);
                List<Path> files;
                try (Stream<Path> walk = Files.walk(templates)) {
                    files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
                }
                for (Path file : files) {
                    String path = templates.relativize(file).toString().replace(File.separatorChar, '/');
                    if (new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("FAIL")) {
                        report.add("failed\t" + templates + "\t" + fields[0] + "\tthe template says FAIL");
                        continue;
                    }
                    String page = "grails-scaffolded/" + fields[0] + "/" + templates.getFileName() + "/" + path;
                    Path target = output.resolve(page);
                    Files.createDirectories(target.getParent());
                    Files.write(target, Files.readAllBytes(file));
                    report.add("page\t" + templates + "\t" + page);
                }
            }
        }
        Files.write(Paths.get(args[4]), report, StandardCharsets.UTF_8);
    }
}
