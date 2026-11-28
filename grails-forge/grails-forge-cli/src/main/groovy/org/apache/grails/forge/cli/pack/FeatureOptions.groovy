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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackParameter
import org.grails.forge.feature.FeatureSelectionException

/**
 * The options of features as a command line gives them, with {@code --feature-option}: each is
 * {@code FEATURE.NAME=VALUE}, the name of a feature, the name of one of the options its feature pack
 * declares as {@code parameters}, and the value. Neither the name of a feature nor that of an option
 * has a dot or an equals sign, so the first dot and the first equals sign after it are where the three
 * parts end, and the value is everything after, as it is.
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureOptions {

    /** The option of a command line that gives an option of a feature */
    public static final String OPTION = '--feature-option'

    /** How a value of the option is written */
    public static final String LABEL = 'FEATURE.NAME=VALUE'

    private static final Pattern GIVEN = Pattern.compile(/(?s)([a-z][a-z0-9]*(?:-[a-z0-9]+)*)\.([a-z][A-Za-z0-9]{0,39})=(.*)/)

    private FeatureOptions() {
    }

    /**
     * @param given the values of {@code --feature-option}
     * @return the options by the name of the feature and then of the option, both in the order of the names
     * @throws IllegalArgumentException for a value that is not written as one, and for an option that is
     * given two values
     */
    static Map<String, Map<String, String>> parse(List<String> given) {
        Map<String, Map<String, String>> options = new TreeMap<>()
        for (String each : given) {
            Matcher matcher = GIVEN.matcher(each)
            if (!matcher.matches()) {
                throw new IllegalArgumentException("${OPTION} takes ${LABEL}: the name of a feature, a dot, the name of an option of that feature and its value, " +
                        "as in acme-audit.store=collection, but is ${FeatureSelectionException.shown(each)}")
            }
            Map<String, String> ofFeature = options.computeIfAbsent(matcher.group(1)) { String feature -> new TreeMap<String, String>() }
            String earlier = ofFeature.put(matcher.group(2), matcher.group(3))
            if (earlier != null && earlier != matcher.group(3)) {
                throw new IllegalArgumentException("${OPTION} gives the option ${matcher.group(2)} of the feature ${matcher.group(1)} two values")
            }
        }
        return options
    }

    /**
     * @param options options as {@link #parse} gives them, or as a record of a project has them
     * @return each as it is given on a command line, in the order of the names
     */
    static List<String> written(Map<String, Map<String, String>> options) {
        List<String> written = []
        options.each { String feature, Map<String, String> ofFeature ->
            ofFeature.each { String name, String value -> written << "${feature}.${name}=${value}".toString() }
        }
        return written
    }

    /**
     * @param packs feature packs
     * @return what the features of those that declare parameters take, for whoever is about to give
     * them: a line that names the feature, and under it a line for each option with its values, its
     * default and what it is for. The text is that of descriptors, as the parser let it through
     */
    static List<String> listing(Collection<Pack> packs) {
        List<String> lines = []
        for (Pack pack : packs) {
            if (pack.descriptor.parameters.isEmpty()) {
                continue
            }
            lines << "The feature ${pack.descriptor.name} takes options, each given as ${OPTION} ${pack.descriptor.name}.NAME=VALUE:".toString()
            for (PackParameter parameter : pack.descriptor.parameters) {
                lines << "    ${parameter.usage}${parameter.description == null ? '' : '  ' + parameter.description}".toString()
            }
        }
        return lines
    }
}
