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

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.validation.OneOfFeatureValidator
import org.grails.forge.fixture.ContextFixture
import org.grails.forge.fixture.ProjectFixture
import org.grails.forge.options.Options
import spock.lang.Unroll

class ExclusiveGroupSpec extends ApplicationContextSpec implements ContextFixture, ProjectFixture {

    void "the exclusive groups of the framework keep their identifiers"() {
        when:
        Map<String, String> familyByGroup = beanContext.getBean(FeatureRegistry).all()
                .findAll { it instanceof OneOfFeature }
                .collectEntries { [((OneOfFeature) it).group, ((OneOfFeature) it).featureClass?.simpleName ?: 'a pack of Forge'] }

        then:
        familyByGroup == [
                'api-views'           : 'a pack of Forge',
                'cache-provider'      : 'a pack of Forge',
                'configuration'       : 'ConfigurationFeature',
                'data-nosql'          : 'a pack of Forge',
                'database-driver'     : 'a pack of Forge',
                'embedded-servlet'    : 'a pack of Forge',
                'geb'                 : 'a pack of Forge',
                'gradle-build-src'    : 'GradleBuildSrcFeature',
                'gradle-settings-file': 'GradleSettingsFileFeature',
                'gsp-layout'          : 'a pack of Forge',
                'logging'             : 'a pack of Forge',
                'migration'           : 'a pack of Forge',
                'reloading'           : 'a pack of Forge',
                'spring-ai-provider'  : 'a pack of Forge',
                'spring-security'     : 'a pack of Forge',
                'threading'           : 'a pack of Forge',
        ]
    }

    void "two features of any group of the framework cannot be selected together"() {
        given:
        OneOfFeatureValidator validator = beanContext.getBean(OneOfFeatureValidator)
        Map<String, List<Feature>> featuresByGroup = beanContext.getBean(FeatureRegistry).all()
                .findAll { it instanceof OneOfFeature }
                .groupBy { ((OneOfFeature) it).group }
                .findAll { String group, List<Feature> features -> features.size() > 1 }

        expect: 'groups with a choice to make, the gorm and database ones among them'
        featuresByGroup.keySet().containsAll(['database-driver', 'data-nosql', 'gsp-layout', 'reloading'])

        and:
        featuresByGroup.every { String group, List<Feature> features ->
            try {
                validator.validatePreProcessing(new Options(), ApplicationType.WEB, features.take(2).toSet())
                return false
            } catch (IllegalArgumentException e) {
                return e.message == "There can only be one of the following features selected: ${features.take(2)*.name.toSorted()}"
            }
        }
    }

    void "features of unrelated types that name the same group cannot be selected together"() {
        when:
        beanContext.getBean(OneOfFeatureValidator).validatePreProcessing(new Options(), ApplicationType.WEB,
                [new GroupedFeature('zeta', 'com.acme:audit', String), new GroupedFeature('alpha', 'com.acme:audit', Integer)] as Set<Feature>)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'There can only be one of the following features selected: [alpha, zeta]'
    }

    void "features of one type that name different groups can be selected together"() {
        when:
        beanContext.getBean(OneOfFeatureValidator).validatePreProcessing(new Options(), ApplicationType.WEB,
                [new GroupedFeature('first', 'one', String), new GroupedFeature('second', 'two', String)] as Set<Feature>)

        then:
        noExceptionThrown()
    }

    @Unroll
    void "a selected feature that names the group #group is rejected"() {
        when:
        beanContext.getBean(OneOfFeatureValidator).validatePostProcessing(new Options(), ApplicationType.WEB,
                [new GroupedFeature('first', group, String)] as Set<Feature>)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature first names no exclusive group'

        where:
        group << [null, '', ' ']
    }

    void "a selected feature is present by its name and its group"() {
        given:
        FeatureContext featureContext = buildFeatureContext(['postgres'], new Options(), ApplicationType.WEB)
        featureContext.processSelectedFeatures()

        expect:
        featureContext.isPresent('postgres')
        !featureContext.isPresent('mysql')
        featureContext.isGroupPresent('database-driver')
        !featureContext.isGroupPresent('migration')
    }

