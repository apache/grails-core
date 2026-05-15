/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.boot

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import org.springframework.boot.Banner
import org.springframework.boot.SpringBootVersion
import org.springframework.core.SpringVersion
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource

import grails.util.BuildSettings

/**
 * The default Grails application banner.
 *
 * @since 7.1
 */
@CompileStatic
@MapConstructor(noArg = true)
class GrailsBanner implements Banner {

    private static final int FALLBACK_BANNER_WIDTH = 0
    private static final String DEFAULT_BANNER_FILE = 'grails-banner.txt'

    String bannerFile = DEFAULT_BANNER_FILE
    int bannerPaddingTop = 1
    int bannerPaddingBottom = 1
    int artPaddingBottom = 0

    /**
     * Prints the banner to the specified PrintStream.
     *
     * @param environment the current environment
     * @param sourceClass the source class
     * @param out the PrintStream to print to
     */
    @Override
    void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {

        def bannerWidth = FALLBACK_BANNER_WIDTH

        bannerPaddingTop.times { out.println() }
        if (shouldDisplayArt(environment)) {
            def art = createBannerArt(environment)
            bannerWidth = longestLineLength(art) ?: FALLBACK_BANNER_WIDTH
            out.println(art)
            artPaddingBottom.times { out.println() }
        }
        if (shouldDisplayVersions(environment)) {
            createVersionsFormatter().format(createBannerVersions(environment), bannerWidth)
                    .forEach { out.println(it) }
        }
        bannerPaddingBottom.times { out.println() }
    }

    /**
     * Creates the banner art to be displayed.
     *
     * @param environment the current environment
     * @return the banner art
     */
    protected String createBannerArt(Environment environment) {
        if (bannerFile != DEFAULT_BANNER_FILE) {
            // Banner file was programmatically set, use it directly
            def customBannerResource = new ClassPathResource(bannerFile)
            if (customBannerResource.exists()) {
                return customBannerResource.inputStream.text
            }
        } else {
            // Use configured banner file or default
            def configBannerFile = environment.getProperty('grails.banner.art.file', String, DEFAULT_BANNER_FILE)
            def bannerResource = new ClassPathResource(configBannerFile)
            if (bannerResource.exists()) {
                return bannerResource.inputStream.text
            }
        }
        return ''
    }

    /**
     * Creates a map of versions to be displayed in the banner.
     *
     * @param env the current env
     * @return a map of version labels to version values
     */
    @SuppressWarnings('GrMethodMayBeStatic')
    protected Map<String,String> createBannerVersions(Environment env) {
        def defaultIncluded = (DefaultVersionOption.values()).collect { it.key }
        def sortOrder = findConfiguredVersions(env, 'grails.banner.versions.order') { it in VersionOption.values()*.key }
        def configExcluded = findConfiguredVersions(env, 'grails.banner.versions.exclude') { it in DefaultVersionOption.values()*.key }
        def configIncluded = findConfiguredVersions(env, 'grails.banner.versions.include') { it in OptionalVersionOption.values()*.key }
        def includedVersions = defaultIncluded
                .tap { removeAll(configExcluded) }
                .tap { addAll(configIncluded) }
                .unique()
        if (sortOrder) {
            includedVersions.sort { a, b ->
                def indexA = sortOrder.indexOf(a)
                def indexB = sortOrder.indexOf(b)
                if (indexA == -1 && indexB == -1) {
                    return 0
                } else if (indexA == -1) {
                    return 1
                } else if (indexB == -1) {
                    return -1
                } else {
                    return indexA <=> indexB
                }
            }
        }
        includedVersions.collectEntries { key ->
            switch (VersionOption.fromString(key)) {
                case VersionOption.APP:
                    [(env.getProperty('info.app.name') ?: 'app'): env.getProperty('info.app.version') ?: 'unknown']
                    break
                case VersionOption.JVM:
                    ['JVM': System.getProperty('java.vendor') + ' ' + System.getProperty('java.version')]
                    break
                case VersionOption.GRAILS:
                    ['Grails': BuildSettings.grailsVersion]
                    break
                case VersionOption.GROOVY:
                    ['Groovy': GroovySystem.version]
                    break
                case VersionOption.SPRING_BOOT:
                    ['Spring Boot': SpringBootVersion.version]
                    break
                case VersionOption.SPRING:
                    ['Spring': SpringVersion.version]
                    break
                case VersionOption.SPRING_SECURITY:
                    ['Spring Security': findVersion('org.springframework.security.core.SpringSecurityCoreVersion')]
                    break
                case VersionOption.TOMCAT:
                    ['Tomcat': findVersion('org.apache.catalina.util.ServerInfo')]
                    break
                case VersionOption.JETTY:
                    ['Jetty': findVersion('org.eclipse.jetty.util.Jetty')]
                    break
                case VersionOption.UNDERTOW:
                    ['Undertow': findVersion('io.undertow.Undertow')]
                    break
                default:
                    null
            }
        } as Map<String, String>
    }

