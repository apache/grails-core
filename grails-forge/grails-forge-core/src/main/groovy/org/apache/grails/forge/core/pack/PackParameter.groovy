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
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.options.Options

/**
 * An option of the feature of a pack, which the descriptor declares under {@code parameters}: a name,
 * a type, the value it has when it is not given, and what it is for. Whoever creates the application
 * gives it a value as an option of the feature ({@link Options#getFeatureOptions()}), and the templates
 * and the conditions of the pack read it.
 *
 * <p>A value is text of one line ({@code string}), {@code true} or {@code false} ({@code boolean}), or
 * one of the values the descriptor lists ({@code choice}). It is data: nothing of it is run, and where
 * it is written is for the templates of the pack to say.</p>
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true, includePackage = false)
final class PackParameter {

    /** A value that is one line of text */
    public static final String STRING = 'string'

    /** A value that is {@code true} or {@code false} */
    public static final String BOOLEAN = 'boolean'

    /** A value that is one of those the descriptor lists */
    public static final String CHOICE = 'choice'

    /** The types a parameter can have */
    public static final List<String> TYPES = List.of(STRING, BOOLEAN, CHOICE)

    /** The most parameters a pack can declare */
    public static final int MAX = 20

    /** The most characters a value that is text can have */
    public static final int MAX_VALUE = 200

    /** The name of the parameter, which a template reads it by */
    final String name

    /** One of {@link #TYPES} */
    final String type

    /** The value when none is given: text, or a {@link Boolean} for a {@link #BOOLEAN} */
    final Object defaultValue

    /** The values a {@link #CHOICE} can have; empty for the other types */
    final List<String> choices

    /** What the parameter is for, or {@code null} */
    final String description

    PackParameter(String name, String type, Object defaultValue, List<String> choices, String description) {
        this.name = name
        this.type = type
        this.defaultValue = defaultValue
        this.choices = List.copyOf(choices)
        this.description = description
    }

    /**
     * @param text a value of text
     * @return whether it can be the value of a {@link #STRING} or one of a {@link #CHOICE}: one line that
     * is not blank, of at most {@link #MAX_VALUE} characters, with nothing a terminal acts on and without
     * the start of the markup of the command line. It is the text a record of a project can hold
     */
    static boolean isText(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_VALUE && !PackDescriptorParser.hasUnsafeCharacter(text) && !text.contains('@|')
    }

    /**
     * @param given a value as it is given on a command line or written in a record
     * @return why it cannot be the value of this parameter, or {@code null} when it can
     */
    String refusal(String given) {
        switch (type) {
            case BOOLEAN:
                return given == 'true' || given == 'false' ? null : 'must be true or false'
            case CHOICE:
                return choices.contains(given) ? null : "must be one of ${choices}".toString()
            default:
                return isText(given) ? null : "must be one line of text of at most ${MAX_VALUE} characters that is not blank and has no control characters".toString()
        }
    }

    /**
     * @param given a value {@link #refusal} does not refuse
     * @return the value as a template has it: a {@link Boolean} for a {@link #BOOLEAN}, else the text
     */
    Object read(String given) {
        return type == BOOLEAN ? (Object) Boolean.valueOf(given) : given
    }

    /**
     * @return what the parameter takes, on one line: {@code string}, {@code true|false} or the choices, and the default
     */
    String getUsage() {
        String values = type == BOOLEAN ? 'true|false' : type == CHOICE ? choices.join('|') : 'text'
        return "${name}=${values} (default ${defaultValue})".toString()
    }

    /**
     * The value of every parameter of a pack for one application: the one that was given, when it can
     * be the value, and else the default. What cannot be a value is refused by {@link #check}, before
     * anything is generated; here it gives way to the default.
     *
     * @param descriptor the descriptor of the pack
     * @param given the options that were given for the feature of the pack, by their names, or {@code null}
     * @return the values by the names of the parameters, in the order of the names, which cannot be changed
     */
    static Map<String, Object> values(PackDescriptor descriptor, Map<String, String> given) {
        Map<String, Object> values = new TreeMap<>()
        for (PackParameter parameter : descriptor.parameters) {
            String value = given?.get(parameter.name)
            values.put(parameter.name, value != null && parameter.refusal(value) == null ? parameter.read(value) : parameter.defaultValue)
        }
        return Collections.unmodifiableMap(values)
    }

    /**
     * @param descriptor the descriptor of a pack
     * @param options the options of an application, or {@code null}
     * @return the value of every parameter of the pack for that application, see {@link #values(PackDescriptor, Map)}
     */
    static Map<String, Object> values(PackDescriptor descriptor, Options options) {
        return values(descriptor, options == null ? null : options.getFeatureOptions().get(descriptor.name))
    }

    /**
     * Holds the options that were given for features to the features the application has: each is the
     * option of a feature of the application, which is the feature of a pack that declares a parameter
     * of that name, and its value is one the parameter can have.
     *
     * @param options the options of the application
     * @param features the features of the application
     * @throws FeatureSelectionException for an option that is none: a mistake in what was asked for
     */
    static void check(Options options, Collection<? extends Feature> features) {
        options.getFeatureOptions().each { String featureName, Map<String, String> given ->
            String shown = FeatureSelectionException.shown(featureName)
            Feature feature = features.find { Feature each -> each.getName() == featureName }
            if (feature == null) {
                throw new FeatureSelectionException("An option was given for the feature ${shown}, which the application does not have: " +
                        'an option is that of a feature that is selected')
            }
            List<PackParameter> declared = feature instanceof PackFeature ? ((PackFeature) feature).getPack().descriptor.parameters :
                    Collections.<PackParameter>emptyList()
            given.each { String name, String value ->
                PackParameter parameter = declared.find { PackParameter each -> each.name == name }
                if (parameter == null) {
                    throw new FeatureSelectionException("The feature ${shown} has no option ${FeatureSelectionException.shown(name)}: " +
                            (declared.isEmpty() ? 'it takes no options' : "its options are ${declared.collect { PackParameter each -> each.usage }.join(', ')}"))
                }
                String refusal = parameter.refusal(value)
                if (refusal != null) {
                    throw new FeatureSelectionException("The option ${parameter.name} of the feature ${shown} ${refusal}, " +
                            "but is '${PackDescriptorParser.shown(value, 80)}'")
                }
            }
        }
    }
}
