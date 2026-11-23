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
import java.time.LocalDate
import java.time.format.DateTimeParseException

@CompileStatic
class VersionInfo {

    private static final String END_OF_SUPPORT = 'grails.endOfSupport'
    private static final String GRADLE_VERSION = 'gradle.version'
    private static final String GRADLE_MINIMUM = 'gradle.minimum'
    // what the build writes beside the versions of the BOM, and is no version of a dependency
    private static final Set<String> NOT_DEPENDENCIES = [END_OF_SUPPORT, GRADLE_VERSION, GRADLE_MINIMUM].toSet().asImmutable()
    private static final Properties VERSIONS = new Properties()
    private static final String GENERATOR_SHA256 = generatorSha256()

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
     * The identity of this build of the generator: the digest of what it generates from, its sources,
     * templates and feature packs and the versions it writes, which the build computes. Two builds of
     * one version, as two snapshots are, have different ones when they generate from different sources.
     *
     * @return the SHA-256 digest in hexadecimal, or {@code null} for a generator that was not built with
     * one, as when it runs from the classes an editor compiled
     * @since 9.0
     */
    static String getGeneratorSha256() {
        return GENERATOR_SHA256
    }

    private static String generatorSha256() {
        URL resource = VersionInfo.getResource('/META-INF/grails-forge-generator.properties')
        if (resource == null) {
            return null
        }
        Properties properties = new Properties()
        try (Reader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
            properties.load(reader)
        } catch (IOException ignored) {
            return null
        }
        String digest = properties.getProperty('sha256')
        return digest != null && digest ==~ /[0-9a-f]{64}/ ? digest : null
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
        // by the lookup that gives a text: making a text of an object goes through the formatting of
        // Groovy, which loads and scans far more than this is worth, on every start of a command line
        String grailsVersion = VERSIONS.getProperty('grails.version')
        if (grailsVersion != null) {
            return grailsVersion
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
     * Retrieves the last day the Grails release line is supported.
     *
     * @return The end of support date, or empty when none is configured
     */
    static Optional<LocalDate> getEndOfSupport() {
        Object endOfSupport = VERSIONS.get(END_OF_SUPPORT)
        if (endOfSupport == null) {
            return Optional.empty()
        }
        try {
            return Optional.of(LocalDate.parse(endOfSupport.toString().trim()))
        } catch (DateTimeParseException ignored) {
            return Optional.empty()
        }
    }

    /**
     * The version of Gradle the wrapper of a generated application names. The build writes it from
     * {@code gradleToolingApiVersion} of the root of the Grails repository, which is the Gradle of
     * the build of Grails itself.
     *
     * @return the version, as {@code 9.9.2}
     * @throws IllegalStateException for a generator that was not built with it
     * @since 9.0
     */
    static String getGradleVersion() {
        return required(GRADLE_VERSION)
    }

    /**
     * The earliest version of Gradle a Grails application of this version is built with. The build
     * writes it from {@code gradleMinimumVersion} of the root of the Grails repository.
     *
     * @return the version, as {@code 9.7}
     * @throws IllegalStateException for a generator that was not built with it
     * @since 9.0
     */
    static String getMinimumGradleVersion() {
        return required(GRADLE_MINIMUM)
    }

    private static String required(String key) {
        String value = VERSIONS.getProperty(key)
        if (value == null) {
            throw new IllegalStateException("The versions this generator was built with have no ${key}".toString())
        }
        return value
    }

    /**
     * Gets the dependency versions.
     *
     * @return The versions
     */
    static Map<String, String> getDependencyVersions() {
        Map<String, String> map = new LinkedHashMap<>()
        for (String key : VERSIONS.stringPropertyNames().sort()) {
            if (!NOT_DEPENDENCIES.contains(key)) {
                map.put(key, VERSIONS.getProperty(key))
            }
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
