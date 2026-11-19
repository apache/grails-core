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

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.IgnoreIf
import spock.lang.TempDir
import spock.lang.Unroll

class PackGenerationSpec extends ApplicationContextSpec {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    @TempDir
    Path directory

    void "a selected pack adds what its descriptor says to the application"() {
        given:
        Pack pack = pack('acme-audit', '''\
            title: Acme Audit Trail
            documentation: https://acme.example/docs
            dependencies:
              - scope: implementation
                coordinates: com.acme:acme-audit-grails
                version: 2.3.0
              - scope: runtimeOnly
                coordinates: com.acme:acme-audit-h2
                excludes: ['org.slf4j:slf4j-simple', commons-logging]
            platforms:
              - coordinates: com.acme:acme-bom
                version: 2.3.0
            buildPlugins:
              - id: com.acme.audit
                version: 2.3.0
            build:
              properties:
                acme.audit.verbose: false
              extensions:
                acmeAudit: |
                  retention = 90
                  sinks = ['file']
                assets: minifyJs = false
                tasks.bootRun: systemProperty 'acme.audit.console', 'true'
            configuration:
              acme.audit.enabled: true
              acme:
                audit:
                  sinks: [file, table]
              environments:
                production:
                  acme.audit.flush: 30s
            files:
              - from: conf/audit.txt
                to: 'grails-app/conf/{packagePath}/audit.txt'
            readme: |
              Audit events are written to the `audit_event` table.
            ''', ['conf/audit.txt': 'kept as it is: ${notATemplate}\n'])

        when:
        Map<String, String> output = generate(['acme-audit'], [pack])
        String buildGradle = output['build.gradle']

        then: 'the dependencies, with their exclusions, the platform and the plugin'
        buildGradle.contains('    implementation "com.acme:acme-audit-grails:2.3.0"\n')
        buildGradle.contains('    runtimeOnly("com.acme:acme-audit-h2") { exclude group: "org.slf4j", module: "slf4j-simple"; exclude group: "commons-logging" }\n')
        buildGradle.contains('    implementation platform("com.acme:acme-bom:2.3.0")\n')
        buildGradle.contains('    id "com.acme.audit" version "2.3.0"\n')

        and: 'a block of its own, a line in the block of another feature, and the configuration of tasks'
        buildGradle.contains("acmeAudit {\n    retention = 90\n    sinks = ['file']\n}\n\n")
        buildGradle.count('\nassets {\n') == 1
        buildGradle.contains('assets {\n    minifyJs = false\n    excludes = [\n')
        buildGradle.contains("tasks.named('bootRun') {\n    systemProperty 'acme.audit.console', 'true'\n}\n")

        and: 'the build property, the configuration of every environment and of one'
        output['gradle.properties'].contains('acme.audit.verbose=false\n')
        output['grails-app/conf/application.yml'].contains('acme:\n  audit:\n    enabled: true\n    sinks:\n')
        output['grails-app/conf/application.yml'].contains('    - file\n    - table\n')
        !output['grails-app/conf/application.yml'].contains('flush')
        output['grails-app/conf/application-production.yml'].contains('flush: 30s')

        and: 'the file, as it is, where the descriptor puts it'
        output['grails-app/conf/example/grails/audit.txt'] == 'kept as it is: ${notATemplate}\n'

        and: 'the readme and the link to its documentation'
        output['README.md'].contains('Audit events are written to the `audit_event` table.')
        output['README.md'].contains('https://acme.example/docs')

        and: 'its name among the features of the application'
        output['grails-forge-cli.yml'].contains('acme-audit')
    }

    void "a pack that is not selected adds nothing"() {
        given:
        Pack pack = pack('acme-audit', "dependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-audit-grails\n")

        expect:
        generate([], [pack]) == generate([], [])
    }

