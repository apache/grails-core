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
import jakarta.annotation.Nonnull
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.AvailableFeatures
import org.grails.forge.feature.Feature
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.util.VersionInfo

/**
 * The features an application of one type can have for one generation: those of the framework, and
 * those of the packs that generation was given. It is made for a generation and is not shared, so the
 * packs of one generation are never the features of another.
 *
 * @since 9.0
 */
@CompileStatic
final class PackFeatures implements AvailableFeatures {

    private final AvailableFeatures framework
    private final Map<String, Feature> packs = new LinkedHashMap<>()

    /**
     * @param framework the features of the framework for the type of application
     * @param everyFrameworkFeature the features of the framework for every type of application
     * @param packs the packs to add; one that does not support the type of application is left out
     * @param applicationType the type of application
     * @throws PackRefusedException when a pack has the name of a feature of the framework or of
     * another pack, names an exclusive group of the framework, or is for another Grails or Forge; and
     * when more than one preset is given, or a preset of another type of application
     */
    PackFeatures(AvailableFeatures framework, Collection<Feature> everyFrameworkFeature, Collection<Pack> packs, ApplicationType applicationType) {
        this.framework = framework
        // of every type of application: a name or a group means the same whatever is being generated
        Set<String> frameworkNames = everyFrameworkFeature*.getName() as Set<String>
        Set<String> frameworkGroups = everyFrameworkFeature
                .findAll { Feature feature -> feature instanceof OneOfFeature }
                .collect { Feature feature -> ((OneOfFeature) feature).getGroup() } as Set<String>
        Function<String, Optional<Feature>> lookup = { String required -> findFeature(required, true) } as Function<String, Optional<Feature>>
        Map<String, Pack> named = new HashMap<>()
        Pack preset = null
        // one for this generation, and only when a pack has a template
        PackPages[] made = new PackPages[1]
        Supplier<PackPages> pages = { ->
            if (made[0] == null) {
                made[0] = new PackPages(PackPages.classLoader)
            }
            return made[0]
        } as Supplier<PackPages>
        for (Pack pack : packs) {
            checkCompatibility(pack, 'Grails', pack.descriptor.grailsCompatibility, VersionInfo.getGrailsVersion())
            checkCompatibility(pack, 'Forge', pack.descriptor.forgeCompatibility, VersionInfo.getVersion())
            PackFeature feature = PackFeature.of(pack, lookup, pages)
            String name = feature.getName()
            if (frameworkNames.contains(name)) {
                throw new PackRefusedException("The feature pack ${pack.origin} has the name of a feature of the framework, ${name}", [pack], true)
            }
            if (feature instanceof OneOfFeature && frameworkGroups.contains(((OneOfFeature) feature).getGroup())) {
                throw new PackRefusedException("The feature pack ${pack.origin} names the exclusive group " +
                        "${((OneOfFeature) feature).getGroup()} of the framework, which a pack cannot join yet", [pack], true)
            }
            Pack other = named.put(name, pack)
            if (other != null) {
                throw new PackRefusedException("The feature packs ${other.origin} and ${pack.origin} both have the name ${name}", [other, pack], false)
            }
            if (pack.descriptor.preset) {
                // one preset for an application, of its type: one that is not for it is not left out as a feature pack is, it was asked for
                if (preset != null) {
                    throw new PackRefusedException("The presets ${preset.descriptor.name} from ${preset.origin} and ${name} from ${pack.origin} were both given, " +
                            'and an application is made from one preset', [preset, pack], false)
                }
                preset = pack
                if (!feature.supports(applicationType)) {
                    throw new PackRefusedException("The preset ${name} from ${pack.origin} is for an application of the type " +
                            "${PackPreset.nameOf(pack.descriptor.preset.applicationType)}, and this is one of the type ${PackPreset.nameOf(applicationType)}",
                            [pack], false)
                }
            }
            if (feature.supports(applicationType)) {
                this.packs.put(name, feature)
            }
        }
    }

    @Override
    Iterator<String> iterator() {
        return getFeatures().map(Feature::getName).sorted().iterator()
    }

    @Override
    Optional<Feature> findFeature(@Nonnull String name) {
        return findFeature(name, false)
    }

    /**
     * @param name the name of a feature, or the full name of the feature of the pack of a plugin,
     * {@code group:artifact#name}, which is that feature only when the pack was taken from the jar of
     * that plugin, in whichever version: see {@link PackFeature#isNamed}
     */
    @Override
    Optional<Feature> findFeature(@Nonnull String name, boolean ignoreVisibility) {
        if (PackFeature.pluginOf(name) != null) {
            Feature named = packs.get(PackFeature.nameOf(name))
            return named != null && PackFeature.isNamed(name, named) ? Optional.of(named) : Optional.<Feature>empty()
        }
        Feature pack = packs.get(name)
        return pack != null ? Optional.of(pack) : framework.findFeature(name, ignoreVisibility)
    }

    @Override
    Stream<Feature> getFeatures() {
        return getAllFeatures().filter(Feature::isVisible)
    }

    @Override
    Stream<Feature> getAllFeatures() {
        return Stream.concat(framework.getAllFeatures(), packs.values().stream())
    }

    // a pack that was given is refused when it is not for this Grails, selected or not: it was asked for
    static void checkCompatibility(Pack pack, String of, String range, String version) {
        PackVersionRange versions = PackVersionRange.parse(range)
        if (!versions.contains(version)) {
            throw new PackRefusedException("The feature pack ${pack.descriptor.name} from ${pack.origin} is for ${of} ${versions}, " +
                    "and this is ${of} ${version}", [pack], true)
        }
    }
}
