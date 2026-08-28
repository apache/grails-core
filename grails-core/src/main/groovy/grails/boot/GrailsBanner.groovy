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
package grails.boot

import java.lang.management.ManagementFactory

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor
import groovy.util.logging.Slf4j

import org.springframework.boot.Banner
import org.springframework.boot.ansi.Ansi8BitColor
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiElement
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.boot.SpringBootVersion
import org.springframework.aot.AotDetector
import org.springframework.core.NativeDetector
import org.springframework.core.SpringVersion
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource

import grails.util.BuildSettings

/**
 * The default Grails application banner.
 *
 * @since 7.1
 */
@Slf4j
@CompileStatic
@MapConstructor(noArg = true)
class GrailsBanner implements Banner {

    private static final int FALLBACK_BANNER_WIDTH = 0
    private static final String DEFAULT_BANNER_FILE = 'grails-banner.txt'

    private static final String ART_COLOR_PROPERTY = 'grails.banner.art.color'

    private static final String MARK_DISPLAY_PROPERTY = 'grails.banner.mark.display'

    private static final String MARK_TEXT_PROPERTY = 'grails.banner.mark.text'

    private static final String MARK_COLOR_PROPERTY = 'grails.banner.mark.color'

    /** Bright yellow, so the mark reads as its own thing rather than the last line of the art. */
    private static final String DEFAULT_MARK_COLOR = '226'

    /** What an application was started as, strongest first. */
    private static final String NATIVE_MARK = 'NATIVE'
    private static final String CACHE_MARK = 'AOT CACHE'
    private static final String AOT_MARK = 'AOT'

    /** One of the 256 colours a terminal offers: the amber the framework is shown in. */
    private static final String DEFAULT_ART_COLOR = '214'

    private static final String NO_COLOR = 'none'

    /** Shown where an application asked for a version by name and it could not be determined. */
    private static final String UNKNOWN_VERSION = 'unknown'

    private static final String ORDER_PROPERTY = 'grails.banner.versions.order'

    private static final String EXCLUDE_PROPERTY = 'grails.banner.versions.exclude'

    private static final String INCLUDE_PROPERTY = 'grails.banner.versions.include'

