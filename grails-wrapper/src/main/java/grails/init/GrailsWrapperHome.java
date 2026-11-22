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
package grails.init;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for managing the `grails-cli` jar files in the Grails Wrapper home directory.
 */
public class GrailsWrapperHome {
    public static final String CLI_COMBINED_PROJECT_NAME = "grails-cli";

    public final File home;
    public final File wrapperDirectory;
    public final List<GrailsVersion> versions;
    public final Set<GrailsReleaseType> allowedReleaseTypes;
    public final GrailsVersion latestVersion;

    public GrailsWrapperHome(LinkedHashSet<GrailsReleaseType> allowedReleaseTypes, String forcedGrailsHome) throws IOException {
        home = findGrailsHome(forcedGrailsHome).getCanonicalFile();
        this.allowedReleaseTypes = allowedReleaseTypes == null ? new LinkedHashSet<>() : allowedReleaseTypes;

        wrapperDirectory = new File(home, "wrapper");
        if (!wrapperDirectory.exists()) {
            wrapperDirectory.mkdirs();
        } else if (!wrapperDirectory.isDirectory()) {
            throw new IllegalStateException("GRAILS_WRAPPER_HOME must contain a wrapper directory. File exists instead at " + wrapperDirectory.getAbsolutePath());
        }

        versions = determineVersions();
        latestVersion = findLatestVersion();
    }

    File getLatestVersionDirectory() {
        return getVersionDirectory(latestVersion);
    }

    File getVersionDirectory(GrailsVersion version) {
        if (version == null) {
            return null;
        }

        return new File(wrapperDirectory, version.version);
    }

    File getLatestWrapperImplementation() {
        File implDirectory = getLatestVersionDirectory();
        return getWrapperImplementation(latestVersion, implDirectory);
    }

    File getWrapperImplementation(GrailsVersion version, File implDirectory) {
        if (implDirectory == null) {
            return null;
        }

        return new File(implDirectory, CLI_COMBINED_PROJECT_NAME + "-" + version.version + "-all.jar");
    }

    /**
     * The versions are sorted as {@link GrailsVersion} orders them, the latest first.
     */
    private GrailsVersion findLatestVersion() {
        return versions.isEmpty() ? null : versions.get(0);
    }

    /**
     * Finds the available versions in the GRAILS_WRAPPER_HOME directory; sorted as {@link GrailsVersion} orders them, the latest first
     */
    private List<GrailsVersion> determineVersions() {
        File[] children = wrapperDirectory.listFiles();
        if (children == null || children.length == 0) {
            return new ArrayList<>();
        }

        List<GrailsVersion> versions = new ArrayList<>();
        for (File child : children) {
            if (!child.isDirectory()) {
                continue;
            }

            try {
                GrailsVersion version = new GrailsVersion(child.getName());
                if (!allowedReleaseTypes.isEmpty() && !allowedReleaseTypes.contains(version.releaseType)) {
                    continue;
                }
                versions.add(version);
            } catch (Exception ignored) {
                throw new IllegalStateException("Grails Version [" + child.getName() + "] at [" + child.getAbsolutePath() + "] is not a valid Grails version.");
            }
        }

        Collections.sort(versions);

        return versions;
    }

    /**
     * Locates the home of the wrapper, where the command line it downloads is kept in {@code wrapper}:
     * <ol>
     * <li>the directory that is given,</li>
     * <li>the directory the environment variable {@code GRAILS_WRAPPER_HOME} names,</li>
     * <li>{@code .grails} of the home directory of the user.</li>
     * </ol>
     * The home is never looked for in a project. Any directory can be named, the {@code .grails} directory of
     * a project among them; the downloads are then in its {@code wrapper} directory.
     *
     * @param grailsHomeOverride the directory to use, or null
     * @return the home directory of the wrapper
     * @throws IOException if canonicalization fails
     */
    public static File findGrailsHome(String grailsHomeOverride) throws IOException {
        if (grailsHomeOverride != null && !grailsHomeOverride.isEmpty()) {
            return validateGrailsHome(grailsHomeOverride, "Specified Grails Home");
        }

        String environmentOverride = System.getenv("GRAILS_WRAPPER_HOME");
        if (environmentOverride != null && !environmentOverride.isEmpty()) {
            return validateGrailsHome(environmentOverride, "GRAILS_WRAPPER_HOME environment variable");
        }

        File userHome = new File(System.getProperty("user.home")).getCanonicalFile();
        File grailsHome = new File(userHome, ".grails");
        if (grailsHome.exists() && !grailsHome.isDirectory()) {
            throw new IllegalStateException("Grails Wrapper Home [" + grailsHome + "] is not a directory.");
        }
        grailsHome.mkdirs();
        return grailsHome;
    }

    private static File validateGrailsHome(String possibleGrailsHome, String description) {
        File possibleHome = new File(possibleGrailsHome);
        if (!possibleHome.exists()) {
            possibleHome.mkdirs();
        }

        if (!possibleHome.isDirectory()) {
            throw new IllegalArgumentException(description + " [" + possibleGrailsHome + "] is not a directory");
        }

        return possibleHome;
    }
}
