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
package org.grails.forge.cli.command

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.apache.grails.forge.core.pack.PackDescriptor
import org.apache.grails.forge.core.state.GenerationOptions
import org.apache.grails.forge.core.state.ProjectLock
import org.apache.grails.forge.core.state.ProjectLock.LockedFeature
import org.apache.grails.forge.core.state.ProjectManifest
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.grails.forge.util.VersionInfo

/**
 * Which features of Grails can be added to an application by {@code feature add}, as far as its
 * record tells: the features are selected as adding would select them, with what was asked for when
 * the application was created and with its options, which adding never changes, and nothing is
 * rendered. What adding finds in the files of the application, a change of yours in the way of a
 * feature, is not looked for: {@code feature add --dry-run} shows that.
 *
 * @since 9.0
 */
@CompileStatic
final class FeatureAvailability {

    /** Why no feature is added to the application by a command, or {@code null} when features can be. */
    final String refusal

    /** The features that were asked for by name, as the record has them. */
    final List<String> askedFor

    /** A verdict for every feature of Grails for the type of the application that it does not have, by name. */
    final List<Verdict> verdicts

    private FeatureAvailability(String refusal, List<String> askedFor, List<Verdict> verdicts) {
        this.refusal = refusal
        this.askedFor = askedFor.asImmutable()
        this.verdicts = verdicts.asImmutable()
    }

    /**
     * @param generator the generator of the applications
     * @param state the record of the application
     * @param operatingSystem the operating system the generator is told of, as when a feature is added
     * @return what can be added to the application
     */
    static FeatureAvailability of(ProjectGenerator generator, ProjectState state, @Nullable OperatingSystem operatingSystem) {
        ProjectManifest manifest = state.manifest
        List<String> asked = manifest.requestedFeatures*.name
        String refusal = refusal(state)
        if (refusal != null) {
            return new FeatureAvailability(refusal, asked, Collections.<Verdict>emptyList())
        }
        AvailableFeatures available = generator.availableFeatures(manifest.applicationType, [])
        List<String> has = state.lock.featureNames
        List<Verdict> verdicts = []
        for (Feature feature : available.getFeatures().sorted(Comparator.comparing(Feature::getName)).toList()) {
            if (!has.contains(feature.getName())) {
                verdicts.add(verdict(generator, available, state, feature, operatingSystem))
            }
        }
        return new FeatureAvailability(null, asked, verdicts)
    }

    /**
     * @param state the record of an application
     * @return why the record does not let {@code feature add} add anything, or {@code null}: what adding
     * refuses before it looks at a feature
     */
    static String refusal(ProjectState state) {
        ProjectLock lock = state.lock
        if (!state.manifest.isTracked()) {
            return "the record was written by project migrate for an application that was there, and not by the generator (its generationState is ${ProjectManifest.UNTRACKED}). " +
                    'A feature is added to such an application by hand'
        }
        if (lock.generatorName != ProjectLock.GENERATOR || lock.generatorVersion != VersionInfo.getVersion() || lock.grailsVersion != VersionInfo.getGrailsVersion() ||
                lock.templateApi != PackDescriptor.TEMPLATE_API) {
            return "it was generated by ${lock.generatorName} ${lock.generatorVersion} for Grails ${lock.grailsVersion}, and a feature is added by the version " +
                    "it was generated with: the command line of Grails ${lock.generatorVersion}"
        }
        List<String> ofPacks = lock.features.findAll { LockedFeature feature -> feature.source == LockedFeature.PACK }*.name
        if (!ofPacks.isEmpty()) {
            return "it has the ${ofPacks.size() == 1 ? 'feature' : 'features'} ${ofPacks.join(', ')} of a feature pack that is not part of Grails, " +
                    'and a feature cannot yet be added to such an application'
        }
        return null
    }

