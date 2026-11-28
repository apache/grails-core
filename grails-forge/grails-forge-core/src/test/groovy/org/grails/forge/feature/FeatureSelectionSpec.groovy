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

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackLoader
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.ContextFactory
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.OptionName
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import spock.lang.TempDir
import spock.lang.Unroll

class FeatureSelectionSpec extends ApplicationContextSpec {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    @TempDir
    Path directory

    @Unroll
    void "packs that require each other in a circle are selected together, whichever is named: #selected"() {
        given:
        List<Pack> packs = [
                pack('acme-a', 'requires: [acme-b]\n'),
                pack('acme-b', 'requires: [acme-c]\n'),
                pack('acme-c', 'requires: [acme-a, postgres]\n'),
        ]

        when:
        GeneratorContext context = context(selected, packs)

        then:
        context.features.containsAll(['acme-a', 'acme-b', 'acme-c', 'postgres'])
        !context.features.contains('h2')

        and: 'and the application is the same; its state says which of them was named'
        application(generate(selected, packs)) == application(generate(['acme-a'], packs))

        where:
        selected << [['acme-a'], ['acme-b'], ['acme-c'], ['acme-c', 'acme-a', 'acme-b']]
    }

    void "a feature two others require is selected and applied once"() {
        given:
        List<Pack> packs = [
                pack('acme-top', 'requires: [acme-left, acme-right]\n'),
                pack('acme-left', 'requires: [acme-base]\n'),
                pack('acme-right', 'requires: [acme-base]\n'),
                pack('acme-base', "dependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-base\n"),
        ]

        when:
        Map<String, String> output = generate(['acme-top'], packs)

        then:
        output['build.gradle'].count('implementation "com.acme:acme-base"') == 1
        output['.grails/forge.lock.yml'].count('- name: acme-base\n') == 1

        and: 'however the four are named'
        application(output) == application(generate(['acme-right', 'acme-base', 'acme-top', 'acme-left'], packs))
    }

    void "a requirement there is not is refused with the way that led to it"() {
        given:
        List<Pack> packs = [
                pack('acme-a', 'requires: [acme-b]\n'),
                pack('acme-b', 'requires: [acme-c]\n'),
                pack('acme-c', 'requires: [acme-missing]\n'),
        ]

        when:
        context(['acme-a'], packs)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature acme-c requires the feature acme-missing, which there is not for this type of application ' +
                '(acme-a requires acme-b requires acme-c)'
    }

    void "what a default requires is selected with it, and what that requires"() {
        given: 'a default that requires a feature nothing else selects'
        AvailableFeatures framework = beanContext.getBean(FeatureRegistry).availableFeatures(ApplicationType.WEB)
        Feature needsMigration = new RequiringDefault('acme-default', ['acme-needed'])
        Feature needed = new RequiringDefault('acme-needed', ['database-migration']) {

            @Override
            boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
                false
            }
        }
        AvailableFeatures available = new WithMore(framework, [needsMigration, needed])

        when:
        FeatureContext featureContext = beanContext.getBean(ContextFactory).createFeatureContext(available, [], ApplicationType.WEB,
                new Options(), OperatingSystem.LINUX)
        featureContext.processSelectedFeatures()
        List<String> names = featureContext.getFinalFeatures(ConsoleOutput.NOOP)*.name

        then:
        names.containsAll(['acme-default', 'acme-needed', 'database-migration'])

