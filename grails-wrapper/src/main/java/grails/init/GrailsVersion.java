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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assists in parsing grails versions and sorting them by priority
 */
public class GrailsVersion implements Comparable<GrailsVersion> {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)[.](\\d+)[.](.*)$");

    private static final Pattern RELEASE = Pattern.compile("^(\\d+)$");
    private static final Pattern RC = Pattern.compile("^RC(\\d+)$");
    private static final Pattern MILESTONE = Pattern.compile("^M(\\d+)$");
    private static final Pattern SNAPSHOT = Pattern.compile("^(\\d+)-SNAPSHOT$");

    public final String version;
    public final String major;
    public final String minor;
    public final String patch;
    public final GrailsReleaseType releaseType;
    public final int patchNumber;

    /**
     * @param version the grails version number
     */
    GrailsVersion(String version) {
        this.version = version;

        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version format: " + version);
        }

        if (matcher.groupCount() != 3) {
            throw new IllegalArgumentException("Invalid version format: " + version);
        }

        major = matcher.group(1);
        minor = matcher.group(2);
        patch = matcher.group(3);

        Matcher m;
        if ((m = RELEASE.matcher(patch)).matches()) {
            releaseType = GrailsReleaseType.RELEASE;
            patchNumber = Integer.parseInt(m.group(1));
        } else if ((m = RC.matcher(patch)).matches()) {
            releaseType = GrailsReleaseType.RC;
            patchNumber = Integer.parseInt(m.group(1));
        } else if ((m = MILESTONE.matcher(patch)).matches()) {
            releaseType = GrailsReleaseType.MILESTONE;
            patchNumber = Integer.parseInt(m.group(1));
        } else if ((m = SNAPSHOT.matcher(patch)).matches()) {
            releaseType = GrailsReleaseType.SNAPSHOT;
            patchNumber = Integer.parseInt(m.group(1));
        } else {
            throw new IllegalArgumentException("Unrecognized patch version: " + patch);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GrailsVersion that = (GrailsVersion) o;
        return Objects.equals(major, that.major) && Objects.equals(minor, that.minor) && Objects.equals(patch, that.patch);
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

        int majorCompare = this.major.compareTo(o.major);
        if (majorCompare != 0) {
            return majorCompare;
        }

        int minorCompare = this.minor.compareTo(o.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }

        if(releaseType != o.releaseType) {
            return releaseType.ordinal() - o.releaseType.ordinal();
        }

        return Integer.compare(patchNumber, o.patchNumber);
    }

    @Override
    public String toString() {
        return version;
    }
}
