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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl

/**
 * What only the descriptor of a preset says, a pack whose {@code kind} is {@code preset}: the one type
 * of application it is for, and the options it gives an application that the command line does not.
 * The features a preset asks for are the {@link PackDescriptor#requires} of its descriptor, which are
 * selected with it as the requirements of a feature are, and what it adds to the application is what a
 * feature pack adds.
 *
 * <p>The options of a preset are defaults: an option that is given on the command line is the one the
 * application gets, and one that neither the command line nor the preset gives is the default of the
 * framework. An option a preset gives is not a choice the features have to keep to, as an option that is
 * given is: it is what the application gets when nothing else decides.</p>
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true, includePackage = false)
final class PackPreset {

    /** The {@code kind} of the descriptor of a preset */
    public static final String KIND = 'preset'

    /** The options a preset can give, by their names in the descriptor, which are those of the create commands */
    public static final List<String> OPTIONS = List.of('data', 'servlet', 'reloading', 'jdk')

    /** The type of application the preset is for, the only one */
    final ApplicationType applicationType

    /** The Grails Data implementation, or {@code null} when the preset gives none */
    final GormImpl data

    /** The servlet container, or {@code null} when the preset gives none */
    final ServletImpl servlet

    /** How the application reloads in development, or {@code null} when the preset gives none */
    final DevelopmentReloading reloading

    /** The version of Java, or {@code null} when the preset gives none */
    final JdkVersion jdk

    PackPreset(ApplicationType applicationType, GormImpl data, ServletImpl servlet, DevelopmentReloading reloading, JdkVersion jdk) {
        this.applicationType = applicationType
        this.data = data
        this.servlet = servlet
        this.reloading = reloading
        this.jdk = jdk
    }

    /**
     * The options of an application made from the preset: each one that was given, else the one of the
     * preset, else the default of the framework ({@code null} for the generator to choose it, and the
     * default version of Java).
     *
     * @param givenReloading the reloading that was given, or {@code null}
     * @param givenData the Grails Data implementation that was given, or {@code null}
     * @param givenServlet the servlet container that was given, or {@code null}
     * @param givenJdk the version of Java that was given, or {@code null}
     * @param operatingSystem the operating system the application is generated for
     * @return the options, none of them marked as given: marking is for the caller, which knows what was typed
     */
    Options optionsWith(DevelopmentReloading givenReloading, GormImpl givenData, ServletImpl givenServlet, JdkVersion givenJdk,
                        OperatingSystem operatingSystem) {
        return new Options(givenReloading ?: reloading, givenData ?: data, givenServlet ?: servlet,
                givenJdk ?: jdk ?: JdkVersion.DEFAULT_OPTION, operatingSystem)
    }

    /**
     * @return the options the preset gives, by their names in the descriptor, with their values as the
     * create commands take them, in the order of {@link #OPTIONS}
     */
    Map<String, String> getOptions() {
        Map<String, String> options = new LinkedHashMap<>()
        if (data != null) {
            options.put('data', data.name().toLowerCase(Locale.ROOT))
        }
        if (servlet != null) {
            options.put('servlet', servlet.name().toLowerCase(Locale.ROOT))
        }
        if (reloading != null) {
            options.put('reloading', reloading.name().toLowerCase(Locale.ROOT))
        }
        if (jdk != null) {
            options.put('jdk', String.valueOf(jdk.majorVersion()))
        }
        return Collections.unmodifiableMap(options)
    }

    /**
     * @param type a type of application
     * @return its name as a descriptor and the create commands write it
     */
    static String nameOf(ApplicationType type) {
        return type.getName().replace('_', '-')
    }
}