        when: 'and cannot be excluded'
        beanContext.getBean(ContextFactory).createFeatureContext(available, [], ApplicationType.WEB,
                new Options(), OperatingSystem.LINUX, ['database-migration'])

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The feature database-migration cannot be excluded: the feature acme-needed requires it (acme-default requires acme-needed)'
    }

    @Unroll
    void "what a default requires of an exclusive group is there before the group is given its default: #required"() {
        given: 'a default that is last by name and requires one of a group, or what a default gives way to'
        AvailableFeatures framework = beanContext.getBean(FeatureRegistry).availableFeatures(ApplicationType.WEB)
        AvailableFeatures available = new WithMore(framework, [new RequiringDefault('zzz-default', [required])])

        when:
        FeatureContext featureContext = beanContext.getBean(ContextFactory).createFeatureContext(available, [], ApplicationType.WEB,
                new Options(), OperatingSystem.LINUX)
        GeneratorContext context = beanContext.getBean(ContextFactory).createGeneratorContext(NameUtils.parse('example.grails.foo'), featureContext, ConsoleOutput.NOOP)

        then:
        context.features.containsAll(['zzz-default', required])
        !context.features.contains(notThere)

        where:
        required                  || notThere
        'postgres'                || 'h2'
        'gorm-hibernate7'         || 'gorm-hibernate5'
        'properties'              || 'yaml'
        'grails-layout'           || 'sitemesh3'
        'geb-with-local-browsers' || 'geb-with-testcontainers'
    }

    @Unroll
    void "a pack is refused with a feature it conflicts with that #how, when the features are chosen"() {
        given:
        List<Pack> packs = descriptors.collect { String name, String body -> pack(name, body) }

        when: 'nothing is applied and no template runs: only the context is made'
        context(selected, packs)

        then:
        FeatureSelectionException e = thrown()
        e.message == message

        where:
        how                                 | selected                 | descriptors                                                                         || message
        'is selected'                       | ['acme-audit', 'postgres'] | ['acme-audit': 'conflicts: [postgres]\n']                                         || 'The feature acme-audit cannot be used with the feature postgres'
        'is a default'                      | ['acme-audit']           | ['acme-audit': 'conflicts: [h2]\n']                                                 || 'The feature acme-audit cannot be used with the feature h2'
        'is a default nobody can select'    | ['acme-audit']           | ['acme-audit': 'conflicts: [logback]\n']                                            || 'The feature acme-audit cannot be used with the feature logback'
        'another feature brings in'         | ['acme-audit', 'postgres'] | ['acme-audit': 'conflicts: [testcontainers]\n']                                   || 'The feature acme-audit cannot be used with the feature testcontainers (postgres requires testcontainers)'
        'another pack requires'             | ['acme-audit', 'acme-b'] | ['acme-audit': 'conflicts: [database-migration]\n', 'acme-b': 'requires: [database-migration]\n'] || 'The feature acme-audit cannot be used with the feature database-migration (acme-b requires database-migration)'
        'is a pack that requires this one'  | ['acme-b']               | ['acme-audit': 'conflicts: [acme-b]\n', 'acme-b': 'requires: [acme-audit]\n']       || 'The feature acme-audit cannot be used with the feature acme-b (acme-b requires acme-audit)'
        'it requires through another pack' | ['acme-audit']           | ['acme-audit': 'requires: [acme-b]\nconflicts: [database-migration]\n', 'acme-b': 'requires: [database-migration]\n'] || 'The feature acme-audit cannot be used with the feature database-migration (acme-audit requires acme-b requires database-migration)'
    }

    void "a pack that conflicts with a feature the application has not, or that there is not, is used"() {
        given:
        Pack pack = pack('acme-audit', 'conflicts: [postgres, gorm-mongodb, acme-no-such-feature]\n')

        expect:
        context(['acme-audit'], [pack]).features.contains('acme-audit')
    }

    void "a template of a pack does not run when another pack is refused for a conflict"() {
        given: 'a pack that is applied first and whose template fails when it runs'
        Pack first = pack('acme-a', "files:\n  - from: f.txt.gsp\n    to: f.txt\n", ['f.txt.gsp': '<% throw new IllegalStateException("the template ran") %>'])
        Pack second = pack('acme-b', 'conflicts: [acme-a]\n')

        when:
        generate(['acme-a', 'acme-b'], [first, second])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature acme-b cannot be used with the feature acme-a'
    }

    @Unroll
    void "the default #excluded is left out of a #type application when it is excluded"() {
        when:
        GeneratorContext with = context([], [], type)
        GeneratorContext without = context([], [], type, excluded)

        then:
        with.features.containsAll(excluded)
        excluded.every { !without.features.contains(it) }
        without.excludedFeatures == excluded.toSorted()

        and: 'and nothing is there instead: what only the excluded feature brought in is left out with it'
        (with.features - excluded).containsAll(without.features)
        alsoLeftOut == with.features - excluded - without.features

        and: 'the application is generated, and says what was excluded'
        Map<String, String> output = generate([], [], type, excluded)
        output['.grails/project.yml'].contains("excludedFeatures:\n${excluded.toSorted().collect { "- name: ${it}\n" }.join('')}")

        where:
        type                     | excluded                                        || alsoLeftOut
        ApplicationType.WEB      | ['geb-with-testcontainers']                     || []
        ApplicationType.WEB      | ['mockito']                                     || []
        ApplicationType.WEB      | ['scaffolding']                                 || []
        ApplicationType.WEB      | ['scaffolding', 'geb-with-testcontainers']      || []
        ApplicationType.WEB_PLUGIN | ['scaffolding', 'spock', 'mockito']           || []
        ApplicationType.WEB      | ['spock', 'mockito', 'geb-with-testcontainers'] || []
        ApplicationType.REST_API | ['mockito']                                     || []
        ApplicationType.REST_API_PLUGIN | ['spock', 'mockito']                      || []
        ApplicationType.PLUGIN   | ['mockito']                                     || []
    }

    void "an application without excluded features is what it was and says nothing of them"() {
        when:
        Map<String, String> output = generate([], [])

        then:
        output['.grails/project.yml'].contains('excludedFeatures: []\n')
        !output.containsKey('grails-forge-cli.yml')
        context([], []).excludedFeatures == []
    }

    void "excluding a feature the application would not have changes nothing but the record of it"() {
        when:
        Map<String, String> with = generate([], [], ApplicationType.WEB, ['postgres'])
        Map<String, String> without = generate([], [])

        then:
        with['.grails/project.yml'] == without['.grails/project.yml'].replace('excludedFeatures: []\n', 'excludedFeatures:\n- name: postgres\n')
        with['.grails/project.yml'] != without['.grails/project.yml']
        with.findAll { !it.key.startsWith('.grails/') } == without.findAll { !it.key.startsWith('.grails/') }
        with.keySet() == without.keySet()
    }

    @Unroll
    void "the state of a project has what was excluded, sorted, in whatever order it was named: #excluded"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('com.acme.shop')
        Path target = Files.createDirectory(directory.resolve('app'))

        when:
        generator.generate(ApplicationType.WEB, project, new FileSystemOutputHandler(target.toFile(), ConsoleOutput.NOOP),
                generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX, ['postgres'], ConsoleOutput.NOOP, [], excluded))
        ProjectState state = ProjectState.read(target)
        state.verifyBaseline(target)

        then:
        state.manifest.excludedFeatures*.name == ['mockito', 'mysql', 'scaffolding']
        state.manifest.excludedFeatures.every { it.coordinates == null && it.version == null }
        state.manifest.requestedFeatures*.name == ['postgres']
        !state.lock.featureNames.contains('mockito')
        target.resolve('.grails/project.yml').toFile().getText('UTF-8').contains(
                'excludedFeatures:\n- name: mockito\n- name: mysql\n- name: scaffolding\n')

        where:
        excluded << ['mysql', 'mockito', 'scaffolding'].permutations().toList() + [['mockito', 'mysql', 'mockito', 'scaffolding']]
    }

    void "the state of a project is the same in whatever order the excluded features are named"() {
        expect:
        ['mysql', 'mockito', 'scaffolding'].permutations().collect { List<String> excluded ->
            generate(['postgres'], [], ApplicationType.WEB, excluded).findAll { it.key.startsWith('.grails/') }
        }.toSet().size() == 1
    }

    @Unroll
    void "excluding #excluded with #selected selected is refused: #problem"() {
        given:
        List<Pack> packs = [pack('acme-audit', 'requires: [acme-base]\n'), pack('acme-base', 'requires: [database-migration]\n'),
                            pack('acme-rest', 'applicationTypes: [rest-api]\n')]

        when:
        context(selected, packs, ApplicationType.WEB, excluded)

        then:
        FeatureSelectionException e = thrown()
        e.message == message

        where:
        problem                                    | selected        | excluded                || message
        'there is no such feature'                 | []              | ['acme-missing']        || 'The excluded feature does not exist: acme-missing'
        'it is of another type of application'     | []              | ['acme-rest']           || 'The excluded feature does not exist: acme-rest'
        'it is selected too'                       | ['postgres']    | ['postgres']            || 'The feature postgres is both selected and excluded'
        'every application has it'                 | []              | ['logback']             || 'The feature logback cannot be excluded: it is not a feature that is selected by name'
        'an option chooses it'                     | []              | ['spring-boot-devtools'] || 'The feature spring-boot-devtools cannot be excluded: it is not a feature that is selected by name'
        'a selected pack requires it'              | ['acme-base']   | ['database-migration']  || 'The feature database-migration cannot be excluded: the feature acme-base requires it'
        'a pack requires it through another'       | ['acme-audit']  | ['database-migration']  || 'The feature database-migration cannot be excluded: the feature acme-base requires it (acme-audit requires acme-base)'
        'a required pack'                          | ['acme-audit']  | ['acme-base']           || 'The feature acme-base cannot be excluded: the feature acme-audit requires it'
        'the default of a group a default needs'   | []              | ['h2']                  || 'The feature h2 cannot be excluded: the feature gorm-hibernate5 requires it'
        'the Grails Data implementation'           | []              | ['gorm-hibernate5']     || 'The feature gorm-hibernate5 cannot be excluded: it is the Grails Data implementation of the application. Choose another one (--data), or leave the exclusion out'
        'the implementation and its driver'        | []              | ['gorm-hibernate5', 'h2'] || 'The feature gorm-hibernate5 cannot be excluded: it is the Grails Data implementation of the application. Choose another one (--data), or leave the exclusion out'
        'several that are no features'             | []              | ['zzz-missing', 'mockito', 'aaa-missing'] || 'The excluded features do not exist: aaa-missing, zzz-missing'
        'a default another default needs'          | []              | ['spock']               || 'The feature spock cannot be excluded: the feature geb-with-testcontainers requires it'
        'a default a selected feature brings in'   | ['postgres']    | ['gorm-hibernate5']     || 'The feature gorm-hibernate5 cannot be excluded: it is the Grails Data implementation of the application. Choose another one (--data), or leave the exclusion out'
        'a feature a selected feature brings in'   | ['postgres']    | ['testcontainers']      || 'The feature testcontainers cannot be excluded: the feature postgres requires it'
    }

    @Unroll
    void "a #type application without one of its defaults is still one that can be built: #name"() {
        given: 'what an application of the type always has'
        GeneratorContext whole = context([], [], type)
        List<String> structure = whole.features.features.findAll { !it.visible }*.name
        Map<String, String> complete = generate([], [], type)
        List<String> plugins = grailsPlugins(complete['build.gradle'])

        expect: 'the type has such parts, and the application of the type has what is asked of the others below'
        structure.containsAll(['base', 'gradle', 'grails-gradle-plugin', 'yaml'])
        !plugins.isEmpty()
        complete.keySet().containsAll(['build.gradle', 'gradle.properties', 'grails-app/conf/application.yml', 'grails-app/init/example/grails/Application.groovy'])

        when:
        Map<String, String> output = null
        String refusal = null
        try {
            output = generate([], [], type, [name])
        } catch (FeatureSelectionException e) {
            refusal = e.message
        }

        then: 'it is refused for a reason, or it is an application'
        output != null || refusal ==~ "The feature ${name} cannot be excluded: (the feature [a-z0-9-]+ requires it|" +
                'the application would be without the feature [a-z0-9-]+, which it has to have|' +
                'what the feature [a-z0-9-]+ generates relies on it|' +
                'it is the Grails Data implementation of the application\\. Choose another one \\(--data\\), or leave the exclusion out)'

        and: 'that has every part an application of the type is built on'
        output == null || featuresOf(output).containsAll(structure)
        output == null || !featuresOf(output).contains(name)

        and: 'applies the Grails Gradle plugins of its type, and has its configuration, its class and its build'
        output == null || grailsPlugins(output['build.gradle']) == plugins
        output == null || output.keySet().containsAll(['build.gradle', 'gradle.properties', 'grails-app/conf/application.yml', 'grails-app/init/example/grails/Application.groovy'])

        and: 'and is recorded as what it is'
        output == null || featuresOf(output).contains('gorm-' + (output['.grails/project.yml'] =~ /(?m)^  data: (.+)$/)[0][1])
        output == null || output['.grails/project.yml'].contains("excludedFeatures:\n- name: ${name}\n")

        where:
        [type, name] << ApplicationType.values().toList().collectMany { ApplicationType each ->
            visibleDefaults(each).collect { String visible -> [each, visible] }
        }
    }

    void "every type of application has defaults that can be left out, and some that cannot"() {
        expect:
        ApplicationType.values().every { ApplicationType type ->
            List<String> outcomes = visibleDefaults(type).collect { String name ->
                try {
                    context([], [], type, [name])
                    return 'left out'
                } catch (FeatureSelectionException ignored) {
                    return 'refused'
                }
            }
            outcomes.contains('left out') && outcomes.contains('refused')
        }
    }

    void "a feature that alone brings in a part the application is built on cannot be excluded"() {
        when: 'the JSON views bring the web layer into a REST API, and with it the Grails Gradle plugin'
        context([], [], ApplicationType.REST_API, ['views-json'])

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The feature views-json cannot be excluded: the application would be without the feature grails-web, which it has to have'

        when: 'with another feature left out too, the two are named'
        context([], [], ApplicationType.REST_API, ['views-json', 'mockito'])

        then:
        FeatureSelectionException both = thrown()
        both.message == 'The features mockito, views-json cannot be excluded together: the application would be without the feature grails-web, which it has to have'

        expect: 'where another feature brings that part in, the views can be left out'
        !context(['views-markup'], [], ApplicationType.REST_API, ['views-json']).features.contains('views-json')
        context(['views-markup'], [], ApplicationType.REST_API, ['views-json']).features.contains('grails-web')
    }

    @Unroll
    void "the Grails Data implementation #gorm chooses cannot be excluded, since no option says there is none"() {
        when:
        beanContext.getBean(ProjectGenerator).createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'),
                new Options().withGormImpl(gorm), OperatingSystem.LINUX, [], ConsoleOutput.NOOP, [], [gorm.name])

        then:
        FeatureSelectionException e = thrown()
        e.message == "The feature ${gorm.name} cannot be excluded: it is the Grails Data implementation of the application. " +
                'Choose another one (--data), or leave the exclusion out'

        where:
        gorm << GormImpl.values().toList()
    }

    void "the implementation an option does not choose can be excluded, and the record names the one the application has"() {
        when:
        Map<String, String> output = generate(['gorm-hibernate7'], [], ApplicationType.WEB, ['gorm-hibernate5'])

        then:
        featuresOf(output).contains('gorm-hibernate7')
        !featuresOf(output).contains('gorm-hibernate5')
        output['.grails/project.yml'].contains('  data: hibernate7\n')
    }

    @Unroll
    void "the application is the same in whatever order #selected are selected and #excluded excluded"() {
        given:
        List<Pack> packs = [
                pack('acme-audit', "requires: [acme-base, database-migration]\ndependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-audit\n" +
                        "configuration:\n  acme:\n    audit: true\nfiles:\n  - from: f.txt.gsp\n    to: audit.txt\n", ['f.txt.gsp': '${features.join(",")}']),
                pack('acme-base', "requires: [acme-audit]\ndependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-base\n" +
                        "build:\n  extensions:\n    acme: 'base = true'\n"),
                pack('acme-one', "oneOf: com.acme:choice\nconflicts: [gorm-mongodb]\nreadme: One.\n"),
                pack('acme-other', "build:\n  extensions:\n    acme: 'other = true'\n  properties:\n    acmeVersion: '1'\n"),
        ]
        Map<String, String> expected = generate(selected, packs, type, excluded)

        expect: 'the same files with the same content, written in the same order, also when the packs are given in another order'
        selected.permutations().every { List<String> order ->
            (excluded ? excluded.permutations() : [[]]).every { List<String> left ->
                Map<String, String> output = generate(order, packs.reverse(), type, left)
                output == expected && output.keySet().toList() == expected.keySet().toList()
            }
        }

        where:
        type                     | selected                                                        | excluded
        ApplicationType.WEB      | ['acme-audit', 'acme-one', 'postgres', 'acme-other']            | []
        ApplicationType.WEB      | ['acme-base', 'acme-other', 'cache-ehcache', 'asciidoctor']     | ['mockito', 'scaffolding']
        ApplicationType.WEB      | ['gorm-hibernate7', 'mysql', 'grails-quartz', 'acme-one']       | ['scaffolding', 'geb-with-testcontainers', 'postgres']
        ApplicationType.REST_API | ['acme-other', 'acme-audit', 'gorm-mongodb']                    | ['mockito', 'spock']
        ApplicationType.PLUGIN   | ['acme-one', 'acme-base', 'shade']                              | ['mockito']
    }

    void "what is refused is the same in whatever order the features are named"() {
        given:
        List<Pack> packs = [pack('acme-a', 'conflicts: [acme-b, acme-c]\n'), pack('acme-b', 'conflicts: [acme-c]\n'), pack('acme-c', 'requires: [acme-missing-one]\n'),
                            pack('acme-d', 'requires: [acme-missing-two]\n')]

        expect:
        ['acme-a', 'acme-b', 'acme-c'].permutations().collect { refusal(it, packs) }.toSet() == ['The feature acme-c requires the feature acme-missing-one, which there is not for this type of application'].toSet()
        ['acme-d', 'acme-c'].permutations().collect { refusal(it, packs) }.toSet() == ['The feature acme-c requires the feature acme-missing-one, which there is not for this type of application'].toSet()
        ['acme-a', 'acme-b'].permutations().collect { refusal(it, packs) }.toSet() == ['The feature acme-a cannot be used with the feature acme-b'].toSet()
    }

    void "what is refused is the same in whatever order features of the framework are named"() {
        expect:
        [['postgres', 'mysql', 'h2'], ['zzz-missing', 'postgres', 'aaa-missing', 'mmm-missing'], ['gorm-hibernate5', 'gorm-hibernate7', 'mysql', 'postgres'],
         ['gorm-mongodb', 'gorm-neo4j', 'no-such']].every { List<String> selection ->
            selection.permutations().collect { refusal(it, []) }.toSet().size() == 1 && refusal(selection, []) != null
        }
        refusal(['zzz-missing', 'postgres', 'aaa-missing', 'mmm-missing'], []) == 'The requested features do not exist: aaa-missing, mmm-missing, zzz-missing'
        refusal(['postgres', 'no-such'], []) == 'The requested feature does not exist: no-such'
        refusal((1..14).collect { "missing-${it}".toString() }, []) == 'The requested features do not exist: ' +
                'missing-1, missing-10, missing-11, missing-12, missing-13, missing-14, missing-2, missing-3, missing-4, missing-5 and 4 more'
    }

    @Unroll
    void "a name that is given is shown in one bounded line that is no markup: #what"() {
        when:
        context(selected, [], ApplicationType.WEB, excluded)

        then:
        FeatureSelectionException e = thrown()
        e.message == message
        !e.message.contains('@|')
        !(e.message =~ /\p{Cntrl}/)
        e.message.length() < 200

        where:
        what                           | selected               | excluded                      || message
        'markup, selected'             | ['@|red x|@']          | []                            || 'The requested feature does not exist: @ |red x|@'
        'markup of no style, selected' | ['@|bogus x|@']        | []                            || 'The requested feature does not exist: @ |bogus x|@'
        'markup, excluded'             | []                     | ['@|bogus x|@']               || 'The excluded feature does not exist: @ |bogus x|@'
        'an escape and a line'         | []                     | ['a\u001b[31mb\nsecond']      || 'The excluded feature does not exist: a?[31mb?second'
        'a line, selected'             | ['a\r\nb\u2028c\u202ed'] | []                            || 'The requested feature does not exist: a??b?c?d'
        'a long name'                  | []                     | ['x' * 3000]                  || 'The excluded feature does not exist: ' + ('x' * 80) + '...'
        'a long name, selected'        | ['y' * 3000]           | []                            || 'The requested feature does not exist: ' + ('y' * 80) + '...'
        'no name'                      | []                     | ['']                          || 'The excluded feature does not exist: (empty)'
        'no name, selected'            | ['']                   | []                            || 'The requested feature does not exist: (empty)'
    }

    void "a long way to a requirement there is not is shown by its ends"() {
        given:
        List<Pack> packs = (1..40).collect { int i ->
            pack("acme-${String.format('%02d', i)}", "requires: [${i < 40 ? "acme-${String.format('%02d', i + 1)}" : 'acme-missing'}]\n")
        }

        when:
        context(['acme-01'], packs)

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The feature acme-40 requires the feature acme-missing, which there is not for this type of application ' +
                '(acme-01 requires acme-02 requires acme-03 requires ... 34 more ... requires acme-38 requires acme-39 requires acme-40)'
    }

    @Unroll
    void "an option that is given is a choice the features do not replace: #given #options.gormImpl #options.servletImpl #options.developmentReloading with #selected"() {
        given:
        List<Pack> packs = [pack('acme-jetty', 'requires: [spring-boot-starter-jetty]\n'), pack('acme-jrebel', 'requires: [jrebel]\n'),
                            pack('acme-devtools', 'requires: [spring-boot-devtools]\n')]

        when:
        beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'), options.withGiven(given),
                OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, packs, [])

        then:
        FeatureSelectionException e = thrown()
        e.message == message
        e.option == given[0]
        (e.without ?: e.with) != null

        when: 'the same without saying that the option was given: it is a default, and gives way'
        GeneratorContext context = beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'), options,
                OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, packs, [])
        Options recorded = context.applicationOptions

        then: 'and what is recorded is what the application has'
        [recorded.gormImpl, recorded.servletImpl, recorded.developmentReloading] == has
        context.features.contains(recorded.gormImpl.name)
        recorded.servletImpl == ServletImpl.NONE || context.features.contains(recorded.servletImpl.name)

        where:
        type                | given                  | options                                                     | selected              || has                                                                    | message
        ApplicationType.WEB | [OptionName.DATA]      | new Options()                                               | ['gorm-hibernate7']   || [GormImpl.HIBERNATE7, ServletImpl.TOMCAT, DevelopmentReloading.DEVTOOLS] | 'The Grails Data implementation hibernate5 that was asked for does not agree with the features that are selected or excluded: the application would be made without the feature gorm-hibernate5'
        ApplicationType.WEB | [OptionName.SERVLET]   | new Options().withServletImpl(ServletImpl.NONE)             | ['acme-jetty']        || [GormImpl.HIBERNATE5, ServletImpl.JETTY, DevelopmentReloading.DEVTOOLS]  | 'The servlet container none that was asked for does not agree with the features that are selected or excluded: the application would be made with the feature spring-boot-starter-jetty (acme-jetty requires spring-boot-starter-jetty)'
        ApplicationType.WEB | [OptionName.RELOADING] | new Options().withDevelopmentReloading(DevelopmentReloading.NONE) | ['acme-jrebel'] || [GormImpl.HIBERNATE5, ServletImpl.TOMCAT, DevelopmentReloading.JREBEL]    | 'The development reloading none that was asked for does not agree with the features that are selected or excluded: the application would be made with the feature jrebel (acme-jrebel requires jrebel)'
        ApplicationType.WEB | [OptionName.RELOADING] | new Options().withDevelopmentReloading(DevelopmentReloading.NONE) | ['acme-devtools'] || [GormImpl.HIBERNATE5, ServletImpl.TOMCAT, DevelopmentReloading.DEVTOOLS] | 'The development reloading none that was asked for does not agree with the features that are selected or excluded: the application would be made with the feature spring-boot-devtools (acme-devtools requires spring-boot-devtools)'
        ApplicationType.WEB | [OptionName.RELOADING] | new Options().withJavaVersion(JdkVersion.JDK_25)            | ['grails-micronaut']  || [GormImpl.HIBERNATE5, ServletImpl.TOMCAT, DevelopmentReloading.NONE]      | 'The development reloading devtools that was asked for does not agree with the features that are selected or excluded: the application would be made without Spring Boot DevTools'
    }

    @Unroll
    void "the default servlet container or reloader stands aside for #selected when the option was not given, and does not when it was"() {
        given:
        List<Pack> packs = [pack('acme-jetty', 'requires: [spring-boot-starter-jetty]\n'), pack('acme-jrebel', 'requires: [jrebel]\n'),
                            pack('acme-chain', 'requires: [acme-middle]\n'), pack('acme-middle', 'requires: [acme-jetty]\n')]
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)

        when: 'the option has its value, and nobody said that it was given'
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), options,
                OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, packs, [])
        Options recorded = context.applicationOptions

        then: 'the feature that was selected or is required is there, and not the default'
        context.features.containsAll(has)
        !context.features.any { it in hasNot }
        [recorded.gormImpl, recorded.servletImpl, recorded.developmentReloading] == record

        when: 'the same option, given'
        generator.createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), options.withGiven([given]),
                OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, packs, [])

        then: 'the two are refused together'
        FeatureSelectionException e = thrown()
        e.message == message

        where:
        options                                    | given                | selected            || has                           | hasNot                         | record                                                                 | message
        new Options()                              | OptionName.SERVLET   | ['acme-jetty']      || ['spring-boot-starter-jetty'] | ['spring-boot-starter-tomcat'] | [GormImpl.HIBERNATE5, ServletImpl.JETTY, DevelopmentReloading.DEVTOOLS] | 'There can only be one of the following features selected: [spring-boot-starter-jetty, spring-boot-starter-tomcat]'
        new Options()                              | OptionName.SERVLET   | ['acme-chain']      || ['spring-boot-starter-jetty'] | ['spring-boot-starter-tomcat'] | [GormImpl.HIBERNATE5, ServletImpl.JETTY, DevelopmentReloading.DEVTOOLS] | 'There can only be one of the following features selected: [spring-boot-starter-jetty, spring-boot-starter-tomcat]'
        new Options().withServletImpl(ServletImpl.UNDERTOW) | OptionName.SERVLET | ['acme-jetty'] || ['spring-boot-starter-jetty'] | ['grails-undertow']           | [GormImpl.HIBERNATE5, ServletImpl.JETTY, DevelopmentReloading.DEVTOOLS] | 'There can only be one of the following features selected: [grails-undertow, spring-boot-starter-jetty]'
        new Options()                              | OptionName.RELOADING | ['acme-jrebel']     || ['jrebel']                    | ['spring-boot-devtools']       | [GormImpl.HIBERNATE5, ServletImpl.TOMCAT, DevelopmentReloading.JREBEL]  | 'There can only be one of the following features selected: [jrebel, spring-boot-devtools]'
    }

    @Unroll
    void "a Grails Data implementation that is not the default does not stand aside for #selected, given or not: #gorm"() {
        when: 'the caller passes the implementation and does not say that it was given'
        beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'), new Options().withGormImpl(gorm),
                OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, [], [])

        then: 'it is its choice all the same: the persistence layer is not swapped without a word'
        FeatureSelectionException e = thrown()
        e.message == message

        when: 'and says so'
        beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'),
                new Options().withGormImpl(gorm).withGiven([OptionName.DATA]), OperatingSystem.LINUX, selected, ConsoleOutput.NOOP, [], [])

        then:
        FeatureSelectionException given = thrown()
        given.message == message

        where:
        [type, gorm, selected, message] << ApplicationType.values().toList().collectMany { ApplicationType each ->
            [[each, GormImpl.HIBERNATE7, ['gorm-hibernate5'], 'Only one Grails Data for Hibernate implementation can be selected: gorm-hibernate5 or gorm-hibernate7'],
             [each, GormImpl.NEO4J, ['gorm-mongodb'], 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'],
             [each, GormImpl.MONGODB, ['gorm-neo4j'], 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]']]
        }
    }

    void "the default Grails Data implementation gives way to a selected Hibernate 7 when it was not given, and only then"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)

        when:
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), new Options(),
                OperatingSystem.LINUX, ['gorm-hibernate7'], ConsoleOutput.NOOP, [], [])

        then:
        context.features.contains('gorm-hibernate7')
        !context.features.contains('gorm-hibernate5')
        context.applicationOptions.gormImpl == GormImpl.HIBERNATE7

        when:
        generator.createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), new Options().withGiven([OptionName.DATA]),
                OperatingSystem.LINUX, ['gorm-hibernate7'], ConsoleOutput.NOOP, [], [])

        then:
        FeatureSelectionException e = thrown()
        e.option == OptionName.DATA
    }

    void "a given option for none names the way to the feature that is there all the same"() {
        given:
        List<Pack> packs = [pack('acme-jetty', 'requires: [spring-boot-starter-jetty]\n'),
                            pack('acme-chain', 'requires: [acme-middle]\n'), pack('acme-middle', 'requires: [acme-jetty]\n')]

        when:
        beanContext.getBean(ProjectGenerator).createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'),
                new Options().withServletImpl(ServletImpl.NONE).withGiven([OptionName.SERVLET]), OperatingSystem.LINUX, ['acme-chain'], ConsoleOutput.NOOP, packs, [])

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The servlet container none that was asked for does not agree with the features that are selected or excluded: the application would be made ' +
                'with the feature spring-boot-starter-jetty (acme-chain requires acme-middle requires acme-jetty requires spring-boot-starter-jetty)'
    }

    @Unroll
    void "what is recorded of the options of a #type application is what it has: #servlet"() {
        when:
        GeneratorContext context = beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'),
                new Options(), OperatingSystem.LINUX, [], ConsoleOutput.NOOP, [], [])
        Map<String, String> output = generate([], [], type)

        then:
        context.applicationOptions.servletImpl == servlet
        context.features.contains('spring-boot-starter-tomcat') == (servlet == ServletImpl.TOMCAT)
        output['.grails/project.yml'].contains("  servlet: ${servlet.name().toLowerCase()}\n")
        output['.grails/forge.lock.yml'].contains("  servlet: ${servlet.name().toLowerCase()}\n")
        featuresOf(output).contains(context.applicationOptions.gormImpl.name)

        where:
        type                       || servlet
        ApplicationType.WEB        || ServletImpl.TOMCAT
        ApplicationType.REST_API   || ServletImpl.TOMCAT
        ApplicationType.WEB_PLUGIN || ServletImpl.TOMCAT
        ApplicationType.REST_API_PLUGIN || ServletImpl.TOMCAT
        ApplicationType.PLUGIN     || ServletImpl.NONE
    }

    void "a template of a pack is told the options the application has"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: f.txt.gsp\n    to: f.txt\n", ['f.txt.gsp': '${options.servlet} ${options.data} ${options.reloading}'])

        expect: 'a plugin has no servlet container, whatever the default of the option is'
        generate(['acme-audit'], [pack], ApplicationType.PLUGIN)['f.txt'] == 'none hibernate5 devtools'
        generate(['acme-audit'], [pack], ApplicationType.WEB)['f.txt'] == 'tomcat hibernate5 devtools'
        generate(['acme-audit', 'gorm-hibernate7'], [pack], ApplicationType.WEB)['f.txt'] == 'tomcat hibernate7 devtools'
    }

    @Unroll
    void "#excluded cannot be excluded from a #type application, where what #by generates relies on it"() {
        when:
        context([], [], type, excluded)

        then:
        FeatureSelectionException e = thrown()
        e.message == "The feature ${relied} cannot be excluded: what the feature ${by} generates relies on it"

        where:
        type                       | excluded                                   || relied                  | by
        ApplicationType.WEB        | ['asset-pipeline-grails']                  || 'asset-pipeline-grails' | 'grails-url-mappings'
        ApplicationType.WEB_PLUGIN | ['asset-pipeline-grails']                  || 'asset-pipeline-grails' | 'gsp'
        ApplicationType.WEB        | ['gsp', 'scaffolding']                     || 'gsp'                   | 'geb-with-testcontainers'
        ApplicationType.WEB        | ['gsp', 'scaffolding', 'geb-with-testcontainers'] || 'gsp'            | 'grails-url-mappings'
        ApplicationType.WEB_PLUGIN | ['gsp', 'scaffolding']                     || 'gsp'                   | 'grails-url-mappings'
        ApplicationType.WEB        | ['asset-pipeline-grails', 'gsp', 'scaffolding', 'geb-with-testcontainers'] || 'gsp'            | 'grails-url-mappings'
    }

    void "a feature relied on is named with the way to the feature that relies on it"() {
        given: 'a pack that brings in the pages, in an application that has none by default'
        Pack pack = pack('acme-pages', 'requires: [gsp]\n')

        expect: 'selection does not add what is relied on: the pages are there without the assets, as before'
        context(['acme-pages'], [pack], ApplicationType.REST_API).features.contains('gsp')
        !context(['acme-pages'], [pack], ApplicationType.REST_API).features.contains('asset-pipeline-grails')

        when: 'but it cannot be excluded where it would be there'
        context(['acme-pages'], [pack], ApplicationType.WEB_PLUGIN, ['asset-pipeline-grails'])

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The feature asset-pipeline-grails cannot be excluded: what the feature gsp generates relies on it (acme-pages requires gsp)'
    }

    @Unroll
    void "no view or mapping of a #type application without #excluded uses what a feature that is absent provides"() {
        given: 'which feature provides what a view or a mapping can use'
        Map<String, String> tagFeatures = ['asset': 'asset-pipeline-grails', 'g': 'gsp', 'sitemesh': 'sitemesh3']
        Map<String, String> viewFeatures = ['.gsp': 'gsp', '.gson': 'views-json', '.gml': 'views-markup']

        when:
        Map<String, String> output = generate([], [], type, excluded)
        List<String> features = featuresOf(output)
        Map<String, String> views = output.findAll { it.key.startsWith('grails-app/views/') }
        String mappings = output.find { it.key.endsWith('/UrlMappings.groovy') }?.value ?: ''
        List<String> mapped = (mappings =~ /view: ?["']\/([A-Za-z]+)["']/).collect { List match -> match[1] as String }

        then: 'every view is of a view layer the application has'
        views.keySet().every { String path -> features.contains(viewFeatures.find { path.endsWith(it.key) }?.value) }

        and: 'every tag of a page is of a feature the application has'
        views.findAll { it.key.endsWith('.gsp') }.every { String path, String text ->
            (text =~ /<([a-z][a-z0-9]*):[A-Za-z]/).collect { List match -> match[1] as String }.unique().every { String namespace ->
                features.contains(tagFeatures[namespace])
            }
        }

        and: 'every view a mapping names is generated, and a mapping to the assets has the assets'
        mapped.every { String view -> views.keySet().any { it.startsWith("grails-app/views/${view}.") } }
        !mappings.contains('/assets/') || features.contains('asset-pipeline-grails')

        and: 'a web application has the pages its mappings name, and a REST API plugin maps nothing'
        type in [ApplicationType.PLUGIN, ApplicationType.REST_API_PLUGIN] || !mapped.isEmpty()
        type != ApplicationType.REST_API_PLUGIN || (mapped.isEmpty() && views.isEmpty())

        where:
        [type, excluded] << ApplicationType.values().toList().collectMany { ApplicationType each ->
            [[each, []]] + acceptedExclusions(each).collect { List<String> names -> [each, names] }
        }
    }

    void "the exclusions that are accepted are those the generated applications were built and run with"() {
        expect: 'one at a time and in pairs, of the features an application has by default and that can be selected by name'
        acceptedExclusions(ApplicationType.WEB) == [['geb-with-testcontainers'], ['mockito'], ['scaffolding'],
                                                    ['geb-with-testcontainers', 'mockito'], ['geb-with-testcontainers', 'scaffolding'],
                                                    ['geb-with-testcontainers', 'spock'], ['mockito', 'scaffolding']]
        acceptedExclusions(ApplicationType.WEB_PLUGIN) == [['mockito'], ['scaffolding'], ['spock'], ['mockito', 'scaffolding'], ['mockito', 'spock'],
                                                           ['scaffolding', 'spock']]
        acceptedExclusions(ApplicationType.REST_API) == [['mockito'], ['spock'], ['mockito', 'spock']]
        acceptedExclusions(ApplicationType.REST_API_PLUGIN) == [['mockito'], ['spock'], ['mockito', 'spock']]
        acceptedExclusions(ApplicationType.PLUGIN) == [['mockito'], ['spock'], ['mockito', 'spock']]
    }

    // the single visible defaults and the pairs of them that can be excluded from an application of a type
    private List<List<String>> acceptedExclusions(ApplicationType type) {
        List<String> defaults = visibleDefaults(type).toSorted()
        List<List<String>> candidates = defaults.collect { [it] } +
                defaults.collectMany { String first -> defaults.findAll { it > first }.collect { String second -> [first, second] } }
        return candidates.findAll { List<String> names ->
            try {
                context([], [], type, names)
                return true
            } catch (FeatureSelectionException ignored) {
                return false
            }
        }
    }

    void "an option that chooses nothing for a type of application agrees with every application of the type"() {
        when: 'a plugin has no servlet container to choose'
        GeneratorContext context = beanContext.getBean(ProjectGenerator).createGeneratorContext(ApplicationType.PLUGIN, NameUtils.parse('example.grails.foo'),
                new Options().withGiven([OptionName.SERVLET, OptionName.DATA, OptionName.RELOADING]), OperatingSystem.LINUX, [], ConsoleOutput.NOOP, [], [])

        then:
        !context.features.contains('spring-boot-starter-tomcat')
        context.applicationOptions.servletImpl == ServletImpl.NONE
        context.applicationOptions.given == [OptionName.SERVLET, OptionName.DATA, OptionName.RELOADING] as Set
    }

    void "options that are given and agree with the features are accepted, and recorded as they are"() {
        when:
        Options options = new Options(DevelopmentReloading.JREBEL, GormImpl.MONGODB, ServletImpl.JETTY, JdkVersion.DEFAULT_OPTION)
                .withGiven(OptionName.values().toList())
        GeneratorContext context = beanContext.getBean(ProjectGenerator).createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'),
                options, OperatingSystem.LINUX, ['gorm-hibernate7', 'postgres'], ConsoleOutput.NOOP, [], [])

        then:
        context.features.containsAll(['gorm-mongodb', 'gorm-hibernate7', 'postgres', 'spring-boot-starter-jetty', 'jrebel'])
        [context.applicationOptions.gormImpl, context.applicationOptions.servletImpl, context.applicationOptions.developmentReloading] ==
                [GormImpl.MONGODB, ServletImpl.JETTY, DevelopmentReloading.JREBEL]
    }

    private List<String> visibleDefaults(ApplicationType type) {
        return context([], [], type).features.features.findAll { it.visible }*.name
    }

    private static List<String> featuresOf(Map<String, String> output) {
        // the lock names every feature of the application
        return (output['.grails/forge.lock.yml'] =~ /(?m)^- name: (.+)$/).collect { List<String> match -> match[1] }
    }

    // the lines of a build file that apply a Gradle plugin of Grails
    private static List<String> grailsPlugins(String buildGradle) {
        return buildGradle.readLines()*.trim().findAll { it.contains('org.apache.grails.gradle.grails-') }.toSorted()
    }

    private String refusal(List<String> selected, List<Pack> packs) {
        try {
            context(selected, packs)
            return null
        } catch (IllegalArgumentException e) {
            return e.message
        }
    }

    // the files of the application without the state of the project, which has the names that were asked for
    private static Map<String, String> application(Map<String, String> output) {
        return output.findAll { !it.key.startsWith('.grails/') }
    }

    private Pack pack(String name, String body, Map<String, String> files = [:]) {
        Path packDirectory = Files.createDirectories(directory.resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), HEADER + 'name: ' + name + '\n' + body)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text ->
            Files.writeString(packDirectory.resolve('templates').resolve(path), text)
        }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private GeneratorContext context(List<String> features, List<Pack> packs, ApplicationType type = ApplicationType.WEB, List<String> excluded = []) {
        return beanContext.getBean(ProjectGenerator).createGeneratorContext(type, NameUtils.parse('example.grails.foo'), new Options(),
                OperatingSystem.LINUX, new ArrayList<String>(features), ConsoleOutput.NOOP, packs, new ArrayList<String>(excluded))
    }

    private Map<String, String> generate(List<String> features, List<Pack> packs, ApplicationType type = ApplicationType.WEB, List<String> excluded = []) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        generator.generate(type, project, output, context(features, packs, type, excluded))
        return output.getProject()
    }

    static class RequiringDefault implements DefaultFeature {

        final String name
        final List<String> dependentFeatures

        RequiringDefault(String name, List<String> dependentFeatures) {
            this.name = name
            this.dependentFeatures = dependentFeatures
        }

        @Override
        boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
            true
        }

        @Override
        boolean supports(ApplicationType applicationType) {
            true
        }
    }

    static class WithMore implements AvailableFeatures {

        private final AvailableFeatures framework
        private final Map<String, Feature> more

        WithMore(AvailableFeatures framework, List<Feature> more) {
            this.framework = framework
            this.more = more.collectEntries { [it.name, it] }
        }

        @Override
        Iterator<String> iterator() {
            getFeatures().map { it.name }.iterator()
        }

        @Override
        Optional<Feature> findFeature(String name) {
            findFeature(name, false)
        }

        @Override
        Optional<Feature> findFeature(String name, boolean ignoreVisibility) {
            more.containsKey(name) ? Optional.of(more[name]) : framework.findFeature(name, ignoreVisibility)
        }

        @Override
        Stream<Feature> getFeatures() {
            getAllFeatures().filter { it.visible }
        }

        @Override
        Stream<Feature> getAllFeatures() {
            Stream.concat(framework.getAllFeatures(), more.values().stream())
        }
    }
}
