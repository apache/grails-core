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
package org.grails.web.pages;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.grails.gradle.plugin.views.RecordingForkedCompiler;

/**
 * Stands in for grails-web-gsp's forked page compiler, by its name, which {@code GroovyPageForkCompileTask}
 * starts; see {@link RecordingForkedCompiler}. Records as well the directories of generated pages
 * compiled with the source, the pages the task says it may leave out, and the encoding the pages
 * are read in. How the real compiler treats a generated page is tested in grails-web-gsp.
 */
public final class GroovyPageForkedCompiler {

    private GroovyPageForkedCompiler() {
    }

    public static void main(String[] args) throws IOException {
        List<String> optional = new ArrayList<>();
        String lists = System.getProperty("grails.views.gsp.optionalPages");
        if (lists != null) {
            for (String list : lists.split(File.pathSeparator)) {
                optional.addAll(Files.readAllLines(Paths.get(list), StandardCharsets.UTF_8));
            }
        }
        RecordingForkedCompiler.record(args[0], args[1],
                "generatedViews=" + System.getProperty("grails.views.gsp.generatedViewDirectories"),
                "optionalPages=" + String.join(",", optional),
                "encoding=" + args[7]);
    }
}
