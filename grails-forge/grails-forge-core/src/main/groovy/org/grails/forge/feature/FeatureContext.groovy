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
package org.grails.forge.feature

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import java.util.stream.Stream

import static java.util.stream.Collectors.collectingAndThen
import static java.util.stream.Collectors.toCollection

@CompileStatic
class FeatureContext {

    /**
     * The order features are applied and listed in: by {@link Feature#getOrder()}, then by name, so that
     * features of the same order always come out in the same sequence.
     */
    public static final Comparator<Feature> FEATURE_ORDER = { Feature first, Feature second ->
        int byOrder = Integer.compare(first.getOrder(), second.getOrder())
        byOrder != 0 ? byOrder : first.getName() <=> second.getName()
    } as Comparator<Feature>

    private final ApplicationType applicationType
    private final OperatingSystem operatingSystem
    private final Set<Feature> selectedFeatures
    private final Options options
    private final List<Feature> features = new ArrayList<>()
    private final List<FeaturePredicate> exclusions = new ArrayList<>()
    private List<String> requestedFeatures = Collections.emptyList()
    private final Set<String> excludedNames = new TreeSet<>()
    private final Deque<Feature> processing = new ArrayDeque<>()
    private final Map<String, String> requiredBy = new HashMap<>()
    private Set<String> structure = Collections.emptySet()
    private Set<OptionName> applicableOptions = Collections.emptySet()
    private ListIterator<Feature> iterator

    FeatureContext(Options options,
                          ApplicationType applicationType,
                          @Nullable OperatingSystem operatingSystem,
                          Set<Feature> selectedFeatures) {
        this.applicationType = applicationType
        this.operatingSystem = operatingSystem
        this.selectedFeatures = selectedFeatures
        if (options.getDevelopmentReloading() == null) {
            DevelopmentReloading reloading = selectedFeatures.stream()
                .map { Feature feature -> DevelopmentReloading.ofFeature(feature.getName()) }
                .filter { it != null }
                .findFirst()
                .orElse(DevelopmentReloading.DEFAULT_OPTION)
            options = options.withDevelopmentReloading(reloading)
        }
        this.options = options
    }

    /**
     * @return the names of the features that were asked for by name, without what they require and
     * without the defaults
     * @since 9.0
     */
    List<String> getRequestedFeatures() {
        return requestedFeatures
    }

    /**
     * @param requestedFeatures the names of the features that were asked for by name
     * @since 9.0
     */
    void setRequestedFeatures(List<String> requestedFeatures) {
        this.requestedFeatures = Collections.unmodifiableList(new ArrayList<String>(requestedFeatures))
    }

    /**
     * @return the names of the features the application cannot be without, whatever is excluded
     * @since 9.0
     */
    Set<String> getStructure() {
        return structure
    }

    /**
     * @param structure the names of the features the application cannot be without
     * @since 9.0
     */
    void setStructure(Set<String> structure) {
        this.structure = Collections.unmodifiableSet(new TreeSet<String>(structure))
    }

    /**
     * @return the options whose value names a feature this type of application can have. The servlet
     * container Tomcat is none for a plugin, which has no feature for it.
     * @since 9.0
     */
    Set<OptionName> getApplicableOptions() {
        return applicableOptions
    }

    /**
     * @param applicableOptions the options that choose a feature this type of application can have
     * @since 9.0
     */
    void setApplicableOptions(Set<OptionName> applicableOptions) {
        this.applicableOptions = Collections.unmodifiableSet(new HashSet<OptionName>(applicableOptions))
    }

    /**
     * Notes that a feature is there because another one requires it.
     *
     * @param required the name of the feature that is required
     * @param by the name of the feature that requires it
     * @since 9.0
     */
    void requiredBy(String required, String by) {
        requiredBy.putIfAbsent(required, by)
    }

    /**
     * @param name the name of a feature
     * @return the names of the features that led to it through what each requires, from the one that was
     * selected or is a default to the feature itself; only the feature when nothing is known to require it
     * @since 9.0
     */
    List<String> requirementsTo(String name) {
        List<String> names = []
        Set<String> seen = new HashSet<>()
        for (String step = name; step != null && seen.add(step); step = requiredBy.get(step)) {
            names.add(0, step)
        }
        return names
    }