    // the feature selected as feature add selects it, and what that comes to beside the features the application has
    private static Verdict verdict(ProjectGenerator generator, AvailableFeatures available, ProjectState state, Feature feature, @Nullable OperatingSystem operatingSystem) {
        ProjectManifest manifest = state.manifest
        List<String> asked = manifest.requestedFeatures*.name
        String name = feature.getName()
        // a feature that was asked for by name and is of the exclusive group of this one makes way only with --replace
        List<String> making = asked.findAll { String each -> sameGroup(available, each, feature) }
        String requiredBy = requiredByWhatStays(available, asked.findAll { String each -> !making.contains(each) } + [name], making)
        if (requiredBy != null) {
            return new Verdict(feature, false, [requiredBy])
        }
        List<String> selected = asked.findAll { String each -> !making.contains(each) } + [name]
        GeneratorContext context
        try {
            context = generator.createGeneratorContext(manifest.applicationType, NameUtils.parse(manifest.defaultPackage + '.' + manifest.name), options(manifest.options, operatingSystem),
                    operatingSystem, selected, ConsoleOutput.NOOP, [], manifest.excludedFeatures*.name)
        } catch (FeatureSelectionException e) {
            // the message names what was given, and is made one line that is no markup where it is shown
            String said = e.getMessage() ?: e.getClass().getSimpleName()
            return new Verdict(feature, false, [e.getOption() == null ? said :
                    "${said}. Adding a feature does not change the options the application was created with".toString()])
        } catch (RuntimeException e) {
            // a feature that cannot be selected with what the application has, for a reason of its own, as one that was left out
            return new Verdict(feature, false, [e.getMessage() ?: e.getClass().getSimpleName()])
        }
        List<String> would = context.getFeatures().getFeatures()*.getName()
        List<String> has = state.lock.featureNames
        List<String> lost = has.findAll { String each -> !would.contains(each) }
        List<String> lostAsked = lost.findAll { String each -> asked.contains(each) && !making.contains(each) }
        if (!lostAsked.isEmpty()) {
            return new Verdict(feature, false, ["it would take out ${lostAsked.join(', ')}, which ${lostAsked.size() == 1 ? 'was' : 'were'} asked for by name".toString()])
        }
        List<String> notes = []
        for (String each : making) {
            notes.add("with --replace only: it takes the place of ${each}, which was asked for by name (group ${groupOf(available, each)})".toString())
        }
        for (String each : lost.findAll { String other -> !making.contains(other) }) {
            String group = groupOf(available, each)
            if (!group.isEmpty()) {
                notes.add("in the place of ${each}, the default of the group ${group}".toString())
            } else if (!making.isEmpty()) {
                notes.add("it takes out ${each}, which the application does not have with it in the place of ${making.join(', ')}".toString())
            } else {
                notes.add("it takes out ${each}, which the application has without having asked for it and does not have with it".toString())
            }
        }
        List<String> with = would.findAll { String each -> !has.contains(each) && each != name && available.findFeature(each).isPresent() }.sort()
        if (!with.isEmpty()) {
            notes.add("and with it ${with.join(', ')}".toString())
        }
        return new Verdict(feature, true, notes)
    }

    // The options the application was created with are what it has, so each is a choice that a feature
    // cannot change, as when a feature is added
    private static Options options(GenerationOptions recorded, @Nullable OperatingSystem operatingSystem) {
        return new Options(recorded.reloading, recorded.data, recorded.servlet, recorded.jdk, operatingSystem)
                .withGiven(EnumSet.allOf(OptionName))
                .withSubproject(recorded.subproject)
    }

    private static boolean sameGroup(AvailableFeatures available, String asked, Feature feature) {
        Feature other = available.findFeature(asked, true).orElse(null)
        return other instanceof OneOfFeature && feature instanceof OneOfFeature && other.getName() != feature.getName() &&
                ((OneOfFeature) other).getGroup() == ((OneOfFeature) feature).getGroup()
    }

    private static String groupOf(AvailableFeatures available, String name) {
        Feature feature = available.findFeature(name, true).orElse(null)
        return feature instanceof OneOfFeature ? ((OneOfFeature) feature).getGroup() : ''
    }

    // a feature that would make way and that a feature the application keeps or gets requires: the selection would bring it back
    private static String requiredByWhatStays(AvailableFeatures available, List<String> selected, List<String> making) {
        if (making.isEmpty()) {
            return null
        }
        for (String from : selected) {
            Deque<String> pending = new ArrayDeque<>([from])
            Set<String> seen = new HashSet<>()
            while (!pending.isEmpty()) {
                Feature feature = available.findFeature(pending.pop(), true).orElse(null)
                for (String required : (feature == null ? Collections.<String>emptyList() : feature.getDependentFeatures())) {
                    if (making.contains(required)) {
                        return "it would take the place of ${required}, which was asked for by name, and ${from} requires ${required}".toString()
                    }
                    if (seen.add(required)) {
                        pending.push(required)
                    }
                }
            }
        }
        return null
    }

    /** What adding one feature comes to. */
    static final class Verdict {

        final String name
        final String description
        final String category
        /** Whether {@code feature add} adds it, by the record. */
        final boolean addable
        /** For a feature that is added, what it takes the place of and brings; for another, why it is not. */
        final List<String> notes

        Verdict(Feature feature, boolean addable, List<String> notes) {
            this.name = feature.getName()
            this.description = feature.getDescription() ?: ''
            this.category = feature.getCategory() ?: ''
            this.addable = addable
            this.notes = notes.asImmutable()
        }
    }
}
