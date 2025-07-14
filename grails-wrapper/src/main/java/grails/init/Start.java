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
package grails.init;

import grails.proxy.SystemPropertiesAuthenticator;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The purpose of this class is to download the expanded Grails wrapper jars into GRAILS_WRAPPER_HOME (`.grails` in the project root)
 * This class is not meant to be distributed as part of SDKMAN since we'll distribute the expanded jars with it.
 * After downloading the jars, it will delegate to the downloaded grails-cli project.
 * <p>
 * There are 3 ways this class can be used:
 * 1. in testing a grails release (run from a non-project directory) - requires GRAILS_REPO_URL set to `~/.m2/repository`
 * 2. running from a non-project directory (end user usage)
 * 3. running from inside a grails project
 */
public class Start {

    public static void main(String[] args) {
        Authenticator.setDefault(new SystemPropertiesAuthenticator());

        try {
            GrailsVersion preferredGrailsVersion = getPreferredGrailsVersion();
            LinkedHashSet<GrailsReleaseType> allowedTypes = getAllowedReleaseTypes(preferredGrailsVersion);

            GrailsUpdater updater = new GrailsUpdater(allowedTypes, preferredGrailsVersion);
            boolean forceUpdate = (args.length > 0 && args[0].trim().equals("update-wrapper"));

            boolean updated = false;
            String[] adjustedArgs = args;
            if (forceUpdate || updater.needsUpdating()) {
                String allowTypesString = allowedTypes.stream().map(GrailsReleaseType::name).collect(Collectors.joining(","));
                System.out.printf("Updating Grails wrapper, allowed versions to update to are [%s]...%n", allowTypesString);

                updated = updater.update();

                // remove "update-wrapper" command argument
                if (forceUpdate) {
                    adjustedArgs = Arrays.copyOfRange(args, 1, args.length);
                }
            }

            if (updated) {
                System.out.println("Updated wrapper to version: " + updater.getSelectedVersion().toString());
            }

            URLClassLoader child = new URLClassLoader(new URL[]{updater.getExecutedJarFile().toURI().toURL()});
            Class<?> classToLoad = Class.forName("org.apache.grails.cli.DelegatingShellApplication", true, child);
            Method main = classToLoad.getMethod("main", String[].class);
            main.invoke(null, (Object) adjustedArgs);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static GrailsVersion getPreferredGrailsVersion() {
        // Check for a properties file in case inside a grails project
        File gradleProperties = new File("gradle.properties");
        if (!gradleProperties.exists()) {
            return null;
        }

        Properties properties = new Properties();
        try (InputStream in = new FileInputStream(gradleProperties)) {
            properties.load(in);
        } catch (Exception e) {
            System.err.println("Failed to load gradle.properties from " + gradleProperties);
            e.printStackTrace();
            System.exit(1);
        }

        if (!properties.containsKey("grailsVersion")) {
            return null;
        }

        String grailsVersion = properties.getProperty("grailsVersion");
        if (grailsVersion == null) {
            System.out.println("gradle.properties does not contain grailsVersion; downloading latest Grails Version");

            String overrideGrailsVersion = System.getenv("PREFERRED_GRAILS_VERSION");
            if (overrideGrailsVersion != null) {
                try {
                    return new GrailsVersion(overrideGrailsVersion);
                } catch (Exception e) {
                    System.out.println("An invalid Grails Version [" + overrideGrailsVersion + "] was specified in PREFERRED_GRAILS_VERSION");
                    e.printStackTrace();
                    System.exit(1);
                }
            }

            return null;
        }

        try {
            return new GrailsVersion(grailsVersion);
        } catch (Exception e) {
            System.out.println("An invalid Grails Version [" + grailsVersion + "] was specified in gradle.properties");
            e.printStackTrace();
            System.exit(1);
        }

        return null;
    }

    private static LinkedHashSet<GrailsReleaseType> getAllowedReleaseTypes(GrailsVersion preferredVersion) {
        String raw = System.getenv("GRAILS_WRAPPER_ALLOWED_TYPES");
        if (raw == null || raw.trim().isEmpty()) {
            if (preferredVersion != null) {
                //inside a grails project pull the equivalent version type or newer
                return preferredVersion.releaseType.upTo();
            } else {
                String grailsVersion = Start.class.getPackage().getImplementationVersion();
                if (grailsVersion == null) {
                    // the only time this version isn't defined is when it comes from a non-jar file, which should
                    // only be in development
                    return new LinkedHashSet<>(List.of(GrailsReleaseType.SNAPSHOT));
                }

                GrailsVersion myVersion = new GrailsVersion(grailsVersion);
                if (myVersion.releaseType != GrailsReleaseType.RELEASE) {
                    return new LinkedHashSet<>(List.of(GrailsReleaseType.values()));
                }

                // Only consider releases unless this wrapper
                return new LinkedHashSet<>(List.of(GrailsReleaseType.RELEASE));
            }
        }

        return new LinkedHashSet<>(Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(GrailsReleaseType::valueOf)
                .collect(Collectors.toList()));
    }
}