    void processSelectedFeatures() {
        this.features.addAll(0, selectedFeatures)
        this.features.sort(FEATURE_ORDER)
        this.iterator = this.features.listIterator()
        while (iterator.hasNext()) {
            process(iterator.next())
        }
        this.iterator = null
    }

    void exclude(FeaturePredicate exclusion) {
        exclusions.add(exclusion)
    }

    /**
     * Excludes the feature of a name because the application is not to have it. Unlike another
     * exclusion this one is not silent: a feature that adds the excluded one, because it cannot be
     * without it, is refused.
     *
     * @param name the name of a feature
     * @since 9.0
     */
    void exclude(String name) {
        if (excludedNames.add(name)) {
            exclusions.add(new NamedExclusion(name))
        }
    }

    /**
     * @return the names given to {@link #exclude(String)}, sorted
     * @since 9.0
     */
    List<String> getExcludedFeatures() {
        return Collections.unmodifiableList(new ArrayList<String>(excludedNames))
    }

    /**
     * @return the selected features without the excluded ones, in {@link #FEATURE_ORDER}
     */
    Set<Feature> getFinalFeatures(ConsoleOutput consoleOutput) {
        return features.stream().filter(feature -> {
            for (FeaturePredicate predicate : exclusions) {
                if (predicate.test(feature)) {
                    predicate.getWarning().ifPresent(message -> {
                        throw new IllegalArgumentException(message)
                    })
                    return false
                }
            }
            return true
        }).sorted(FEATURE_ORDER)
                .collect(collectingAndThen(toCollection(LinkedHashSet::new), Collections::unmodifiableSet))
    }

    DevelopmentReloading getDevelopmentReloading() {
        return options.getDevelopmentReloading()
    }

    JdkVersion getJavaVersion() {
        return options.getJavaVersion()
    }

    Options getOptions() {
        return options
    }

    /**
     * Adds a feature to be applied. The added feature is processed immediately.
     *
     * @param feature The feature to add
     */
    void addFeature(Feature feature) {
        if (excludedNames.contains(feature.getName())) {
            Feature requiring = processing.peek()
            throw new IllegalArgumentException("The feature ${feature.getName()} cannot be excluded: " +
                    (requiring != null ? "the feature ${requiring.getName()} requires it" : 'a feature of the application requires it'))
        }
        if (!processing.isEmpty()) {
            requiredBy(feature.getName(), processing.peek().getName())
        }
        if (iterator != null) {
            iterator.add(feature)
        } else {
            features.add(feature)
        }
        process(feature)
    }

    // the feature that is being processed is the one that requires what is added meanwhile
    private void process(Feature feature) {
        processing.push(feature)
        try {
            feature.processSelectedFeatures(this)
        } finally {
            processing.pop()
        }
    }

    private static final class NamedExclusion implements FeaturePredicate {

        private final String name

        NamedExclusion(String name) {
            this.name = name
        }

        @Override
        boolean test(Feature feature) {
            return feature.getName() == name
        }

        // a feature that is among the selected ones although it is excluded was brought in by another
        @Override
        Optional<String> getWarning() {
            return Optional.of("The feature ${name} cannot be excluded: a feature of the application requires it".toString())
        }
    }

    ApplicationType getApplicationType() {
        return applicationType
    }

    boolean isPresent(Class<? extends Feature> feature) {
        return selected()
            .map(Feature::getClass)
            .anyMatch(feature::isAssignableFrom)
    }

    /**
     * @param name the name of a feature
     * @return whether a feature of that name is selected and not excluded
     * @since 9.0
     */
    boolean isPresent(String name) {
        return selected().anyMatch(f -> f.getName() == name)
    }

    /**
     * @param group the identifier of an exclusive group, see {@link OneOfFeature#getGroup()}
     * @return whether a feature of that group is selected and not excluded
     * @since 9.0
     */
    boolean isGroupPresent(String group) {
        return selected().anyMatch(f -> f instanceof OneOfFeature && ((OneOfFeature) f).getGroup() == group)
    }

    private Stream<Feature> selected() {
        return features.stream().filter(f -> exclusions.stream().noneMatch(e -> e.test(f)))
    }

    OperatingSystem getOperatingSystem() {
        return operatingSystem
    }
}
