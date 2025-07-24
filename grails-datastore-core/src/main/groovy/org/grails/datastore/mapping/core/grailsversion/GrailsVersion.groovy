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

package org.grails.datastore.mapping.core.grailsversion

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

import org.grails.datastore.mapping.reflect.ClassUtils

/**
 * A class to represent a version of Grails for comparison
 *
 * @author James Kleeh
 * @author Graeme Rocher
 */
@CompileStatic
@EqualsAndHashCode(includes = ['versionText'])
class GrailsVersion implements Comparable<GrailsVersion> {

    /**
     * The current version
     */
    private static GrailsVersion currentVersion = null

    /**
     * The major version
     */
    int major
    /**
     * The minor version
     */
    int minor
    /**
     * The patch version
     */
    int patch
    /**
     * Information about the snapshot status
     */
    Snapshot snapshot
    /**
     * The full version text
     */
    String versionText

    @Override
    String toString() {
        return versionText
    }

    GrailsVersion(String version) {
        String[] parts = version.split("\\.")
        if (parts.length >= 3) {
            this.versionText = version
            this.major = parts[0].toInteger()
            this.minor = parts[1].toInteger()
            if (parts.length > 3) {
                this.snapshot = new Snapshot(parts[3])
            } else if (parts[2].contains('-')) {
                String[] subParts = parts[2].split("-")
                this.patch = subParts.first() as int
                this.snapshot = new Snapshot(subParts[1..-1].join("-"))
            } else {
                this.patch = parts[2].toInteger()
            }
        } else {
            throw new IllegalArgumentException("GrailsVersion only supports comparison of versions with 3 or 4 parts")
        }
    }

    /**
     * Check whether the current version is at least the given major and minor version
     *
     * @param majorVersion The major version
     * @param minorVersion The minor version
     * @return True if it is
     */
    static boolean isAtLeastMajorMinor(int majorVersion, int minorVersion) {
        GrailsVersion current = getCurrent()
        return isAtLeastMajorMinorImpl(current, majorVersion, minorVersion)
    }

    /**
     * Check whether the current version is at least the given major and minor version
     *
     * @param majorVersion The major version
     * @param minorVersion The minor version
     * @return True if it is
     */
    static boolean isAtLeastMajorMinor(String version, int majorVersion, int minorVersion) {
        return isAtLeastMajorMinorImpl(new GrailsVersion(version), majorVersion, minorVersion)
    }

    private static boolean isAtLeastMajorMinorImpl(GrailsVersion version, int majorVersion, int minorVersion) {
        if (version != null) {
            if (version.major > majorVersion) {
                return true
            }
            return version.major == majorVersion && version.minor >= minorVersion
        }
        return false
    }
    /**
     * Check whether the current version is at least the given version
     *
     * @param requiredVersion The required version
     * @return True if it is
     */
    static boolean isAtLeast(String requiredVersion) {
        GrailsVersion currentVersion = getCurrent()
        return isAtLeastImpl(currentVersion, requiredVersion)
    }
    /**
     * Check whether the version is at least the given version
     *
     * @param version The version
     * @param requiredVersion The required version
     * @return True if it is
     */
    static boolean isAtLeast(String version, String requiredVersion) {
        return isAtLeastImpl(new GrailsVersion(version), requiredVersion)
    }
    /**
     * Check whether the version is at least the given version
     *
     * @param version The version
     * @param requiredVersion The required version
     * @return True if it is
     */
    private static boolean isAtLeastImpl(GrailsVersion version, String requiredVersion) {
        if (version != null) {
            // if the current version is greater than the required version
            GrailsVersion otherVersion = new GrailsVersion(requiredVersion)
            if (version >= otherVersion || version == otherVersion) {
                return true
            }
        }
        return false
    }

    /**
     * @return Obtains the current Grails version
     */
    static GrailsVersion getCurrent() {
        if(currentVersion != null) {
            return currentVersion
        }
        else if (ClassUtils.isPresent("grails.util.BuildSettings")) {
            currentVersion = new GrailsVersion(Class.forName("grails.util.BuildSettings").package.implementationVersion)
            return currentVersion
        } else {
            null
        }
    }

    boolean isSnapshot() {
        snapshot != null
    }

    @Override
    int compareTo(GrailsVersion o) {
        int majorCompare = this.major <=> o.major
        if (majorCompare != 0) {
            return majorCompare
        }

        int minorCompare = this.minor <=> o.minor
        if (minorCompare != 0) {
            return minorCompare
        }

        int patchCompare = this.patch <=> o.patch
        if (patchCompare != 0) {
            return patchCompare
        }

        if (this.isSnapshot() && !o.isSnapshot()) {
            return -1
        } else if (!this.isSnapshot() && o.isSnapshot()) {
            return 1
        } else if (this.isSnapshot() && o.isSnapshot()) {
            return this.getSnapshot() <=> o.getSnapshot()
        } else {
            return 0
        }
    }
}
