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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    GrailsVersion(String version) {
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
        } else if ((m = SNAPSHOT.matcher(candidateString)).matches()) {
            releaseType = GrailsReleaseType.SNAPSHOT;
            candidate = null;
        } else {
            throw new IllegalArgumentException("Invalid Candidate Version: " + candidateString);
        }
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
                Objects.equals(candidate, that.candidate)
                ;
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

        if (releaseType != o.releaseType) {
            return o.releaseType.ordinal() - releaseType.ordinal();
        }

        int majorCompare = Integer.compare(this.major, o.major);
        if (majorCompare != 0) {
            return majorCompare;
        }

        int minorCompare = Integer.compare(this.minor, o.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }

        int patchCompare = Integer.compare(this.patch, o.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }

        if (candidate == null) {
            return 0;
        }

        return Integer.compare(this.candidate, o.candidate);
    }

    @Override
    public String toString() {
        return version;
    }
}
