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
package org.apache.grails.forge.core.state

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

/**
 * The options an application was generated with, written as the create commands take them:
 * {@code jdk: 21}, {@code servlet: tomcat}, {@code data: hibernate7}, {@code reloading: devtools}.
 * An application that was created as a project of a Gradle build that is not its own also has
 * {@code layout: subproject}; one that is a build of its own has no {@code layout}. An application
 * that was given options of features, which feature packs declare as {@code parameters}, has them
 * under {@code featureOptions}, by the name of the feature and then of the option; one that was given
 * none has no {@code featureOptions}.
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode(excludes = 'notRecorded')
final class GenerationOptions {

    /** The options a record has to have, by the keys they are written with. */
    public static final List<String> RECORDED = Collections.unmodifiableList(['jdk', 'servlet', 'data', 'reloading'])

    final JdkVersion jdk
    final ServletImpl servlet
    final GormImpl data
    final DevelopmentReloading reloading

    /** Whether the application has only its own files, and not those of the root of a Gradle build. */
    final boolean subproject

    /** The options that were given for features, by the name of the feature and then of the option, both sorted. */
    final Map<String, Map<String, String>> featureOptions

    /**
     * The keys of the options the record did not have, for which this has what a create command takes
     * when the option is not given. Empty unless the record was read for showing it, see
     * {@link ProjectState#readSelection(java.nio.file.Path, boolean)}; two sets of options that differ
     * in this alone are equal.
     */
    final Set<String> notRecorded

    /** What {@code layout} is for an application that is a project of a build that is not its own. */
    public static final String SUBPROJECT = 'subproject'

    /** The key the options of features are written under. */
    public static final String FEATURE_OPTIONS = 'featureOptions'

    /** The most options one feature can be given. */
    public static final int MAX_FEATURE_OPTIONS = 20

    GenerationOptions(JdkVersion jdk, ServletImpl servlet, GormImpl data, DevelopmentReloading reloading) {
        this(jdk, servlet, data, reloading, false)
    }

    GenerationOptions(JdkVersion jdk, ServletImpl servlet, GormImpl data, DevelopmentReloading reloading, boolean subproject) {
        this(jdk, servlet, data, reloading, subproject, Collections.<String, Map<String, String>>emptyMap())
    }

    GenerationOptions(JdkVersion jdk, ServletImpl servlet, GormImpl data, DevelopmentReloading reloading, boolean subproject,
                      Map<String, Map<String, String>> featureOptions) {
        this(jdk, servlet, data, reloading, subproject, featureOptions, Collections.<String>emptySet())
    }

    @SuppressWarnings('ParameterCount')
    private GenerationOptions(JdkVersion jdk, ServletImpl servlet, GormImpl data, DevelopmentReloading reloading, boolean subproject,
                              Map<String, Map<String, String>> featureOptions, Set<String> notRecorded) {
        this.notRecorded = Collections.unmodifiableSet(new LinkedHashSet<String>(notRecorded))
        Map<String, Map<String, String>> sorted = new TreeMap<>()
        featureOptions.each { String feature, Map<String, String> values ->
            if (!values.isEmpty()) {
                sorted.put(feature, Collections.unmodifiableMap(new TreeMap<String, String>(values)))
            }
        }
        this.featureOptions = Collections.unmodifiableMap(sorted)
        this.jdk = jdk
        this.servlet = servlet
        this.data = data
        this.reloading = reloading
        this.subproject = subproject
    }

    @PackageScope
    static GenerationOptions of(Options options) {
        return new GenerationOptions(options.getJavaVersion(), options.getServletImpl(), options.getGormImpl(), options.getDevelopmentReloading(), options.isSubproject(),
                options.getFeatureOptions())
    }

    @PackageScope
    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('jdk', jdk.majorVersion())
        map.put('servlet', written(servlet))
        map.put('data', written(data))
        map.put('reloading', written(reloading))
        if (subproject) {
            // written only for such an application, so that the state of every other one is what it was
            map.put('layout', SUBPROJECT)
        }
        if (!featureOptions.isEmpty()) {
            // written only when there are some, for the same reason
            Map<String, Object> ofFeatures = new LinkedHashMap<>()
            featureOptions.each { String feature, Map<String, String> values -> ofFeatures.put(feature, new LinkedHashMap<String, Object>(values)) }
            map.put(FEATURE_OPTIONS, ofFeatures)
        }
        return map
    }

    @PackageScope
    static GenerationOptions read(StateSection section) {
        return read(section, false)
    }

    /**
     * @param missingAsDefaults whether an option the section does not have is the one a create command
     * takes when the option is not given, and is named by {@link #notRecorded}; otherwise it is refused
     */
    @PackageScope
    static GenerationOptions read(StateSection section, boolean missingAsDefaults) {
        Set<String> notRecorded = missingAsDefaults ? RECORDED.findAll { String key -> !section.has(key) }.toSet() : Collections.<String>emptySet()
        JdkVersion jdk = JdkVersion.DEFAULT_OPTION
        if (!notRecorded.contains('jdk')) {
            int major = section.number('jdk', 1, 999)
            jdk = JdkVersion.values().find { JdkVersion each -> each.majorVersion() == major }
            if (jdk == null) {
                throw section.problem('jdk', "is ${major}, a version of Java this version of Grails does not generate for")
            }
        }
        GenerationOptions options = new GenerationOptions(
                jdk,
                notRecorded.contains('servlet') ? ServletImpl.DEFAULT_OPTION : ServletImpl.valueOf(named(section, 'servlet', ServletImpl.values() as List<Enum>)),
                notRecorded.contains('data') ? GormImpl.DEFAULT_OPTION : GormImpl.valueOf(named(section, 'data', GormImpl.values() as List<Enum>)),
                notRecorded.contains('reloading') ? DevelopmentReloading.DEFAULT_OPTION :
                        DevelopmentReloading.valueOf(named(section, 'reloading', DevelopmentReloading.values() as List<Enum>)),
                section.has('layout') && section.oneOf('layout', [SUBPROJECT]) == SUBPROJECT,
                section.has(FEATURE_OPTIONS) ? featureOptions(section.section(FEATURE_OPTIONS)) : Collections.<String, Map<String, String>>emptyMap(),
                notRecorded)
        section.end()
        return options
    }

    // the options of each feature, as one line of text each: what they have to be is for the pack that declares them to say
    private static Map<String, Map<String, String>> featureOptions(StateSection section) {
        Map<String, Map<String, String>> ofFeatures = new LinkedHashMap<>()
        List<String> features = section.keys()
        if (features.size() > StateLimits.MAX_FEATURES) {
            throw section.problem('', "has ${features.size()} features, and can have ${StateLimits.MAX_FEATURES}")
        }
        for (String feature : features) {
            Map<String, String> values = section.texts(feature, MAX_FEATURE_OPTIONS)
            if (values.isEmpty()) {
                throw section.problem(feature, 'has no option: a feature without options is not written')
            }
            ofFeatures.put(feature, values)
        }
        section.end()
        return ofFeatures
    }

    /**
     * @param operatingSystem the operating system the generator is told of
     * @return the options an application is generated again with
     */
    Options toOptions(OperatingSystem operatingSystem) {
        return new Options(reloading, data, servlet, jdk, operatingSystem).withSubproject(subproject).withFeatureOptions(featureOptions)
    }

    @PackageScope
    static String written(Enum value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-')
    }

    private static String named(StateSection section, String key, List<Enum> values) {
        String value = section.oneOf(key, values.collect { Enum each -> written(each) })
        return values.find { Enum each -> written(each) == value }.name()
    }
}
