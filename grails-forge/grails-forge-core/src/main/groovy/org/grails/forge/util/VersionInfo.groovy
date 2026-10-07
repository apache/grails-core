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
package org.grails.forge.util

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.grails.forge.options.JdkVersion
import java.nio.charset.StandardCharsets

@CompileStatic
class VersionInfo {

    private static final Properties VERSIONS = new Properties()

    static {
        URL resource = VersionInfo.getResource('/grails-versions.properties')
        if (resource != null) {
            try (Reader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
                VERSIONS.load(reader)
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    /**
     * @return The Grails version
     */
    static String getVersion() {
        Package aPackage = VersionInfo.package
        if (aPackage != null) {
            String implementationVersion = aPackage.getImplementationVersion()
            if (implementationVersion != null) {
                return implementationVersion
            }
        }
        return getGrailsVersion()
    }

    /**
     * @return Checks whether the Grails is a snapshot version.
     */
    static boolean isSnapshot() {
        return getVersion().endsWith('-SNAPSHOT')
    }

    /**
     * @return Checks whether Grails is a snapshot version.
     */
    static boolean isGrailsSnapshot() {
        return getGrailsVersion().endsWith('-SNAPSHOT')
    }

    static String getGrailsVersion() {
        Object grailsVersion = VERSIONS.get('grails.version')
        if (grailsVersion != null) {
            return grailsVersion.toString()
        }
        return '6.0.0-M4'
    }

    /**
     * Retrieves the documentation version for Grails, which is determined based on whether
     * Grails is in a snapshot state or a specific version.
     *
     * @return The documentation version as a string, either "snapshot" if Grails is a snapshot
     *         version, or the specific version of Grails.
     */
    static String getDocumentationVersion() {
        return isGrailsSnapshot() ? 'snapshot' : getGrailsVersion()
    }

    /**
     * Gets the dependency versions.
     *
     * @return The versions
     */
    static Map<String, String> getDependencyVersions() {
        Map<String, String> map = new LinkedHashMap<>()
        for (String key : VERSIONS.stringPropertyNames().sort()) {
            map.put(key, VERSIONS.getProperty(key))
        }
        return Collections.unmodifiableMap(map)
    }

    /**
     * Get a dependency version from the BOM for the given ID.
     * @param id The ID
     * @return The dependency version as a string
     */
    static @Nonnull String getBomVersion(String id) {
        String key = id + '.version'
        Object version = VERSIONS.get(key)
        if (version != null) {
            return version.toString()
        }
        throw new IllegalArgumentException('Could not get version for ID ' + id)
    }

    /**
     * Get a dependency version for the given ID.
     * @param id The ID
     * @return The dependency version
     */
    static @Nonnull Map.Entry<String, String> getDependencyVersion(String id) {
        String key = id + '.version'
        Object version = VERSIONS.get(key)
        if (version != null) {
            return new Map.Entry<String, String>() {
                @Override
                String getKey() {
                    return key
                }

                @Override
                String getValue() {
                    return version.toString()
                }

                @Override
                String setValue(String value) {
                    throw new UnsupportedOperationException('Cannot set version')
                }
            }
        }
        throw new IllegalArgumentException('Could not get version for ID ' + id)
    }

    static JdkVersion getJavaVersion() {
        String version = System.getProperty('java.version')
        if (version.startsWith('1.')) {
            version = version.substring(2)
        }
        // Allow these formats:
        // 1.8.0_72-ea
        // 9-ea
        // 9
        // 9.0.1
        int dotPos = version.indexOf('.')
        int dashPos = version.indexOf('-')
        return JdkVersion.valueOf(Integer.parseInt(version.substring(0,
                dotPos > -1 ? dotPos : dashPos > -1 ? dashPos : version.length())))
    }

    static String toJdkVersion(int javaVersion) {
        String jdkVersion = String.valueOf(javaVersion)
        return javaVersion <= 8 ? '1.' + jdkVersion : jdkVersion
    }

}
