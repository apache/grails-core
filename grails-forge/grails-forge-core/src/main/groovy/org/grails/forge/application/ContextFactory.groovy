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
package org.grails.forge.application

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.apache.grails.forge.core.pack.PackFeature
import org.springframework.stereotype.Component
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.DefaultCoordinateResolver
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.feature.validation.ConflictingFeatureValidator
import org.grails.forge.feature.validation.FeatureValidator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.BuildTool
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.Language
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import java.util.function.Supplier

@Component
@CompileStatic
class ContextFactory {

    // the most names a message lists
    private static final int MAX_NAMES = 10

    // see where the defaults are applied
    private static final Comparator<Feature> DEFAULTS_ORDER = { Feature first, Feature second ->
        int byGroup = Boolean.compare(first instanceof OneOfFeature, second instanceof OneOfFeature)
        if (byGroup != 0) {
            return byGroup
        }
        int byRequirements = Boolean.compare(first.getDependentFeatures().isEmpty(), second.getDependentFeatures().isEmpty())
        return byRequirements != 0 ? byRequirements : first.getName() <=> second.getName()
    } as Comparator<Feature>

    private final FeatureValidator featureValidator
    private final DefaultCoordinateResolver coordinateResolver

    /**
     * The repositories a generated build is given in place of those of Grails, in the form of the
     * environment variable {@code GRAILS_REPO_URL}, which it is taken from; {@code null} for those of
     * Grails. They are part of what an application is generated from, and its lock says so.
     *
     * @since 9.0
     */
    String repositoryOverride = System.getenv('GRAILS_REPO_URL')

    ContextFactory(FeatureValidator featureValidator,
                          DefaultCoordinateResolver coordinateResolver) {
        this.featureValidator = featureValidator
        this.coordinateResolver = coordinateResolver
    }

    FeatureContext createFeatureContext(AvailableFeatures availableFeatures,
                                               List<String> selectedFeatures,
                                               ApplicationType applicationType,
                                               Options options,
                                               @Nullable OperatingSystem operatingSystem) {
        return createFeatureContext(availableFeatures, selectedFeatures, applicationType, options, operatingSystem, Collections.<String>emptyList())
    }

    /**
     * Selects the features of an application: the selected ones, then what they require, then the
     * defaults for what nothing has chosen and what those require, until nothing is added.
     *
     * @param excludedFeatures the names of features the application is not to have. A default among them
     * is not applied. A name that is no feature, that cannot be selected by name, that is selected too,
     * that a feature of the application requires, or without which the application would lose a feature
     * it cannot be without, is refused
     * @throws FeatureSelectionException when the features cannot be selected as they were asked for
     * @since 9.0
     */
    FeatureContext createFeatureContext(AvailableFeatures availableFeatures,
                                               List<String> selectedFeatures,
                                               ApplicationType applicationType,
                                               Options options,
                                               @Nullable OperatingSystem operatingSystem,
                                               Collection<String> excludedFeatures) {
        return selecting {
            // by name, so that what is refused first does not depend on the order the names were given in
            Set<String> excluded = new TreeSet<>()
            for (String name : excludedFeatures) {
                excluded.add(String.valueOf(name))
            }
            FeatureContext featureContext = select(availableFeatures, selectedFeatures, applicationType, options, operatingSystem, excluded)
            if (!excluded.isEmpty()) {
                featureContext.setStructure(structure(availableFeatures, selectedFeatures, applicationType, options, operatingSystem))
            }
            return featureContext
        }
    }

