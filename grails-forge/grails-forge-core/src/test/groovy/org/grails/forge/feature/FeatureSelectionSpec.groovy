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
import org.grails.forge.options.Options
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
        output['grails-forge-cli.yml'].count('acme-base') == 1

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
        IllegalArgumentException e = thrown()
        e.message == 'The feature database-migration cannot be excluded: the feature acme-needed requires it (acme-default requires acme-needed)'
    }

    @Unroll
    void "a pack is refused with a feature it conflicts with that #how, when the features are chosen"() {
        given:
        List<Pack> packs = descriptors.collect { String name, String body -> pack(name, body) }

        when: 'nothing is applied and no template runs: only the context is made'
        context(selected, packs)

        then:
        IllegalArgumentException e = thrown()
        e.message == message

        where:
        how                                 | selected                 | descriptors                                                                         || message
        'is selected'                       | ['acme-audit', 'postgres'] | ['acme-audit': 'conflicts: [postgres]\n']                                         || 'The feature acme-audit cannot be used with the feature postgres'
        'is a default'                      | ['acme-audit']           | ['acme-audit': 'conflicts: [h2]\n']                                                 || 'The feature acme-audit cannot be used with the feature h2'
        'is a default nobody can select'    | ['acme-audit']           | ['acme-audit': 'conflicts: [logback]\n']                                            || 'The feature acme-audit cannot be used with the feature logback'
        'another feature brings in'         | ['acme-audit', 'postgres'] | ['acme-audit': 'conflicts: [testcontainers]\n']                                   || 'The feature acme-audit cannot be used with the feature testcontainers'
        'another pack requires'             | ['acme-audit', 'acme-b'] | ['acme-audit': 'conflicts: [database-migration]\n', 'acme-b': 'requires: [database-migration]\n'] || 'The feature acme-audit cannot be used with the feature database-migration'
        'is a pack that requires this one'  | ['acme-b']               | ['acme-audit': 'conflicts: [acme-b]\n', 'acme-b': 'requires: [acme-audit]\n']       || 'The feature acme-audit cannot be used with the feature acme-b'
    }

    void "a pack that conflicts with a feature the application has not is used"() {
        given:
        Pack pack = pack('acme-audit', 'conflicts: [postgres, gorm-mongodb]\n')

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
        output['grails-forge-cli.yml'].endsWith("]\nexcludedFeatures: ${excluded.toSorted()}\n")

        where:
        type                     | excluded                                        || alsoLeftOut
        ApplicationType.WEB      | ['asset-pipeline-grails']                       || []
        ApplicationType.WEB      | ['geb-with-testcontainers']                     || []
        ApplicationType.WEB      | ['mockito']                                     || []
        ApplicationType.WEB      | ['scaffolding']                                 || []
        ApplicationType.WEB      | ['scaffolding', 'gsp']                          || []
        ApplicationType.WEB      | ['spock', 'mockito', 'geb-with-testcontainers'] || []
        ApplicationType.WEB      | ['h2', 'gorm-hibernate5']                       || []
        ApplicationType.REST_API | ['views-json']                                  || ['grails-web']
        ApplicationType.PLUGIN   | ['mockito']                                     || []
    }

    void "an application without excluded features is what it was and says nothing of them"() {
        when:
        String record = generate([], [])['grails-forge-cli.yml']

        then:
        !record.contains('excludedFeatures')
        record.readLines().last().startsWith('features: [')
        record.endsWith(']\n')
        !record.endsWith('\n\n')
        context([], []).excludedFeatures == []
    }

    void "excluding a feature the application would not have changes nothing but the record of it"() {
        when:
        Map<String, String> with = generate([], [], ApplicationType.WEB, ['postgres'])
        Map<String, String> without = generate([], [])

        then:
        with['grails-forge-cli.yml'] == without['grails-forge-cli.yml'] + 'excludedFeatures: [postgres]\n'
        with.findAll { !it.key.startsWith('.grails/') && it.key != 'grails-forge-cli.yml' } ==
                without.findAll { !it.key.startsWith('.grails/') && it.key != 'grails-forge-cli.yml' }
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
        state.manifest.excludedFeatures*.name == ['asset-pipeline-grails', 'mockito', 'mysql']
        state.manifest.excludedFeatures.every { it.coordinates == null && it.version == null }
        state.manifest.requestedFeatures*.name == ['postgres']
        !state.lock.featureNames.contains('mockito')
        target.resolve('.grails/project.yml').toFile().getText('UTF-8').contains(
                'excludedFeatures:\n- name: asset-pipeline-grails\n- name: mockito\n- name: mysql\n')

        where:
        excluded << ['mysql', 'mockito', 'asset-pipeline-grails'].permutations().toList() + [['mockito', 'mysql', 'mockito', 'asset-pipeline-grails']]
    }

    void "the state of a project is the same in whatever order the excluded features are named"() {
        expect:
        ['mysql', 'mockito', 'asset-pipeline-grails'].permutations().collect { List<String> excluded ->
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
        IllegalArgumentException e = thrown()
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
        'a default another default needs'          | []              | ['spock']               || 'The feature spock cannot be excluded: the feature geb-with-testcontainers requires it'
        'a default a selected feature brings in'   | ['postgres']    | ['gorm-hibernate5']     || 'The feature gorm-hibernate5 cannot be excluded: the feature postgres requires it'
        'a feature a selected feature brings in'   | ['postgres']    | ['testcontainers']      || 'The feature testcontainers cannot be excluded: the feature postgres requires it'
        'the first by name, whatever the order'    | []              | ['zzz-missing', 'aaa-missing'] || 'The excluded feature does not exist: aaa-missing'
    }

    @Unroll
    void "every feature a #type application has by default and can be selected by name is left out when excluded, or refused for what requires it"() {
        given:
        List<String> defaults = context([], [], type).features.features.findAll { it.visible }*.name

        expect:
        defaults.size() > 2

        and:
        defaults.every { String name ->
            try {
                Map<String, String> output = generate([], [], type, [name])
                return !output['grails-forge-cli.yml'].readLines().find { it.startsWith('features:') }.contains(" ${name},") &&
                        output['grails-forge-cli.yml'].contains("excludedFeatures: [${name}]")
            } catch (IllegalArgumentException e) {
                return e.message ==~ "The feature ${name} cannot be excluded: the feature [a-z0-9-]+ requires it"
            }
        }

        where:
        type << ApplicationType.values().toList()
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
        ApplicationType.WEB      | ['acme-base', 'acme-other', 'cache-ehcache', 'asciidoctor']     | ['mockito', 'asset-pipeline-grails']
        ApplicationType.WEB      | ['gorm-hibernate7', 'mysql', 'grails-quartz', 'acme-one']       | ['scaffolding', 'gsp', 'postgres']
        ApplicationType.REST_API | ['acme-other', 'acme-audit', 'gorm-mongodb']                    | ['views-json']
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
