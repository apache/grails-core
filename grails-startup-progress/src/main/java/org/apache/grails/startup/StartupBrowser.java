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
package org.apache.grails.startup;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens a browser on the starting application with the operating system's own command, rather than
 * {@code java.awt.Desktop}, which refuses to work in the headless mode Spring Boot runs applications in.
 */
final class StartupBrowser {

    private static final Logger LOG = LoggerFactory.getLogger(StartupBrowser.class);

    /**
     * The addresses a browser has been opened on in this JVM. Spring Boot DevTools restarts an application
     * in the JVM it started in, and a developer who has the page open does not want another window each time.
     */
    private static final Set<String> OPENED = ConcurrentHashMap.newKeySet();

    private StartupBrowser() {
    }

    /**
     * Opens a browser on the address, unless one has been opened on it already.
     *
     * @param command the command to run, given the address as its last argument, or empty for the
     *                operating system's own
     */
    static void open(URI address, List<String> command) {
        if (!OPENED.add(address.toString())) {
            return;
        }
        List<String> commandLine = new ArrayList<>(command.isEmpty() ? systemCommand() : command);
        commandLine.add(address.toString());
        try {
            new ProcessBuilder(commandLine)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            LOG.debug("Opened a browser on {} with {}", address, commandLine);
        }
        catch (IOException ex) {
            LOG.warn("Could not open a browser on {} with {}: {}", address, commandLine, ex.getMessage());
        }
    }

    private static List<String> systemCommand() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return List.of("open");
        }
        if (os.contains("win")) {
            // unlike "cmd /c start", hands the address over without a shell interpreting its characters
            return List.of("rundll32", "url.dll,FileProtocolHandler");
        }
        return List.of("xdg-open");
    }
}