    void "a pack brings in the features it requires, of the framework and of other packs"() {
        given:
        Pack audit = pack('acme-audit', 'requires: [database-migration, acme-base]\n')
        Pack base = pack('acme-base', "requires: [acme-audit]\ndependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-base\n")

        when:
        Map<String, String> output = generate(['acme-audit'], [audit, base])

        then:
        output['build.gradle'].contains('grails-data-hibernate5-dbmigration')
        output['build.gradle'].contains('implementation "com.acme:acme-base"')
        output.containsKey('grails-app/migrations/.gitkeep')
        ['acme-audit', 'acme-base', 'database-migration'].every { output['grails-forge-cli.yml'].contains(it) }
    }

    @Unroll
    void "generation is refused when #problem"() {
        given:
        List<Pack> packs = descriptors.collect { String name, String body -> pack(name, body) }

        when:
        generate(selected, packs, type)

        then:
        IllegalArgumentException e = thrown()
        e.message.replace(directory.toString(), 'DIR').replace(File.separator, '/') == message

        where:
        problem                                    | type                     | selected                | descriptors                                                         || message
        'a pack requires a feature there is not'   | ApplicationType.WEB      | ['acme-audit']          | ['acme-audit': 'requires: [acme-missing]\n']                        || 'The feature acme-audit requires the feature acme-missing, which there is not for this type of application'
        'a pack requires one of another type'      | ApplicationType.REST_API | ['acme-audit']          | ['acme-audit': 'requires: [acme-web]\n', 'acme-web': 'applicationTypes: [web]\n'] || 'The feature acme-audit requires the feature acme-web, which there is not for this type of application'
        'a pack conflicts with a selected feature' | ApplicationType.WEB      | ['acme-audit', 'gorm-mongodb'] | ['acme-audit': 'conflicts: [gorm-mongodb]\n']                || 'The feature acme-audit cannot be used with the feature gorm-mongodb'
        'two packs of one group are selected'      | ApplicationType.WEB      | ['acme-a', 'acme-b']    | ['acme-a': 'oneOf: com.acme:audit\n', 'acme-b': 'oneOf: com.acme:audit\n'] || 'There can only be one of the following features selected: [acme-a, acme-b]'
        'a pack is for another type'               | ApplicationType.WEB      | ['acme-audit']          | ['acme-audit': 'applicationTypes: [rest-api]\n']                    || 'The requested feature does not exist: acme-audit'
        'a pack has the name of a feature'         | ApplicationType.WEB      | []                      | ['gsp': '']                                                         || 'The feature pack DIR/gsp has the name of a feature of the framework, gsp'
        'a pack has the name of a hidden feature'  | ApplicationType.WEB      | []                      | ['gradle': '']                                                      || 'The feature pack DIR/gradle has the name of a feature of the framework, gradle'
        'a pack joins a group of the framework'    | ApplicationType.WEB      | []                      | ['acme-db': 'oneOf: database-driver\n']                             || 'The feature pack DIR/acme-db names the exclusive group database-driver of the framework, which a pack cannot join yet'
        'a pack writes a file another feature has' | ApplicationType.WEB      | ['acme-audit']          | ['acme-audit': 'files:\n  - from: a.txt\n    to: build.gradle\n']   || 'The features write the file build.gradle more than once'
    }

    void "two packs cannot have one name"() {
        given:
        Pack first = pack('acme-audit', '', [:], 'one')
        Pack second = pack('acme-audit', '', [:], 'two')

        when:
        generate([], [first, second])

        then:
        IllegalArgumentException e = thrown()
        e.message.replace(directory.toString(), 'DIR').replace(File.separator, '/') == 'The feature packs DIR/one and DIR/two both have the name acme-audit'
    }

    void "one of two packs of a group can be selected, and the other pack is left out"() {
        given:
        Pack first = pack('acme-a', "oneOf: com.acme:audit\ndependencies:\n  - scope: implementation\n    coordinates: com.acme:a\n")
        Pack second = pack('acme-b', "oneOf: com.acme:audit\ndependencies:\n  - scope: implementation\n    coordinates: com.acme:b\n")

        when:
        String buildGradle = generate(['acme-b'], [first, second])['build.gradle']

        then:
        buildGradle.contains('"com.acme:b"')
        !buildGradle.contains('"com.acme:a"')
    }