    private FeatureContext select(AvailableFeatures availableFeatures,
                                  List<String> selectedFeatures,
                                  ApplicationType applicationType,
                                  Options options,
                                  @Nullable OperatingSystem operatingSystem,
                                  Set<String> excluded) {
        final Set<Feature> features = Collections.newSetFromMap(new IdentityHashMap<>(8))
        List<String> requested = []
        Set<String> unknown = new TreeSet<>()
        for (String name : selectedFeatures) {
            Feature feature = name == null ? null : availableFeatures.findFeature(name).orElse(null)
            if (feature != null) {
                features.add(feature)
                requested << feature.getName()
            } else {
                unknown.add(String.valueOf(name))
            }
        }
        if (!unknown.isEmpty()) {
            throw new FeatureSelectionException(notThere('requested', unknown))
        }

        Map<String, Feature> toExclude = new TreeMap<>()
        for (String name : excluded) {
            Feature feature = availableFeatures.findFeature(name, true).orElse(null)
            if (feature != null) {
                toExclude.put(name, feature)
            } else {
                unknown.add(name)
            }
        }
        if (!unknown.isEmpty()) {
            throw new FeatureSelectionException(notThere('excluded', unknown))
        }
        toExclude.each { String name, Feature feature ->
            if (!feature.isVisible()) {
                throw new FeatureSelectionException("The feature ${name} cannot be excluded: it is not a feature that is selected by name")
            }
            if (features.contains(feature)) {
                throw new FeatureSelectionException("The feature ${name} is both selected and excluded")
            }
        }

        // what the selected features cannot be without comes before the defaults, which are only for
        // what nothing has chosen: a feature that needs one database driver is not given another
        Map<Feature, Feature> requiredBy = new IdentityHashMap<>()
        selectRequired(availableFeatures, features, features, requiredBy, excluded)

        Options newOptions = options
            .withDevelopmentReloading(determineDevelopmentReloading(options.getDevelopmentReloading()))
            .withGormImpl(determineGormImpl(options.getGormImpl()))
            .withServletImpl(determineServletImpl(options.getServletImpl()))

        // A default can depend on another default, such as Mockito on Spock, and can require features.
        // What a default requires is selected as soon as the default is, and the defaults of exclusive
        // groups come last, those without requirements last of all: a default that requires one database
        // driver is there before the group is given another. Every round adds a feature or is the last,
        // so there are no more rounds than features.
        List<DefaultFeature> defaults = availableFeatures.getAllFeatures()
                .filter { it instanceof DefaultFeature }
                .map { (DefaultFeature) it }
                .sorted(DEFAULTS_ORDER)
                .toList()
        boolean added
        do {
            added = false
            for (DefaultFeature feature : defaults) {
                if (!features.contains(feature) && feature.shouldApply(applicationType, newOptions, features)) {
                    if (standsAside(feature, newOptions, features)) {
                        continue
                    }
                    if (excluded.contains(feature.getName())) {
                        if (feature.getName() == newOptions.getGormImpl().getName()) {
                            // the options cannot say that an application has no Grails Data implementation
                            throw new FeatureSelectionException("The feature ${feature.getName()} cannot be excluded: it is the " +
                                    "${OptionName.DATA.label} of the application. Choose another one (--data), or leave the exclusion out")
                        }
                        continue
                    }
                    features.add(feature)
                    selectRequired(availableFeatures, features, [(Feature) feature], requiredBy, excluded)
                    added = true
                }
            }
        } while (added)

        featureValidator.validatePreProcessing(newOptions, applicationType, features)

        FeatureContext featureContext = new FeatureContext(newOptions, applicationType, operatingSystem, features)
        featureContext.setRequestedFeatures(requested)
        featureContext.setApplicableOptions(applicableOptions(availableFeatures, newOptions))
        requiredBy.each { Feature required, Feature by -> featureContext.requiredBy(required.getName(), by.getName()) }
        for (String name : excluded) {
            featureContext.exclude(name)
        }
        return featureContext
    }

    /**
     * The default servlet container and the default reloader stand aside, when their option was not
     * given, for a feature of their group that was selected or is required in their place. An option
     * that was given is a choice and does not stand aside: the two are then refused together.
     *
     * <p>The Grails Data implementation is not treated so. Only the plain default, Hibernate 5, gives
     * way to a selected Hibernate 7, which that feature decides itself. Another value of the option
     * never stands aside, marked as given or not: a caller of the generator that passes one has no
     * other way to say that it chose it, and a persistence layer that is swapped without a word is a
     * worse surprise than a refusal.</p>
     *
     * <p>The defaults that are no choice of an option look for a feature of their group themselves.</p>
     */
    private static boolean standsAside(DefaultFeature feature, Options options, Set<Feature> features) {
        OptionName option = isOf(feature, OptionName.SERVLET) ? OptionName.SERVLET :
                isOf(feature, OptionName.RELOADING) ? OptionName.RELOADING : null
        if (option == null || options.getGiven().contains(option)) {
            return false
        }
        return features.any { Feature other -> isOf(other, option) }
    }

