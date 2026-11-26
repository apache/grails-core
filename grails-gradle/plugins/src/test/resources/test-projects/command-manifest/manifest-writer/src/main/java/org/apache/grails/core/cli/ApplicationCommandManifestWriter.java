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
package org.apache.grails.core.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stands in for the writer of grails-core-cli: records the arguments it was given and what its
 * classpath held, so the spec can check what the manifest task runs it with.
 */
public final class ApplicationCommandManifestWriter {

    private ApplicationCommandManifestWriter() {
    }

    public static void main(String[] args) throws IOException {
        String runtimeMarker = marker("runtime-marker.txt");
        Path file = Path.of(args[0]);
        Files.createDirectories(file.getParent());
        // a writer that fails part way, as one whose output cannot be written
        if (Files.exists(Path.of("fail-writing"))) {
            Files.writeString(file, "{\"version\": 1, \"comm");
            System.exit(1);
        }
        // JSON, as the real writer writes, with what the spec checks in place of the commands
        Files.writeString(file, "{\"version\": 1, \"commands\": [{\"name\": \"fake\", \"className\": \"org.example.FakeCommand\"},"
                + " {\"name\": \"elsewhere\", \"className\": \"org.example.NotInTheBuild\"}], \"arguments\": \"" + args.length + "\", \"file\": \""
                + file.toString().replace("\\", "\\\\") + "\", \"runtimeMarker\": \"" + runtimeMarker + "\"}\n");
    }

    private static String marker(String resource) throws IOException {
        try (InputStream input = ApplicationCommandManifestWriter.class.getClassLoader().getResourceAsStream(resource)) {
            return input == null ? "absent" : new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
