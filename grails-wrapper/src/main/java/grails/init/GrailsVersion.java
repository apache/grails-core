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
        String raw = System.getenv("GRAILS_WRAPPER_ALLOWED_TYPES");
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
                        throw new IllegalStateException("Invalid Value in GRAILS_WRAPPER_ALLOWED_TYPES: " + input);
                    }
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static GrailsVersion getPreferredGrailsVersion() {
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

            System.out.println("gradle.properties does not contain grailsVersion; assuming latest Grails Version");
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
