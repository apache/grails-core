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
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.options.Options

/**
 * A condition of a descriptor: what has to hold of the application for a feature to be a default, for a
 * feature to be added with another, or for one dependency, setting or file of a feature to be there. It
 * is one language wherever a descriptor has a condition:
 *
 * <pre>
 * { feature: gsp }                              the feature is there
 * { group: database-driver }                    a feature of the exclusive group is there
 * { anyOf: [gorm-hibernate5, gorm-hibernate7] } one of them holds: each a feature name or a condition
 * { allOf: [gsp, { not: openapi }] }            all of them hold
 * { not: gsp }                                  it does not hold: a feature name or a condition
 * { applicationType: [web, web-plugin] }        the application is of one of the types
 * { option: { data: hibernate7 } }              the option has the value, or one of a list of values
 * { jdkAtLeast: 25 }                            the JDK of the application is that or later
 * { parameter: { store: collection } }          the parameter of the pack has the value, or one of a list of values
 * </pre>
 *
 * <p>It is evaluated against the features there are at that moment: those selected so far for a
 * default, those selected when the selection is processed for an addition, and the features of the
 * application for a contribution.</p>
 *
 * @since 9.0
 */
@CompileStatic
@EqualsAndHashCode
final class PackCondition {

    public static final String FEATURE = 'feature'
    public static final String GROUP = 'group'
    public static final String ANY_OF = 'anyOf'
    public static final String ALL_OF = 'allOf'
    public static final String NOT = 'not'
    public static final String APPLICATION_TYPE = 'applicationType'
    public static final String OPTION = 'option'
    public static final String JDK_AT_LEAST = 'jdkAtLeast'
    public static final String PARAMETER = 'parameter'

    /** The options a condition can ask, each with the values it can have, as the create commands take them */
    public static final Map<String, List<String>> OPTIONS = Collections.unmodifiableMap([
            data: List.of('hibernate5', 'hibernate7', 'mongodb', 'neo4j'),
            servlet: List.of('tomcat', 'jetty', 'undertow', 'none'),
            reloading: List.of('devtools', 'jrebel', 'none'),
    ] as Map<String, List<String>>)

    /** The condition that always holds */
    public static final PackCondition ALWAYS = new PackCondition(ALL_OF, null, [], [])

    /** What is asked: one of the keys of the language */
    final String kind
    /** The option that is asked, a key of {@link #OPTIONS}, or the parameter of the pack that is asked, or {@code null} */
    final String option
    /** The feature, the group, the application types, the values of the option or the JDK that is asked */
    final List<String> values
    /** The conditions this one is made of */
    final List<PackCondition> conditions

    PackCondition(String kind, String option, List<String> values, List<PackCondition> conditions) {
        this.kind = kind
        this.option = option
        this.values = List.copyOf(values)
        this.conditions = List.copyOf(conditions)
    }

    static PackCondition feature(String name) {
        return new PackCondition(FEATURE, null, [name], [])
    }

    /**
     * @return whether the condition holds of what is known
     */
    boolean test(Facts facts) {
        switch (kind) {
            case FEATURE:
                return facts.hasFeature(values[0])
            case GROUP:
                return facts.hasGroup(values[0])
            case ANY_OF:
                return conditions.any { PackCondition each -> each.test(facts) }
            case ALL_OF:
                return conditions.every { PackCondition each -> each.test(facts) }
            case NOT:
                return !conditions[0].test(facts)
            case APPLICATION_TYPE:
                return values.contains(typeName(facts.getApplicationType()))
            case OPTION:
                return values.contains(optionValue(facts.getOptions(), option))
            case JDK_AT_LEAST:
                return facts.getOptions() != null && facts.getOptions().getJavaVersion() != null &&
                        facts.getOptions().getJavaVersion().majorVersion() >= Integer.parseInt(values[0])
            case PARAMETER:
                // what is known without the pack knows no parameter, and then the condition does not hold
                Object value = facts.getParameter(option)
                return value != null && values.contains(value.toString())
            default:
                throw new IllegalStateException("A condition cannot ask '${kind}'")
        }
    }

    /**
     * @return whether the condition asks for nothing but the type of the application
     */
    boolean isOfApplicationType() {
        return (kind == APPLICATION_TYPE || kind == ANY_OF || kind == ALL_OF || kind == NOT) &&
                conditions.every { PackCondition each -> each.isOfApplicationType() }
    }

    /**
     * @return the names of the features the condition asks for, wherever in it
     */
    Set<String> getFeatures() {
        Set<String> names = new TreeSet<>()
        if (kind == FEATURE) {
            names.add(values[0])
        }
        for (PackCondition each : conditions) {
            names.addAll(each.getFeatures())
        }
        return names
    }