    void "the name of a feature is not the identifier of a group, and the reverse"() {
        given:
        FeatureContext featureContext = buildFeatureContext(['postgres', 'cache-ehcache'], new Options(), ApplicationType.WEB)
        featureContext.processSelectedFeatures()

        expect:
        featureContext.isPresent('cache-ehcache')
        featureContext.isGroupPresent('cache-provider')
        !featureContext.isGroupPresent('cache-ehcache')
        !featureContext.isGroupPresent('postgres')
        !featureContext.isPresent('cache-provider')
        !featureContext.isPresent('database-driver')
    }

    void "a feature added while the selection is processed is present by its name and its group"() {
        given:
        FeatureContext featureContext = buildFeatureContext([], new Options(), ApplicationType.WEB)
        featureContext.processSelectedFeatures()

        when:
        featureContext.addFeature(new GroupedFeature('acme-audit', 'com.acme:audit', String))

        then:
        featureContext.isPresent('acme-audit')
        featureContext.isGroupPresent('com.acme:audit')
    }

    void "an excluded feature is not present by its name or its group"() {
        given:
        FeatureContext featureContext = buildFeatureContext(['postgres'], new Options(), ApplicationType.WEB)
        featureContext.processSelectedFeatures()

        when:
        featureContext.exclude({ Feature feature -> feature.name == 'postgres' } as FeaturePredicate)

        then:
        !featureContext.isPresent('postgres')
        !featureContext.isGroupPresent('database-driver')
    }

    @Unroll
    void "a registry rejects #problem"() {
        when:
        new FeatureRegistry(features, [])

        then:
        IllegalArgumentException e = thrown()
        e.message == message

        where:
        problem                                     | features                                                                                    || message
        'two families that share a group'           | [new GroupedFeature('first', 'shared', String), new GroupedFeature('second', 'shared', Integer)] || "The feature second puts Integer in the exclusive group 'shared', which does not match the other features of that group"
        'one family whose features name two groups' | [new GroupedFeature('first', 'one', String), new GroupedFeature('second', 'two', String)]      || "The feature second puts String in the exclusive group 'two', which does not match the other features of that group"
        'a feature that names a blank group'        | [new GroupedFeature('first', ' ', String)]                                                    || 'The feature first names no exclusive group'
        'a feature that names no group'             | [new GroupedFeature('first', null, String)]                                                   || 'The feature first names no exclusive group'
        'a pack beside features found by their type' | [new GroupedFeature('first', 'shared', String), new GroupedFeature('second', 'shared', null)]  || "The exclusive group 'shared' has features that are packs and compiled features that are looked for by their type, which a pack has not"
        'the same with the pack first'              | [new GroupedFeature('a', 'shared', null), new GroupedFeature('b', 'shared', String)]           || "The exclusive group 'shared' has features that are packs and compiled features that are looked for by their type, which a pack has not"
    }

    void "a group whose compiled features are found by the group can have packs, and a group can be of packs only"() {
        when:
        new FeatureRegistry([new GroupedFeature('first', 'shared', String, true), new GroupedFeature('second', 'shared', null),
                             new GroupedFeature('third', 'packs', null), new GroupedFeature('fourth', 'packs', null)], [])

        then:
        noExceptionThrown()

        and: 'the layouts are a group of packs only: the default and the one that replaces it'
        beanContext.getBean(FeatureRegistry).all().findAll { it instanceof OneOfFeature && ((OneOfFeature) it).group == 'gsp-layout' }
                .collectEntries { [it.name, ((OneOfFeature) it).featureClass?.simpleName] } == ['grails-layout': null, 'sitemesh3': null]
    }

    static class GroupedFeature implements OneOfFeature {

        final String name
        final String group
        final Class<?> featureClass

        final boolean foundByGroup

        GroupedFeature(String name, String group, Class<?> featureClass, boolean foundByGroup = false) {
            this.name = name
            this.group = group
            this.featureClass = featureClass
            this.foundByGroup = foundByGroup
        }

        @Override
        boolean supports(ApplicationType applicationType) {
            true
        }
    }
}
