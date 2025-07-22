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
package org.grails.forge.cli.util;

import jakarta.inject.Singleton;
import org.grails.forge.util.VersionInfo;
import picocli.CommandLine.IVersionProvider;

import java.util.Objects;

/**
 * Generates version information. Example usage:
 * <pre>
 * &#064;Command(name = 'command', versionProvider = GrailsCliVersionProvider)
 * class App {
 *     &#064;Option(names = ["-V", "--version"], versionHelp = true, description = 'Display version information and quit.')
 *     boolean isVersionRequested
 * }
 * </pre>
 * Or use picocli's built-in standard usage help option (--help and -h) and version help option (-V and --version).
 * <pre>
 * &#064;Command(name = 'command', mixinStandardHelpOptions = true, versionProvider = GrailsCliVersionProvider)
 * class App {
 *     // ...
 * }
 * </pre>
 *
 * @author Remko Popma
 * @version 1.0
 */
@Singleton
public class GrailsVersionProvider implements IVersionProvider {

    public String[] getVersion() {
        return new String[] {
                "Grails Version: " + VersionInfo.getGrailsVersion(),
                "JVM Version: " + Objects.requireNonNullElse(System.getProperty("java.version"), "<a valid java.home was not found>")
        };
    }
}
