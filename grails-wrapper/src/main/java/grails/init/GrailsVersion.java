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
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assists in parsing grails versions and sorting them by priority
 */
public class GrailsVersion implements Comparable<GrailsVersion> {

    /** Environment variable honoured outside a Grails project to pin the wrapper to a specific version. */
    public static final String PREFERRED_GRAILS_VERSION_ENV = "PREFERRED_GRAILS_VERSION";

    /** Environment variable used to restrict which release types the wrapper will resolve (e.g. {@code SNAPSHOT,RC}). */
    public static final String GRAILS_WRAPPER_ALLOWED_TYPES_ENV = "GRAILS_WRAPPER_ALLOWED_TYPES";

    /** Property key read from {@code gradle.properties} to pin the wrapper to a specific version inside a project. */
    public static final String GRAILS_VERSION_PROPERTY = "grailsVersion";

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)[.](\\d+)[.](\\d+)-?(.*)$");

    private static final Pattern RC = Pattern.compile("^RC(\\d+)$");
    private static final Pattern MILESTONE = Pattern.compile("^M(\\d+)$");
    private static final Pattern SNAPSHOT = Pattern.compile("^SNAPSHOT$");

    public final String version;
    public final int major;
    public final int minor;
    public final int patch;
    public final GrailsReleaseType releaseType;
    public final Integer candidate;

    /**
     * @param version the grails version number
     */
    public GrailsVersion(String version) {
        this.version = version;

        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Grails Version format: " + version);
        }

        if (matcher.groupCount() != 4) {
            throw new IllegalArgumentException("Invalid Grails Version format: " + version);
        }

        major = Integer.parseInt(matcher.group(1));
        minor = Integer.parseInt(matcher.group(2));
        patch = Integer.parseInt(matcher.group(3));

        String candidateString = matcher.group(4);

        Matcher m;
        if (candidateString.isEmpty()) {
            releaseType = GrailsReleaseType.RELEASE;
            candidate = null;
        } else if ((m = RC.matcher(candidateString)).matches()) {
            releaseType = GrailsReleaseType.RC;
            candidate = Integer.parseInt(m.group(1));
        } else if ((m = MILESTONE.matcher(candidateString)).matches()) {
            releaseType = GrailsReleaseType.MILESTONE;
            candidate = Integer.parseInt(m.group(1));
        } else if (SNAPSHOT.matcher(candidateString).matches()) {
            releaseType = GrailsReleaseType.SNAPSHOT;
            candidate = null;
        } else {
            throw new IllegalArgumentException("Invalid Candidate Version: " + candidateString);
        }
    }

    public static LinkedHashSet<GrailsReleaseType> getAllowedReleaseTypes(GrailsVersion preferredVersion, GrailsWrapper wrapper) {
        String raw = System.getenv(GRAILS_WRAPPER_ALLOWED_TYPES_ENV);
        if (raw == null || raw.trim().isEmpty()) {
            if (preferredVersion != null) {
                //inside a grails project pull the equivalent version type or newer
                return preferredVersion.releaseType.upTo();
            } else {
                String grailsVersion = wrapper.getVersion();
                if (grailsVersion == null) {
                    // the only time this version isn't defined is when it comes from a non-jar file, which should
                    // only be in development of the wrapper
                    System.out.println("Detected running from a non-jar file, assuming local grails-core development...");
                    return new LinkedHashSet<>(List.of(GrailsReleaseType.SNAPSHOT));
                }

                GrailsVersion myVersion = new GrailsVersion(grailsVersion);
                if (myVersion.releaseType != GrailsReleaseType.RELEASE) {
                    return new LinkedHashSet<>(myVersion.releaseType.upTo());
                }

                // Only consider releases of this wrapper
                return new LinkedHashSet<>(List.of(GrailsReleaseType.RELEASE));
            }
        }

        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .map(input -> {
                try {
                    return GrailsReleaseType.valueOf(input);
                } catch (Exception e) {
                    throw new IllegalStateException("Invalid Value in " + GRAILS_WRAPPER_ALLOWED_TYPES_ENV + ": " + input);
                }
            })
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static GrailsVersion getPreferredGrailsVersion() {
        return getPreferredGrailsVersion(new File("gradle.properties"));
    }

    /**
     * Determine the preferred Grails version.
     *
     * <p>Precedence matches the documented behaviour in the wrapper README:
     * <ol>
     *   <li>Inside a Grails project (a {@code gradle.properties} containing a
     *       {@code grailsVersion} key with a non-blank value), use that value
     *       and ignore environment variables. A present-but-blank value or an
     *       unparseable version is treated as a project misconfiguration and
     *       exits the process.</li>
     *   <li>Outside a Grails project (no {@code gradle.properties}, or one
     *       without {@code grailsVersion}), honour the
     *       {@code PREFERRED_GRAILS_VERSION} environment variable. A
     *       whitespace-only value is treated as unset.</li>
     *   <li>Otherwise return {@code null} and let the wrapper resolve the
     *       latest version from Maven metadata.</li>
     * </ol>
     *
     * @param gradleProperties the properties file to inspect; exposed as a
     *                         parameter for testability
     * @return the pinned {@link GrailsVersion} or {@code null} if none was
     *         specified
     */
    static GrailsVersion getPreferredGrailsVersion(File gradleProperties) {
        GrailsVersion fromProperties = readVersionFromProperties(gradleProperties);
        if (fromProperties != null) {
            // Inside a Grails project: gradle.properties wins and the env var is intentionally ignored per the wrapper README.
            return fromProperties;
        }

        String overrideGrailsVersion = System.getenv(PREFERRED_GRAILS_VERSION_ENV);
        if (overrideGrailsVersion == null || (overrideGrailsVersion = overrideGrailsVersion.trim()).isEmpty()) {
            return null;
        }

        try {
            return new GrailsVersion(overrideGrailsVersion);
        } catch (Exception e) {
            System.out.println("An invalid Grails Version [" + overrideGrailsVersion + "] was specified in " + PREFERRED_GRAILS_VERSION_ENV);
            e.printStackTrace();
            System.exit(1);
        }

        return null;
    }

    private static GrailsVersion readVersionFromProperties(File gradleProperties) {
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

        String grailsVersion = properties.getProperty(GRAILS_VERSION_PROPERTY);
        if (grailsVersion == null) {
            return null;
        }
        grailsVersion = grailsVersion.trim();
        if (grailsVersion.isEmpty()) {
            System.out.println("A blank Grails Version was specified in gradle.properties for key [" + GRAILS_VERSION_PROPERTY + "]");
            System.exit(1);
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GrailsVersion that = (GrailsVersion) o;
        return Objects.equals(releaseType, that.releaseType) &&
            Objects.equals(major, that.major) &&
            Objects.equals(minor, that.minor) &&
            Objects.equals(patch, that.patch) &&
            Objects.equals(candidate, that.candidate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public int compareTo(GrailsVersion o) {
        if (o == null) {
            return 1;
        }

        if (o.equals(this)) {
            return 0;
        }

        int majorCompare = Integer.compare(o.major, this.major);
        if (majorCompare != 0) {
            return majorCompare;
        }

        int minorCompare = Integer.compare(o.minor, this.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }

        int patchCompare = Integer.compare(o.patch, this.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }

        if (releaseType != o.releaseType) {
            return releaseType.ordinal() - o.releaseType.ordinal();
        }

        if (candidate == null) {
            return 0;
        }

        return Integer.compare(o.candidate, this.candidate);
    }

    @Override
    public String toString() {
        return version;
    }
}