    // the servlet containers and the reloaders are the features of the exclusive group of their option
    private static boolean isOf(Feature feature, OptionName option) {
        return feature instanceof OneOfFeature && ((OneOfFeature) feature).getGroup() == option.getGroup()
    }

    /**
     * The hidden features the application has when nothing is excluded: the parts every application of
     * its kind is built on, which no exclusion may take away. A visible feature can be the only one that
     * brings such a part in, as the JSON views bring the web layer into a REST API.
     *
     * @return their names, or none when the selection cannot be made without the exclusions either, in
     * which case the exclusions are not held to it
     */
    private Set<String> structure(AvailableFeatures availableFeatures,
                                  List<String> selectedFeatures,
                                  ApplicationType applicationType,
                                  Options options,
                                  @Nullable OperatingSystem operatingSystem) {
        try {
            FeatureContext whole = select(availableFeatures, selectedFeatures, applicationType, options, operatingSystem, Collections.<String>emptySet())
            whole.processSelectedFeatures()
            Set<String> hidden = new TreeSet<>()
            for (Feature feature : whole.getFinalFeatures(ConsoleOutput.NOOP)) {
                if (!feature.isVisible()) {
                    hidden.add(feature.getName())
                }
            }
            return hidden
        } catch (IllegalArgumentException ignored) {
            return Collections.<String>emptySet()
        }
    }

    // an option chooses a feature; where the type of application has not the feature its value names, as
    // a plugin has no Tomcat, the option chooses nothing and no feature can disagree with it. An option
    // that asks for none chooses wherever the type of application has a feature of the kind
    private static Set<OptionName> applicableOptions(AvailableFeatures availableFeatures, Options options) {
        Set<OptionName> applicable = EnumSet.noneOf(OptionName)
        if (availableFeatures.findFeature(options.getGormImpl().getName(), true).isPresent()) {
            applicable.add(OptionName.DATA)
        }
        ServletImpl servlet = options.getServletImpl()
        if (servlet == ServletImpl.NONE ? availableFeatures.getAllFeatures().anyMatch { Feature feature -> isOf(feature, OptionName.SERVLET) } :
                availableFeatures.findFeature(servlet.getName(), true).isPresent()) {
            applicable.add(OptionName.SERVLET)
        }
        DevelopmentReloading reloading = options.getDevelopmentReloading()
        if (availableFeatures.getAllFeatures().anyMatch { Feature feature ->
            isOf(feature, OptionName.RELOADING) && (reloading == DevelopmentReloading.NONE || DevelopmentReloading.ofFeature(feature.getName()) == reloading)
        }) {
            applicable.add(OptionName.RELOADING)
        }
        return applicable
    }

    // every refusal of a selection is of one type, whichever part of the selection found it
    private static <T> T selecting(Supplier<T> selection) {
        try {
            return selection.get()
        } catch (FeatureSelectionException e) {
            throw e
        } catch (IllegalArgumentException e) {
            throw new FeatureSelectionException(e.getMessage(), e)
        }
    }

    private static String notThere(String kind, Set<String> names) {
        List<String> shown = names.take(MAX_NAMES).collect { String name -> FeatureSelectionException.shown(name) }
        if (names.size() == 1) {
            return "The ${kind} feature does not exist: ${shown[0]}"
        }
        return "The ${kind} features do not exist: ${shown.join(', ')}" + (names.size() > MAX_NAMES ? " and ${names.size() - MAX_NAMES} more" : '')
    }