    /**
     * @return the condition as a descriptor says it, on one line
     */
    @Override
    String toString() {
        switch (kind) {
            case FEATURE:
                return values[0]
            case NOT:
                return "not ${conditions[0]}"
            case ANY_OF:
            case ALL_OF:
                return this == ALWAYS ? 'always' : "${kind}(${conditions.join(', ')})"
            case OPTION:
                return "${option}=${values.join('|')}"
            case PARAMETER:
                return "parameter ${option}=${values.join('|')}"
            default:
                return "${kind}=${values.join('|')}"
        }
    }

    static String typeName(ApplicationType type) {
        return type == null ? null : type.getName().replace('_', '-')
    }

    private static String optionValue(Options options, String option) {
        if (options == null) {
            return null
        }
        switch (option) {
            case 'data':
                return options.getGormImpl()?.name()?.toLowerCase(Locale.ROOT)
            case 'servlet':
                return options.getServletImpl()?.name()?.toLowerCase(Locale.ROOT)
            default:
                return options.getDevelopmentReloading()?.getName()
        }
    }

    /**
     * @return what is known while the features are selected: those selected so far
     */
    static Facts of(ApplicationType applicationType, Options options, Collection<Feature> selected) {
        return new Selected(applicationType, options, selected)
    }

    /**
     * @return what is known while the selection is processed, which changes as features are added
     */
    static Facts of(FeatureContext featureContext) {
        return new Processing(featureContext)
    }

    /**
     * @return what is known of the application that is generated
     */
    static Facts of(GeneratorContext generatorContext) {
        return new Selected(generatorContext.getApplicationType(), generatorContext.getOptions(), generatorContext.getFeatures().getFeatures())
    }

    /**
     * @param facts what is known of an application
     * @param parameters the values of the parameters of one pack for it, by their names
     * @return what is known of the application for the conditions of that pack
     */
    static Facts withParameters(Facts facts, Map<String, Object> parameters) {
        return new OfPack(facts, parameters)
    }

    /**
     * What a condition is evaluated against.
     */
    interface Facts {

        boolean hasFeature(String name)

        boolean hasGroup(String group)

        ApplicationType getApplicationType()

        Options getOptions()

        /**
         * @return the value of a parameter of the pack the condition is of, or {@code null} when it is not known
         */
        Object getParameter(String name)
    }

    @CompileStatic
    private static final class OfPack implements Facts {

        private final Facts facts
        private final Map<String, Object> parameters

        OfPack(Facts facts, Map<String, Object> parameters) {
            this.facts = facts
            this.parameters = parameters
        }

        @Override
        boolean hasFeature(String name) {
            return facts.hasFeature(name)
        }

        @Override
        boolean hasGroup(String group) {
            return facts.hasGroup(group)
        }

        @Override
        ApplicationType getApplicationType() {
            return facts.getApplicationType()
        }

        @Override
        Options getOptions() {
            return facts.getOptions()
        }

        @Override
        Object getParameter(String name) {
            return parameters.get(name)
        }
    }

    @CompileStatic
    private static final class Selected implements Facts {

        private final ApplicationType applicationType
        private final Options options
        private final Collection<Feature> features

        Selected(ApplicationType applicationType, Options options, Collection<Feature> features) {
            this.applicationType = applicationType
            this.options = options
            this.features = features
        }

        @Override
        boolean hasFeature(String name) {
            return features.any { Feature feature -> feature.getName() == name }
        }

        @Override
        boolean hasGroup(String group) {
            return features.any { Feature feature -> feature instanceof OneOfFeature && ((OneOfFeature) feature).getGroup() == group }
        }

        @Override
        ApplicationType getApplicationType() {
            return applicationType
        }

        @Override
        Options getOptions() {
            return options
        }

        @Override
        Object getParameter(String name) {
            return null
        }
    }

    @CompileStatic
    private static final class Processing implements Facts {

        private final FeatureContext featureContext

        Processing(FeatureContext featureContext) {
            this.featureContext = featureContext
        }

        @Override
        boolean hasFeature(String name) {
            return featureContext.isPresent(name)
        }

        @Override
        boolean hasGroup(String group) {
            return featureContext.isGroupPresent(group)
        }

        @Override
        ApplicationType getApplicationType() {
            return featureContext.getApplicationType()
        }

        @Override
        Options getOptions() {
            return featureContext.getOptions()
        }

        @Override
        Object getParameter(String name) {
            return null
        }
    }
}