    void "the packs of one generation are not the features of the next"() {
        given:
        Pack pack = pack('acme-audit', '')

        when:
        generate(['acme-audit'], [pack])
        generate(['acme-audit'], [])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The requested feature does not exist: acme-audit'
    }

    @Unroll
    void "a directory is not a pack when #problem"() {
        given:
        Path packDirectory = Files.createDirectories(directory.resolve('acme'))
        Files.createDirectories(packDirectory.resolve('templates'))
        Files.writeString(directory.resolve('outside.txt'), 'outside')
        if (descriptor != null) {
            Files.write(packDirectory.resolve('feature.yml'), descriptor)
        }
        prepare.call(packDirectory)

        when:
        new PackLoader().fromDirectory(packDirectory)

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]
        e.source == packDirectory.toString()

        where:
        problem                              | descriptor                                  | prepare                                                                         || message
        'it has no descriptor'               | null                                        | { Path it -> }                                                                  || 'There is no feature.yml in the directory, or it is not a file'
        'its descriptor is not UTF-8'        | [0xC3, 0x28] as byte[]                      | { Path it -> }                                                                  || 'feature.yml is not text in UTF-8'
        'its descriptor is not valid'        | (HEADER.replace('format: 1', 'format: 2') + 'name: acme\n').bytes | { Path it -> }                                                                  || 'format: must be the number 1, the one this version of Forge reads, but is the number 2'
        'a file it names is not there'       | descriptor('files:\n  - from: a.txt\n    to: a.txt\n') | { Path it -> }                                                       || "files[0].from: 'a.txt' is not a file under templates of the pack, or is a link or is reached through one"
        'a file it names is a directory'     | descriptor('files:\n  - from: a\n    to: a.txt\n')     | { Path it -> Files.createDirectories(it.resolve('templates/a')) }    || "files[0].from: 'a' is not a file under templates of the pack, or is a link or is reached through one"
        'a file it names is a template'      | descriptor('files:\n  - from: A.GROOVY.GSP\n    to: a.groovy\n') | { Path it -> Files.writeString(it.resolve('templates/A.GROOVY.GSP'), 'x') } || "files[0].from: 'A.GROOVY.GSP' is a template, and this version of Forge reads only files that are copied as they are"
    }

    @IgnoreIf({ os.windows })
    @Unroll
    void "a directory is not a pack when #what is a link"() {
        given:
        Path packDirectory = Files.createDirectories(directory.resolve('acme'))
        Path elsewhere = directory.resolve('elsewhere')
        Files.createDirectories(elsewhere.resolve('sub'))
        Files.writeString(elsewhere.resolve('a.txt'), 'outside')
        Files.writeString(elsewhere.resolve('sub/a.txt'), 'outside')
        Files.write(elsewhere.resolve('feature.yml'), descriptor('files:\n  - from: ' + from + '\n    to: a.txt\n'))
        prepare.call(packDirectory, elsewhere)

        when:
        new PackLoader().fromDirectory(packDirectory)

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]

        where:
        what                                 | from        | prepare || message
        'the descriptor'                     | 'a.txt'     | { Path pack, Path other -> Files.createSymbolicLink(pack.resolve('feature.yml'), other.resolve('feature.yml')); real(pack, 'a.txt') } || 'There is no feature.yml in the directory, or it is not a file'
        'the directory of its files'         | 'a.txt'     | { Path pack, Path other -> copy(pack, other); Files.createSymbolicLink(pack.resolve('templates'), other) } || "files[0].from: 'a.txt' is not a file under templates of the pack, or is a link or is reached through one"
        'a file that leaves the pack'        | 'a.txt'     | { Path pack, Path other -> copy(pack, other); Files.createDirectories(pack.resolve('templates')); Files.createSymbolicLink(pack.resolve('templates/a.txt'), other.resolve('a.txt')) } || "files[0].from: 'a.txt' is not a file under templates of the pack, or is a link or is reached through one"
        'a file that stays in the pack'      | 'a.txt'     | { Path pack, Path other -> copy(pack, other); real(pack, 'b.txt'); Files.createSymbolicLink(pack.resolve('templates/a.txt'), pack.resolve('templates/b.txt')) } || "files[0].from: 'a.txt' is not a file under templates of the pack, or is a link or is reached through one"
        'a directory on the way to a file'   | 'sub/a.txt' | { Path pack, Path other -> copy(pack, other); Files.createDirectories(pack.resolve('templates')); Files.createSymbolicLink(pack.resolve('templates/sub'), other.resolve('sub')) } || "files[0].from: 'sub/a.txt' is not a file under templates of the pack, or is a link or is reached through one"
        'a second name of a file elsewhere'  | 'a.txt'     | { Path pack, Path other -> copy(pack, other); Files.createDirectories(pack.resolve('templates')); Files.createLink(pack.resolve('templates/a.txt'), other.resolve('a.txt')) } || "files[0].from: 'a.txt' is not a file under templates of the pack, or is a link or is reached through one"
    }

    void "the files of a pack are what they were when it was read"() {
        given:
        Pack pack = pack('acme-audit', 'files:\n  - from: a.txt\n    to: audit.txt\n')

        when:
        Files.writeString(directory.resolve('acme-audit/templates/a.txt'), 'changed afterwards')

        then:
        generate(['acme-audit'], [pack])['audit.txt'] == 'a'
    }

    void "a file of a pack can be as large as the limit and no larger"() {
        given:
        Path packDirectory = Files.createDirectories(directory.resolve('acme/templates')).parent
        Files.write(packDirectory.resolve('feature.yml'), descriptor('files:\n  - from: a.bin\n    to: a.bin\n'))

        when:
        Files.write(packDirectory.resolve('templates/a.bin'), new byte[PackLoader.MAX_FILE_BYTES])

        then:
        new PackLoader().fromDirectory(packDirectory).content('a.bin').length == PackLoader.MAX_FILE_BYTES

        when:
        Files.write(packDirectory.resolve('templates/a.bin'), new byte[PackLoader.MAX_FILE_BYTES + 1])
        new PackLoader().fromDirectory(packDirectory)

        then:
        PackDescriptorException e = thrown()
        e.problems == ["files[0].from: 'a.bin' has more than the 1048576 bytes a file of a pack can have"]
    }

    @Unroll
    void "destinations that are one file once their placeholders have values are refused: #first and #second"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: a.txt\n    to: '" + first + "'\n  - from: a.txt\n    to: '" + second + "'\n")

        when:
        generate(['acme-audit'], [pack], ApplicationType.WEB, name)

        then:
        IllegalArgumentException e = thrown()
        e.message == message

        where:
        first                         | second                        | name   || message
        'src/{packageName}/a.txt'     | 'src/{packagePath}/a.txt'     | 'shop' || 'The features write the file src/shop/a.txt more than once'
        '{name}.txt'                  | '{propertyName}.txt'          | 'shop' || 'The features write the file shop.txt more than once'
        'a/{className}.txt'           | 'a/shop.txt'                  | 'shop' || 'The features write the file a/shop.txt more than once'
    }

    void "a pack cannot replace a file of the framework through a placeholder"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: a.txt\n    to: 'grails-app/init/{packageName}/Application.groovy'\n")

        when:
        generate(['acme-audit'], [pack], ApplicationType.WEB, 'shop')

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The features write the file grails-app/init/shop/Application.groovy more than once'
    }

    @Unroll
    void "a destination is checked again with the name of the application in it: #to for #name"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: a.txt\n    to: '" + to + "'\n")

        when:
        generate(['acme-audit'], [pack], ApplicationType.WEB, name)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The feature acme-audit would write the file ${to} to ${written}, which is not a path a feature pack can write for this application"

        where:
        to                   | name  || written
        '.{name}/hooks/x'    | 'git' || '.git/hooks/x'
        'src/{name}.txt'     | 'nul' || 'src/nul.txt'
    }

    void "the settings of the test environment are among the test resources, with those of the framework"() {
        given:
        Pack pack = pack('acme-audit', 'configuration:\n  environments:\n    test:\n      acme.audit.enabled: false\n    staging:\n      acme.audit.enabled: true\n')

        when:
        Map<String, String> output = generate(['acme-audit'], [pack])

        then:
        output['src/test/resources/application-test.yml'].contains('enabled: false')
        output['grails-app/conf/application-staging.yml'].contains('enabled: true')
        !output.containsKey('grails-app/conf/application-test.yml')
    }

    void "a pack cannot set a setting another feature has set to another value"() {
        given:
        Pack first = pack('acme-a', 'configuration:\n  acme.audit.enabled: true\n  acme.shared: 1\n')
        Pack second = pack('acme-b', 'configuration:\n  acme.shared: 1\n  acme:\n    audit:\n      enabled: false\n')

        when:
        generate(['acme-a', 'acme-b'], [first, second])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature acme-b sets acme.audit.enabled, which another feature has set to another value'
    }

    void "a pack cannot have the name of a feature or the group of features of another type of application"() {
        given:
        Set<String> restOnly = beanContext.getBean(org.grails.forge.feature.FeatureRegistry).all()
                .findAll { !it.supports(ApplicationType.PLUGIN) }*.name as Set<String>
        String name = restOnly.find { it ==~ /[a-z][a-z0-9]*(-[a-z0-9]+)*/ }

        expect:
        name != null

        when:
        generate([], [pack(name, '')], ApplicationType.PLUGIN)

        then:
        IllegalArgumentException e = thrown()
        e.message.endsWith("has the name of a feature of the framework, ${name}")
    }

    void "the features of a generation list the packs with those of the framework"() {
        given:
        PackFeatures features = new PackFeatures(
                beanContext.getBean(org.grails.forge.feature.FeatureRegistry).availableFeatures(ApplicationType.WEB),
                beanContext.getBean(org.grails.forge.feature.FeatureRegistry).all(),
                [pack('acme-audit', ''), pack('acme-rest', 'applicationTypes: [rest-api]\n')], ApplicationType.WEB)

        expect:
        features.findFeature('acme-audit').get() instanceof PackFeature
        features.findFeature('gsp').isPresent()
        !features.findFeature('acme-rest').isPresent()
        features.toList().containsAll(['acme-audit', 'gsp'])
        features.toList() == features.toList().toSorted()
        features.getAllFeatures().count() == features.getFeatures().count() + features.getAllFeatures().filter { !it.visible }.count()
    }

    private static void real(Path pack, String name) {
        Files.createDirectories(pack.resolve('templates'))
        Files.writeString(pack.resolve('templates').resolve(name), 'a')
    }

    private static void copy(Path pack, Path other) {
        Files.copy(other.resolve('feature.yml'), pack.resolve('feature.yml'))
    }

    private static byte[] descriptor(String body) {
        return (HEADER + 'name: acme\n' + body).bytes
    }

    private Pack pack(String name, String body, Map<String, String> files = [:], String directoryName = name) {
        Path packDirectory = Files.createDirectories(directory.resolve(directoryName))
        Files.writeString(packDirectory.resolve('feature.yml'), HEADER + 'name: ' + name + '\n' + body.stripIndent())
        Files.createDirectories(packDirectory.resolve('templates'))
        Files.writeString(packDirectory.resolve('templates/a.txt'), 'a')
        files.each { String path, String text ->
            Path file = packDirectory.resolve('templates').resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, text)
        }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private Map<String, String> generate(List<String> features, List<Pack> packs, ApplicationType type = ApplicationType.WEB, String name = 'example.grails.foo') {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse(name)
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(type, project, new Options(), OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, packs)
        generator.generate(type, project, output, context)
        return output.getProject()
    }
}