    /**
     * Adds what the given features require, and what that requires, to the features. A feature is looked
     * at once, so features that require each other, directly or through others, are selected together.
     */
    private static void selectRequired(AvailableFeatures availableFeatures,
                                       Set<Feature> features,
                                       Collection<Feature> from,
                                       Map<Feature, Feature> requiredBy,
                                       Set<String> excluded) {
        // by name: the features come from a set that has no order of its own
        Deque<Feature> pending = new ArrayDeque<>(from.toSorted(Comparator.comparing(Feature::getName)))
        while (!pending.isEmpty()) {
            Feature feature = pending.pop()
            for (String name : feature.getDependentFeatures()) {
                Feature required = availableFeatures.findFeature(name, true).orElseThrow {
                    new FeatureSelectionException(PackFeature.requiredNotThere(feature.getName(), name) + through(feature, requiredBy))
                }
                if (excluded.contains(required.getName())) {
                    throw new FeatureSelectionException("The feature ${name} cannot be excluded: the feature ${feature.getName()} " +
                            "requires it${through(feature, requiredBy)}")
                }
                if (features.add(required)) {
                    requiredBy.put(required, feature)
                    pending.push(required)
                }
            }
        }
    }

    // how a feature nobody selected came to be required, from the feature that was selected or is a default
    private static String through(Feature feature, Map<Feature, Feature> requiredBy) {
        if (!requiredBy.containsKey(feature)) {
            return ''
        }
        List<String> names = []
        for (Feature step = feature; step != null; step = requiredBy.get(step)) {
            names.add(0, step.getName())
        }
        return " (${FeatureSelectionException.chain(names)})"
    }

    /**
     * @throws FeatureSelectionException when the features cannot be had together, when an exclusion
     * would take away a feature the application cannot be without, or when the application would not
     * have what an option that was given asks for ({@link Options#getGiven()})
     */
    GeneratorContext createGeneratorContext(Project project,
                                                   FeatureContext featureContext,
                                                   ConsoleOutput consoleOutput) {
        Set<Feature> featureList = selecting {
            featureContext.processSelectedFeatures()

            Set<Feature> features = featureContext.getFinalFeatures(consoleOutput)

            checkStructure(featureContext, features)
            checkReliedOn(featureContext, features)
            String conflict = ConflictingFeatureValidator.conflict(features) { String name -> featureContext.requirementsTo(name) }
            if (conflict != null) {
                throw new FeatureSelectionException(conflict)
            }
            featureValidator.validatePostProcessing(featureContext.getOptions(), featureContext.getApplicationType(), features)
            checkGivenOptions(featureContext, features)
            return features
        }

        GeneratorContext generatorContext = new GeneratorContext(project, featureContext.getApplicationType(), featureContext.getOptions(), featureContext.getOperatingSystem(), featureList, coordinateResolver,
                featureContext.getExcludedFeatures(), repositoryOverride)
        generatorContext.setRequestedFeatures(featureContext.getRequestedFeatures())
        return generatorContext
    }

    // an exclusion takes a default out, and with it what only that default brought in, but never a
    // hidden feature: those are what an application is built on, not what it can choose
    private static void checkStructure(FeatureContext featureContext, Set<Feature> features) {
        Set<String> names = features*.getName() as Set<String>
        List<String> lost = featureContext.getStructure().findAll { String name -> !names.contains(name) }.toList()
        if (!lost.isEmpty()) {
            List<String> excluded = featureContext.getExcludedFeatures()
            throw new FeatureSelectionException((excluded.size() == 1 ? "The feature ${excluded[0]} cannot be excluded" :
                    "The features ${excluded.take(MAX_NAMES).join(', ')}${excluded.size() > MAX_NAMES ? ' and others' : ''} cannot be excluded together") +
                    ": the application would be without the feature ${lost[0]}, which it has to have")
        }
    }