    /**
     * Finds the implementation version of the specified class.
     *
     * @param className the fully qualified class name
     * @return the implementation version, or 'unknown' if not found
     */
    private static String findVersion(String className) {

        try {
            def pkg = Class.forName(className).package
            return pkg?.implementationVersion ?: 'unknown'
        } catch (ClassNotFoundException ignore) {
            return 'unknown'
        }
    }

    /**
     * Finds the configured versions from the environment.
     *
     * @param env the current environment
     * @param propertyName the property name to look for
     * @param filter the filter closure
     * @return a list of configured versions
     */
    private static List<String> findConfiguredVersions(
            Environment env,
            String propertyName,
            @ClosureParams(
                    value = SimpleType,
                    options = ['java.lang.String']
            ) Closure<Boolean> filter) {
        env.getProperty(propertyName, List<String>, [] as List<String>).findAll(filter)
    }

    /**
     * Determines whether to display the banner art.
     *
     * @param env the current environment
     * @return true if the banner art should be displayed, false otherwise
     */
    @SuppressWarnings('GrMethodMayBeStatic')
    protected boolean shouldDisplayArt(Environment env) {
        env.getProperty('grails.banner.art.display', Boolean, true)
    }

    /**
     * Determines whether to display the version information.
     *
     * @param env the current environment
     * @return true if the version information should be displayed, false otherwise
     */
    @SuppressWarnings('GrMethodMayBeStatic')
    protected boolean shouldDisplayVersions(Environment env) {
        env.getProperty('grails.banner.versions.display', Boolean, true)
    }

    /**
     * Creates the versions formatter to format the version information.
     *
     * @return the versions formatter
     */
    protected VersionsFormatter createVersionsFormatter() {
        new DefaultVersionFormatter()
    }

    /**
     * Calculates the length of the longest line in the given text.
     *
     * @param text the text to analyze
     * @return the length of the longest line
     */
    private static int longestLineLength(String text) {
        text.readLines()*.size()?.max() ?: 0
    }

    /**
     * Strategy interface for formatting version information
     * into printable lines.
     */
    @FunctionalInterface
    interface VersionsFormatter {

        /**
         * Formats the version information into a list of banner lines.
         *
         * @param versions An insertion-ordered map (e.g., LinkedHashMap)
         *                 mapping human-readable labels to version values.
         *                 The iteration order defines the order of the
         *                 formatted output.
         * @param bannerWidth Total banner width in characters
         * @return a list of lines to print, without line-termination characters
         */
        List<String> format(Map<String, String> versions, int bannerWidth)
    }

    /**
     * The default implementation of the VersionsFormatter.
     */
    @CompileStatic
    @MapConstructor(noArg = true)
    static class DefaultVersionFormatter implements VersionsFormatter {

        int margin = 4
        int maxItemsPerRow = 0 // 0 or negative = unlimited
        String itemSeparator = ' | '
        String pairSeparator = ': '

        /**
         * Formats the version information into centered lines that fit
         * within the banner width.
         */
        @Override
        List<String> format(Map<String, String> versions, int bannerWidth) {
            def columnWidth = bannerWidth - margin * 2
            List<String> rows = []
            def currentRow = new StringBuilder()
            def countInRow = 0

            versions.each { k, v ->
                String item = "$k${pairSeparator}$v"
                def proposedLength = currentRow.length() + (countInRow > 0 ? itemSeparator.size() : 0) + item.size()
                def wouldOverflow = (countInRow > 0 && proposedLength > columnWidth)
                def hitCountLimit = (maxItemsPerRow > 0 && countInRow >= maxItemsPerRow)

                if (wouldOverflow || hitCountLimit) {
                    rows << currentRow.center(bannerWidth)
                    currentRow.length = 0
                    countInRow = 0
                }

                if (currentRow.size() > 0) {
                    currentRow << itemSeparator
                }
                currentRow << item
                countInRow++
            }

            if (countInRow > 0) {
                rows << currentRow.center(bannerWidth)
            }

            return rows
        }
    }

    /**
     * Enumeration of supported version options.
     */
    @CompileStatic
    enum VersionOption {
        APP,
        JVM,
        GRAILS,
        GROOVY,
        SPRING_BOOT,
        SPRING,
        SPRING_SECURITY,
        TOMCAT,
        JETTY,
        UNDERTOW

        final String key

        VersionOption() {
            this.key = name().toLowerCase().replace('_', '-')
        }

        static VersionOption fromString(String value) {
            try {
                return valueOf(value.toUpperCase().replace('-', '_'))
            } catch (IllegalArgumentException ignore) {
                return null
            }
        }
    }

    /**
     * Enumeration of default version options.
     */
    @CompileStatic
    enum DefaultVersionOption {
        APP,
        JVM,
        GRAILS,
        GROOVY,
        SPRING_BOOT,
        SPRING

        final String key

        DefaultVersionOption() {
            this.key = name().toLowerCase().replace('_', '-')
        }
    }

    /**
     * Enumeration of optional version options.
     */
    @CompileStatic
    enum OptionalVersionOption {
        SPRING_SECURITY,
        TOMCAT,
        JETTY,
        UNDERTOW

        final String key

        OptionalVersionOption() {
            this.key = name().toLowerCase().replace('_', '-')
        }
    }
}