    /** Everywhere an application names a version option, and so everywhere one can be misspelt. */
    private static final List<String> VERSION_PROPERTIES =
            [ORDER_PROPERTY, EXCLUDE_PROPERTY, INCLUDE_PROPERTY].asImmutable()

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
            // measured before colouring, so the escapes do not count towards the width the
            // versions below are centred on
            bannerWidth = longestLineLength(art) ?: FALLBACK_BANNER_WIDTH
            out.println(colour(art, environment))
            artPaddingBottom.times { out.println() }
        }
        printMark(environment, out, bannerWidth)
        boolean displayVersions = shouldDisplayVersions(environment)
        if (displayVersions) {
            createVersionsFormatter().format(createBannerVersions(environment), bannerWidth)
                    .forEach { out.println(it) }
        }
        bannerPaddingBottom.times { out.println() }
        if (displayVersions) {
            warnAboutUnrecognisedVersions(environment)
        }
    }

    /**
     * Says which of the version options an application configured were not recognised.
     *
     * <p>An option that names nothing is dropped, and was dropped without a word -- so a name with
     * a typo in it reads as a banner that quietly ignores what it was told, and the only way to
     * find out is to notice a line that is not there.</p>
     *
     * <p>Said after the banner rather than while one is being built. A line logged partway through
     * arrives between the mark and the versions, which is the very thing reading a version without
     * initialising the library it belongs to was for.</p>
     */
    protected void warnAboutUnrecognisedVersions(Environment env) {
        List<String> unrecognised = unrecognisedVersionOptions(env)
        if (unrecognised) {
            log.warn('Ignoring unknown banner version option(s) {}; the known options are {}',
                    unrecognised, VersionOption.values()*.key)
        }
    }

    /**
     * The configured version options that name none of {@link VersionOption}, in the order they
     * were written.
     *
     * <p>Which list an option belongs to is not asked. An option is named by an application to say
     * that it wants it or does not, and moving one between the shown-by-default set and the
     * asked-for set is a decision of this class -- so an application that asked for a version that
     * has since become one of the defaults said something recognisable, and is told nothing.</p>
     */
    protected List<String> unrecognisedVersionOptions(Environment env) {
        List<String> known = VersionOption.values()*.key
        VERSION_PROPERTIES.collectMany { String property ->
            readVersionOptions(env, property).findAll { String option -> !(option in known) }
        }.unique()
    }

    /**
     * Marks how the application was started, centred under the art and above the versions.
     *
     * <p>Only where it is worth saying: an image, a JVM given a cache to read, or one running bean
     * definitions that were generated. An ordinary start says nothing.</p>
     *
     * <p>Written plainly, and deliberately: a banner is printed on the thread that is starting the
     * application, which cannot get on until this returns. Anything drawn over time here is time the
     * application is not starting, and an image that starts in two thirds of a second should not
     * spend a third of it on its own announcement.</p>
     */
    protected void printMark(Environment environment, PrintStream out, int bannerWidth) {
        if (!environment.getProperty(MARK_DISPLAY_PROPERTY, Boolean, true)) {
            return
        }
        String mark = resolveMark(environment)
        if (!mark) {
            return
        }
        String label = spaced(mark)
        String indent = ' ' * Math.max(0, (bannerWidth - label.length()).intdiv(2))
        AnsiElement colour = resolveMarkColour(environment)
        out.println(indent + (colour == null ? label : AnsiOutput.toString(colour, label, AnsiColor.DEFAULT)))
    }

    /**
     * What to say, strongest first, or nothing where an application started the ordinary way.
     *
     * <p>An image has already done all of it. A cache means the JDK was handed what a previous run
     * worked out. Generated bean definitions are the smaller half of the same idea, and worth
     * saying on their own because an application can be run either with them or without.</p>
     */
    protected String resolveMark(Environment environment) {
        String configured = environment.getProperty(MARK_TEXT_PROPERTY, String)
        if (configured != null) {
            return configured.trim() ?: null
        }
        if (isNativeImage()) {
            return NATIVE_MARK
        }
        if (readsAotCache()) {
            return CACHE_MARK
        }
        AotDetector.useGeneratedArtifacts() ? AOT_MARK : null
    }

    /**
     * Whether the JDK was given a cache to read.
     *
     * <p>Asked of the arguments the JVM was started with rather than of the cache: a JVM handed one
     * it cannot use declines it and starts as it would have anyway, and this is the banner rather
     * than a diagnostic.</p>
     */
    protected boolean readsAotCache() {
        ManagementFactory.runtimeMXBean.inputArguments.any { String argument ->
            argument.startsWith('-XX:AOTCache=') || argument.startsWith('-XX:AOTMode=')
        }
    }

    /**
     * Spaced so it reads as a mark rather than a word.
     *
     * <p>Spaced as written rather than shouted: the marks this draws are already upper case, and
     * an application that says what it wants the mark to be gets what it said. A name whose case
     * is part of it -- CRaC -- keeps it.</p>
     */
    private static String spaced(String word) {
        word.toCharArray().join(' ')
    }

    /** Whether this is running as an image. A seam, so the mark can be covered without being one. */
    protected boolean isNativeImage() {
        NativeDetector.inNativeImage()
    }

    /**
     * The art in the configured colour, or as it stands where none is wanted.
     *
     * <p>{@link AnsiOutput} writes the escapes only where colour has been enabled, so this is the
     * same string on a terminal that cannot colour, in a redirected log, and wherever an application
     * has turned colour off.</p>
     */
    protected String colour(String art, Environment environment) {
        AnsiElement colour = resolveArtColour(environment)
        colour == null ? art : AnsiOutput.toString(colour, art, AnsiColor.DEFAULT)
    }

    /**
     * The colour to show the art in, read from {@code grails.banner.art.color}.
     *
     * <p>Takes a number for one of the 256 colours a terminal offers, a name for one of the eight
     * it has always had ({@code red}, {@code bright_blue}), or {@code none} to leave the art as it
     * stands. A value that is neither falls back to the default rather than failing to start over
     * the colour of a banner.</p>
     */
    protected AnsiElement resolveArtColour(Environment environment) {
        resolveColour(environment, ART_COLOR_PROPERTY, DEFAULT_ART_COLOR)
    }

    /**
     * The colour to show the mark in, read from {@code grails.banner.mark.color}.
     *
     * <p>Its own colour rather than the art's, and brighter, so that what an application was started
     * as is not read as the last line of the drawing above it. Takes the same values as
     * {@code grails.banner.art.color}.</p>
     */
    protected AnsiElement resolveMarkColour(Environment environment) {
        resolveColour(environment, MARK_COLOR_PROPERTY, DEFAULT_MARK_COLOR)
    }

    private AnsiElement resolveColour(Environment environment, String property, String fallback) {
        String configured = environment.getProperty(property, String, fallback)
        if (!configured || configured.equalsIgnoreCase(NO_COLOR)) {
            return null
        }
        if (configured.isInteger()) {
            int code = configured.toInteger()
            // A terminal offers 256 of them, and anything else writes an escape it does not
            // understand -- which shows as the escape itself, printed into the banner.
            return code in 0..255 ? Ansi8BitColor.foreground(code) : defaultColour(fallback)
        }
        try {
            return AnsiColor.valueOf(configured.toUpperCase())
        }
        catch (IllegalArgumentException ignored) {
            return defaultColour(fallback)
        }
    }

    private static AnsiElement defaultColour(String fallback) {
        Ansi8BitColor.foreground(fallback.toInteger())
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
    protected Map<String,String> createBannerVersions(Environment env) {
        def defaultIncluded = (DefaultVersionOption.values()).collect { it.key }
        def sortOrder = findConfiguredVersions(env, ORDER_PROPERTY)
        def configExcluded = findConfiguredVersions(env, EXCLUDE_PROPERTY)
        def configIncluded = findConfiguredVersions(env, INCLUDE_PROPERTY)
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
        // A version that cannot be determined is left out where it is shown by default, and shown
        // as unknown where the application asked for it by name. Leaving out a default is what
        // lets one be on at all -- an application without Spring Security says nothing about it
        // rather than saying it does not know -- but leaving out one that was asked for reads as
        // the option having been ignored, which is the thing an unrecognised option is warned about.
        Map<String, String> versions = [:]
        for (String key : includedVersions) {
            versionsFor(key, env).each { String label, String version ->
                if (version != null) {
                    versions[label] = version
                }
                else if (key in configIncluded) {
                    versions[label] = UNKNOWN_VERSION
                }
            }
        }
        versions
    }

    /**
     * What one version option contributes, as the labels it is shown under and what each records.
     *
     * <p>A value of {@code null} is a library that is absent or records nothing; the caller decides
     * whether that is left out or shown as unknown. An option that contributes nothing at all --
     * the container, where none of them is there -- contributes no label to decide about.</p>
     */
    protected Map<String, String> versionsFor(String key, Environment env) {
        switch (VersionOption.fromString(key)) {
            case VersionOption.APP:
                String appName = env.getProperty('info.app.name') ?: 'app'
                return [(appName): env.getProperty('info.app.version') ?: UNKNOWN_VERSION]
            case VersionOption.JVM:
                return ['JVM': System.getProperty('java.vendor') + ' ' + System.getProperty('java.version')]
            case VersionOption.GRAILS:
                return ['Grails': BuildSettings.grailsVersion]
            case VersionOption.GROOVY:
                return ['Groovy': GroovySystem.version]
            case VersionOption.SPRING_BOOT:
                return ['Spring Boot': SpringBootVersion.version]
            case VersionOption.SPRING:
                return ['Spring': SpringVersion.version]
            case VersionOption.SPRING_SECURITY:
                return ['Spring Security': findVersion('org.springframework.security.core.SpringSecurityCoreVersion')]
            case VersionOption.CONTAINER:
                return findContainerVersion()
            case VersionOption.TOMCAT:
                return ['Tomcat': findTomcatVersion()]
            case VersionOption.JETTY:
                return ['Jetty': findJettyVersion()]
            case VersionOption.UNDERTOW:
                return ['Undertow': findUndertowVersion()]
            default:
                return [:]
        }
    }

    /**
     * The servlet container the application is running on, and the version it records.
     *
     * <p>An application runs on one container: choosing another is done by excluding the starter
     * for this one, so two are not on the classpath together. They are therefore tried in the order
     * they are commonly used and the first one found is the answer -- an application on Tomcat
     * never goes looking for Jetty.</p>
     *
     * <p>On a container that records no version, or on none of these, this is empty and the banner
     * leaves the line out rather than saying it does not know.</p>
     */
    protected Map<String, String> findContainerVersion() {
        String tomcat = findTomcatVersion()
        if (tomcat != null) {
            return ['Tomcat': tomcat]
        }
        String jetty = findJettyVersion()
        if (jetty != null) {
            return ['Jetty': jetty]
        }
        String undertow = findUndertowVersion()
        if (undertow != null) {
            return ['Undertow': undertow]
        }
        return [:]
    }

    /**
     * Tomcat's version, read from the resource it ships rather than only from its manifest.
     *
     * <p>A resource survives being repackaged into an executable jar or built into an image, where
     * the manifest's attributes are no longer attached to the package -- which is why the manifest
     * route reads as nothing in exactly the two places a version is most worth having.</p>
     */
    protected String findTomcatVersion() {
        findVersionInResource('org/apache/catalina/util/ServerInfo.properties', 'server.number')
                ?: findVersion('org.apache.catalina.util.ServerInfo')
    }

    protected String findJettyVersion() {
        findVersion('org.eclipse.jetty.util.Jetty')
    }

    protected String findUndertowVersion() {
        findVersion('io.undertow.Undertow')
    }

    /**
     * A version a library records in a resource it ships, read without loading any of its classes.
     *
     * <p>The manifest route only works while a jar is a plain entry on the classpath. Repackaged
     * into an executable jar its attributes are no longer attached to the package, and an image has
     * no jars at all -- which is why a container version read that way reads as nothing in exactly
     * the two places it is most worth having. A resource is still a resource in both.</p>
     *
     * @param resource the classpath location of the resource to read
     * @param key the property within it that carries the version
     * @return the version, or {@code null} where the resource or the property is absent
     */
    protected static String findVersionInResource(String resource, String key) {
        InputStream stream = GrailsBanner.classLoader.getResourceAsStream(resource)
        if (stream == null) {
            return null
        }
        try {
            Properties properties = new Properties()
            stream.withCloseable { properties.load(it) }
            return properties.getProperty(key)
        }
        catch (IOException ignored) {
            return null
        }
    }

    /**
     * The version a library records in the manifest of the jar it ships in.
     *
     * <p>Loaded without being initialised. A version is read <em>about</em> a library rather than
     * <em>from</em> it, and running a static initialiser to find one lets the library do whatever it
     * does on the way -- Spring Security logs a line of its own from there, which arrived in the
     * middle of the banner, between the mark and the very versions it was being read for. The
     * manifest is attached to the package when the class is loaded, and loading is all this
     * needs.</p>
     *
     * @param className the fully qualified name of a class the library ships
     * @return the version, or {@code null} where the class is absent or records none
     */
    protected static String findVersion(String className) {
        try {
            Package pkg = Class.forName(className, false, GrailsBanner.classLoader).package
            return pkg?.implementationVersion
        } catch (ClassNotFoundException ignore) {
            return null
        }
    }

    /**
     * The version options configured under the given property that name something.
     *
     * @param env the current environment
     * @param propertyName the property name to look for
     * @return the options that name a {@link VersionOption}, in the order they were written
     */
    private static List<String> findConfiguredVersions(Environment env, String propertyName) {
        List<String> known = VersionOption.values()*.key
        readVersionOptions(env, propertyName).findAll { String option -> option in known }
    }

    /** What an application wrote under the given property, or nothing where it wrote none. */
    private static List<String> readVersionOptions(Environment env, String propertyName) {
        env.getProperty(propertyName, List<String>, [] as List<String>)
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
        CONTAINER,
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
        SPRING,
        SPRING_SECURITY,
        CONTAINER

        final String key

        DefaultVersionOption() {
            this.key = name().toLowerCase().replace('_', '-')
        }
    }

    /**
     * Enumeration of optional version options.
     *
     * <p>The container being run is shown by default under {@code container}, which is the one an
     * application is on. These name a particular container instead, for an application that wants
     * to be told about one whether or not it is the one serving.</p>
     */
    @CompileStatic
    enum OptionalVersionOption {
        TOMCAT,
        JETTY,
        UNDERTOW

        final String key

        OptionalVersionOption() {
            this.key = name().toLowerCase().replace('_', '-')
        }
    }
}