    /**
     * An option that was given is a choice like a selected feature: when the features that were selected
     * or excluded leave the application without what the option asks for, or with what it asks not to
     * have, the two do not agree, and one of them would be dropped without a word.
     */
    private static void checkGivenOptions(FeatureContext featureContext, Set<Feature> features) {
        Options options = featureContext.getOptions()
        Set<OptionName> applicable = featureContext.getApplicableOptions()
        Set<OptionName> given = options.getGiven().findAll { OptionName option -> applicable.contains(option) } as Set<OptionName>
        Set<String> names = features*.getName() as Set<String>
        if (given.contains(OptionName.DATA) && !names.contains(options.getGormImpl().getName())) {
            throw disagreement(OptionName.DATA, options.getGormImpl().name().toLowerCase(Locale.ENGLISH), "the feature ${options.getGormImpl().getName()}", null)
        }
        if (given.contains(OptionName.SERVLET)) {
            ServletImpl servlet = options.getServletImpl()
            List<String> servlets = features.findAll { Feature feature -> isOf(feature, OptionName.SERVLET) }*.getName()
            if (servlet == ServletImpl.NONE && !servlets.isEmpty()) {
                throw disagreement(OptionName.SERVLET, 'none', null, "the feature ${servlets[0]}${requiredThrough(featureContext, servlets[0])}")
            }
            if (servlet != ServletImpl.NONE && !names.contains(servlet.getName())) {
                throw disagreement(OptionName.SERVLET, servlet.name().toLowerCase(Locale.ENGLISH), "the feature ${servlet.getName()}", null)
            }
        }
        if (given.contains(OptionName.RELOADING)) {
            DevelopmentReloading reloading = options.getDevelopmentReloading()
            List<Feature> present = features.findAll { Feature feature -> isOf(feature, OptionName.RELOADING) }.toList()
            if (reloading == DevelopmentReloading.NONE && !present.isEmpty()) {
                throw disagreement(OptionName.RELOADING, reloading.getName(), null, "the feature ${present[0].getName()}${requiredThrough(featureContext, present[0].getName())}")
            }
            if (reloading != DevelopmentReloading.NONE && !present.any { Feature feature -> DevelopmentReloading.ofFeature(feature.getName()) == reloading }) {
                throw disagreement(OptionName.RELOADING, reloading.getName(), reloading.getLabel(), null)
            }
        }
    }

    // which feature brought one in that nobody selected by name
    private static String requiredThrough(FeatureContext featureContext, String name) {
        List<String> way = featureContext.requirementsTo(name)
        return way.size() < 2 ? '' : " (${FeatureSelectionException.chain(way)})"
    }

    /**
     * An exclusion leaves out a default and what only that default brought in, but not what a feature
     * that remains makes use of in what it generates: the views of a web application use the asset
     * tags, and its mappings are to those views.
     */
    private static void checkReliedOn(FeatureContext featureContext, Set<Feature> features) {
        List<String> excluded = featureContext.getExcludedFeatures()
        if (excluded.isEmpty()) {
            return
        }
        Set<String> names = features*.getName() as Set<String>
        for (Feature feature : features.toSorted(Comparator.comparing(Feature::getName))) {
            for (String reliedOn : feature.getFeaturesReliedOn(featureContext.getApplicationType())) {
                if (excluded.contains(reliedOn) && !names.contains(reliedOn)) {
                    throw new FeatureSelectionException("The feature ${reliedOn} cannot be excluded: what the feature ${feature.getName()} " +
                            "generates relies on it${requiredThrough(featureContext, feature.getName())}")
                }
            }
        }
    }

    private static FeatureSelectionException disagreement(OptionName option, String value, String without, String with) {
        return new FeatureSelectionException("The ${option.label} ${value} that was asked for does not agree with the features that are " +
                "selected or excluded: the application would be made ${without != null ? "without ${without}" : "with ${with}"}",
                null, option, without, with)
    }

    DevelopmentReloading determineDevelopmentReloading(DevelopmentReloading reloading) {
        if (reloading == null) {
            reloading = DevelopmentReloading.DEFAULT_OPTION
        }
        return reloading
    }

    Language determineLanguage(Language language, Set<Feature> features) {
        if (language == null) {
            language = Language.infer(features)
        }
        if (language == null) {
            language = Language.DEFAULT_OPTION
        }
        return language
    }

    BuildTool determineBuildTool(BuildTool buildTool) {
        if (buildTool == null) {
            buildTool = BuildTool.GRADLE
        }
        return buildTool
    }

    GormImpl determineGormImpl(GormImpl gormImpl) {
        if (gormImpl == null) {
            gormImpl = GormImpl.DEFAULT_OPTION
        }
        return gormImpl
    }

    ServletImpl determineServletImpl(ServletImpl servletImpl) {
        if (servletImpl == null) {
            servletImpl = ServletImpl.DEFAULT_OPTION
        }
        return servletImpl
    }
}
