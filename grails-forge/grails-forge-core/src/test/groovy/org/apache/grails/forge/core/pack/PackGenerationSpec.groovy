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
        (new org.yaml.snakeyaml.Yaml().load(output['grails-app/conf/application.yml']) as Map).environments.production.acme.audit.flush == '30s'
        !output.containsKey('grails-app/conf/application-production.yml')

        and: 'the file, as it is, where the descriptor puts it'
        output['grails-app/conf/example/grails/audit.txt'] == 'kept as it is: ${notATemplate}\n'

        and: 'the readme and the link to its documentation'
        output['README.md'].contains('Audit events are written to the `audit_event` table.')
        output['README.md'].contains('https://acme.example/docs')

        and: 'its name among the features of the application'
        output['grails-forge-cli.yml'].contains('acme-audit')
    }

    void "a readme of one line is a paragraph of its own"() {
        when:
        String readme = generate(['acme-audit'], [pack('acme-audit', 'readme: One line about Acme.\n')])['README.md']

        then:
        readme.contains('One line about Acme.\n\n')
        !readme.contains('Acme.#')
    }

    void "a file of a pack whose name ends with gsp is a template, rendered with the application it is generated for"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: Audit.groovy.gsp\n    to: 'src/main/groovy/{packagePath}/{className}Audit.groovy'\n" +
                "  - from: model.txt.GSP\n    to: model.txt\n  - from: plain.txt\n    to: plain.txt\n", [
                'Audit.groovy.gsp': 'package ${project.packageName}\n\nclass ${project.className}Audit {\n' +
                        '<% if (features.contains("gsp")) { %>\n    boolean views = true\n<% } %>\n' +
                        '<% if (features.contains("gorm-mongodb")) { %>\n    boolean mongo = true\n<% } %>\n' +
                        '    String html = "<b>&amp;</b>"\n    String kept = "\\${notReplaced}"\n}\n',
                'model.txt.GSP'   : '${project.name}|${project.packageName}|${project.packagePath}|${project.className}|${project.naturalName}|' +
                        '${project.propertyName}|${applicationType}|${options.jdk}|${options.data}|${options.servlet}|${options.reloading}|' +
                        '${grailsVersion ==~ /\\d+\\.\\d+\\.\\d+.*/}|${pack.name}|${features.contains("acme-audit")}|' +
                        '${features == features.toSorted()}',
                'plain.txt'       : 'not a template: ${project.name} <% %>\n',
        ])

        when:
        Map<String, String> output = generate(['acme-audit'], [pack], ApplicationType.WEB, 'com.example.shop')

        then: 'lines that hold only logic write nothing, and what is written is not encoded'
        output['src/main/groovy/com/example/ShopAudit.groovy'] == 'package com.example\n\nclass ShopAudit {\n    boolean views = true\n' +
                '    String html = "<b>&amp;</b>"\n    String kept = "${notReplaced}"\n}\n'

        and: 'the names a template can refer to'
        output['model.txt'] == 'shop|com.example|com/example|Shop|Shop|shop|web|' +
                "${org.grails.forge.options.JdkVersion.DEFAULT_OPTION.majorVersion()}|${org.grails.forge.options.GormImpl.DEFAULT_OPTION.name().toLowerCase()}|" +
                "${org.grails.forge.options.ServletImpl.DEFAULT_OPTION.name().toLowerCase()}|${org.grails.forge.options.DevelopmentReloading.DEFAULT_OPTION.name().toLowerCase()}|" +
                'true|acme-audit|true|true'

        and: 'a file of another name is written as it is'
        output['plain.txt'] == 'not a template: ${project.name} <% %>\n'
    }

    void "a template sees the features that were chosen for it, and those another feature requires"() {
        given:
        Pack pack = pack('acme-audit', "requires: [database-migration]\nfiles:\n  - from: f.txt.gsp\n    to: f.txt\n",
                ['f.txt.gsp': '${features.contains("database-migration")} ${features.contains("postgres")} ${features.contains("h2")} ${applicationType}'])

        expect:
        generate(['acme-audit', 'postgres'], [pack], ApplicationType.REST_API)['f.txt'] == 'true true false rest-api'
    }

    @Unroll
    void "a template that #problem is refused, and nothing of the application is written"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: bad.txt.gsp\n    to: bad.txt\n", ['bad.txt.gsp': source])
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                ['acme-audit'], ConsoleOutput.NOOP, [pack])
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB, project, output, context)

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith(message)
        e.message.length() < 700
        !(e.message =~ /[\p{Cc}]/)
        output.getProject().isEmpty()

        where:
        problem                                 | source                                               || message
        'is not a page that compiles'           | '<% if (true) { %>unclosed'                          || 'The template bad.txt.gsp of the feature acme-audit cannot be rendered: '
        'refers to what the model has not'      | '${missing.property}'                                || 'The template bad.txt.gsp of the feature acme-audit cannot be rendered: '
        'changes the features it is given'      | '<% features << "another" %>'                        || 'The template bad.txt.gsp of the feature acme-audit cannot be rendered: '
        'changes the project it is given'       | '<% project.name = "other" %>'                       || 'The template bad.txt.gsp of the feature acme-audit cannot be rendered: '
        'throws'                                | '<% throw new IllegalStateException("no\\nway") %>'  || 'The template bad.txt.gsp of the feature acme-audit cannot be rendered: '
        'writes without end'                    | '<% while (true) { %>' + ('x' * 1000) + '<% } %>'    || 'The template bad.txt.gsp of the feature acme-audit writes more than the 1048576 bytes a file of a feature pack can have'
    }

    void "a template has to be text"() {
        given:
        Path packDirectory = Files.createDirectories(directory.resolve('acme/templates')).parent
        Files.write(packDirectory.resolve('feature.yml'), descriptor('files:\n  - from: a.txt.gsp\n    to: a.txt\n'))
        Files.write(packDirectory.resolve('templates/a.txt.gsp'), [0xC3, 0x28] as byte[])

        when:
        new PackLoader().fromDirectory(packDirectory)

        then:
        PackDescriptorException e = thrown()
        e.problems == ["files[0].from: 'a.txt.gsp' is a template and is not text in UTF-8"]
    }

    void "templates of two packs with one name for a page are each their own"() {
        given:
        Pack first = pack('acme-a', "files:\n  - from: page.txt.gsp\n    to: a.txt\n", ['page.txt.gsp': 'first ${pack.name}'])
        Pack second = pack('acme-b', "files:\n  - from: page.txt.gsp\n    to: b.txt\n", ['page.txt.gsp': 'second ${pack.name}'])

        when:
        Map<String, String> output = generate(['acme-a', 'acme-b'], [first, second])

        then:
        output['a.txt'] == 'first acme-a'
        output['b.txt'] == 'second acme-b'
    }

    @Unroll
    void "a template that names #name, which template API 1 has not, is refused"() {
        when:
        generate(['acme-audit'], [pack('acme-audit', "files:\n  - from: bad.txt.gsp\n    to: bad.txt\n", ['bad.txt.gsp': source])])

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('The template bad.txt.gsp of the feature acme-audit cannot be rendered: ')
        e.message.contains("There is no '${name}' for a template: template API 1 has ")
        e.message.contains(has)

        where:
        name              | source                                    || has
        'projekt'         | 'line one\n${projekt.name}'               || '[applicationType, features, grailsVersion, options, pack, project]'
        'flags'           | '<% if (flags) { %>yes<% } %>'            || '[applicationType, features, grailsVersion, options, pack, project]'
        'pack.version'    | '${pack.version}'                         || '[pack.name]'
        'options.gorm'    | '<% if (options.gorm == "x") { %>y<% } %>' || '[options.data, options.jdk, options.reloading, options.servlet]'
        'project.nope'    | '${project.nope}'                         || 'project.packageName'
    }

    void "the options of the model are written as the options of the create commands are"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: o.txt.gsp\n    to: o.txt\n",
                ['o.txt.gsp': '${options.data} ${options.servlet} ${options.reloading} ${options.jdk} ${applicationType}'])
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        Options options = new Options(org.grails.forge.options.DevelopmentReloading.NONE, org.grails.forge.options.GormImpl.MONGODB,
                org.grails.forge.options.ServletImpl.NONE, org.grails.forge.options.JdkVersion.DEFAULT_OPTION, OperatingSystem.LINUX)
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB_PLUGIN, project, options, OperatingSystem.LINUX,
                ['acme-audit'], ConsoleOutput.NOOP, [pack])
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB_PLUGIN, project, output, context)

        then:
        output.getProject()['o.txt'] == "mongodb none none ${org.grails.forge.options.JdkVersion.DEFAULT_OPTION.majorVersion()} web-plugin"
    }

    void "a template is read as UTF-8 and the lines of a message are those of its source"() {
        given:
        Pack text = pack('acme-audit', "files:\n  - from: t.txt.gsp\n    to: t.txt\n", ['t.txt.gsp': '\ufeff\u00e9\u20ac ${"\u00e9\u20ac".length()}'])
        Pack lines = pack('acme-audit', "files:\n  - from: t.txt.gsp\n    to: t.txt\n", ['t.txt.gsp': 'one\ntwo\n${features.nope()}\n'])

        expect: 'a byte order mark is not part of the text'
        generate(['acme-audit'], [text])['t.txt'] == '\u00e9\u20ac 2'

        when:
        generate(['acme-audit'], [lines])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('on line [3]')
        e.cause != null
    }

    @Unroll
    void "a template that #problem fails as a template, with a message that is no markup"() {
        when:
        generate(['acme-audit'], [pack('acme-audit', "files:\n  - from: bad.txt.gsp\n    to: bad.txt\n", ['bad.txt.gsp': source])])

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith('The template bad.txt.gsp of the feature acme-audit cannot be rendered: ')
        e.message.contains(reason)
        !e.message.contains('@|')
        !(e.message =~ /[\p{Cc}\p{Cf}]/)
        e.message.length() < 700

        where:
        problem                              | source                                                                      || reason
        'asserts what is not so'             | '<% assert features.contains("nope") : "wanted \\u001b[31m @|red nope|@" %>' || 'wanted'
        'calls itself without end'           | '<% def f; f = { f() }; f() %>'                                             || 'it calls itself without end'
        'throws an error of linking'         | '<% throw new LinkageError("link \\u202e @|red e|@") %>'                    || 'link'
        'throws what has no message to give' | '<% throw new RuntimeException() { String getMessage() { throw new IllegalStateException("@|red x|@") } } %>' || 'cannot be rendered'
    }

    void "a pack that cannot write one of its files runs none of its templates"() {
        given:
        Path marker = directory.resolve('ran.marker')
        Pack pack = pack('acme-audit', "files:\n  - from: first.txt.gsp\n    to: first.txt\n  - from: plain.txt\n    to: '{name}/config'\n", [
                'first.txt.gsp': "<% new File('${marker.toString().replace('\\', '/')}').text = 'ran' %>x".toString(),
                'plain.txt'    : 'x',
        ])

        when:
        generate(['acme-audit'], [pack], ApplicationType.WEB, '.git')

        then:
        thrown(IllegalArgumentException)
        !Files.exists(marker)
    }

    void "a template runs only when its pack is of the application that is generated"() {
        given:
        Path marker = directory.resolve('ran.marker')
        Pack pack = pack('acme-audit', "files:\n  - from: first.txt.gsp\n    to: first.txt\n",
                ['first.txt.gsp': "<% new File('${marker.toString().replace('\\', '/')}').text = 'ran' %>x".toString()])
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)

        when: 'the pack is there and not selected'
        generate([], [pack])
        generator.createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), new Options(), OperatingSystem.LINUX,
                ['acme-audit'], ConsoleOutput.NOOP, [pack])

        then:
        !Files.exists(marker)

        when:
        generate(['acme-audit'], [pack])

        then:
        Files.exists(marker)
    }

    void "the files of a pack are bounded together after its templates are rendered"() {
        given:
        String files = (1..17).collect { "  - from: big.txt.gsp\n    to: big${it}.txt\n" }.join('')
        Pack pack = pack('acme-audit', "files:\n${files}", ['big.txt.gsp': '${"x" * 1048576}'])

        when:
        generate(['acme-audit'], [pack])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature acme-audit writes more than the 16777216 bytes the files of a feature pack can have together'
    }

    void "what was prepared for one application is not generated as another"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: A.groovy.gsp\n    to: 'src/main/groovy/{packagePath}/{className}Audit.groovy'\n",
                ['A.groovy.gsp': 'package ${project.packageName}\nclass ${project.className}Audit {}'])
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, NameUtils.parse('com.first.alpha'), new Options(),
                OperatingSystem.LINUX, ['acme-audit'], ConsoleOutput.NOOP, [pack])
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB, NameUtils.parse('org.second.beta'), output, context)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The application beta cannot be generated from what was prepared for the application alpha'
        output.getProject().isEmpty()
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

    void "the settings of an environment are with those the framework has for it"() {
        given:
        Pack pack = pack('acme-audit', 'configuration:\n  environments:\n    test:\n      acme.audit.enabled: false\n    staging:\n      acme.audit.enabled: true\n')

        when:
        Map<String, String> output = generate(['acme-audit'], [pack])
        Map<String, Object> configuration = new org.yaml.snakeyaml.Yaml().load(output['grails-app/conf/application.yml']) as Map<String, Object>

        then:
        configuration.environments.test.acme.audit.enabled == false
        configuration.environments.staging.acme.audit.enabled == true
        configuration.environments.test.dataSource != null
        !output.keySet().any { it.contains('application-test') || it.contains('application-staging') }
    }

    @Unroll
    void "a pack named #name cannot change what another feature gives the application: #what"() {
        when:
        generate([name], [pack(name, body)])

        then:
        IllegalArgumentException e = thrown()
        e.message == message.replace('NAME', name)

        where:
        [name, what, body, message] << [['acme', 'zzz'], [
                ['a setting', 'configuration:\n  dataSource.pooled: false\n',
                 'The feature NAME sets dataSource.pooled, which another feature sets to another value'],
                ['a setting of one environment', 'configuration:\n  environments:\n    production:\n      dataSource.dbCreate: create-drop\n',
                 'The feature NAME sets environments.production.dataSource.dbCreate, which another feature sets to another value'],
                ['a build property set last', "build:\n  properties:\n    org.gradle.jvmargs: '-Xmx8g'\n",
                 "The feature NAME sets the build property org.gradle.jvmargs to '-Xmx8g', and another feature sets it to '-Dfile.encoding=UTF-8 -Xmx1024M'"],
        ]].combinations().collect { String packName, List row -> [packName, row[0], row[1], row[2]] }
    }

    @Unroll
    void "a pack named #name cannot turn the settings of #setting into one value"() {
        when:
        generate([name], [pack(name, 'configuration:\n  ' + setting + ': x\n')])

        then:
        IllegalArgumentException e = thrown()
        e.message == "The feature ${name} sets ${setting}, and another feature sets a setting of the same name " +
                'but one of the two is a value where the other is a group of settings'

        where:
        [name, setting] << [['acme', 'zzz'], ['dataSource', 'grails.views', 'dataSource.pooled.size', 'environments.development']].combinations()
    }

    @Unroll
    void "a pack named #name cannot set the build property grailsVersion"() {
        when:
        generate([name], [pack(name, "build:\n  properties:\n    grailsVersion: '0.0.1'\n")])

        then:
        IllegalArgumentException e = thrown()
        e.message.startsWith("The feature ${name} sets the build property grailsVersion to '0.0.1', and another feature sets it to '")

        where:
        name << ['acme', 'zzz']
    }

    @Unroll
    void "a pack named #name cannot declare a dependency of the framework in another version"() {
        when:
        generate([name], [pack(name, "dependencies:\n  - scope: testImplementation\n    coordinates: org.spockframework:spock-core\n    version: '2.0'\n")])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('org.spockframework:spock-core')
        e.message.contains('without a version')
        e.message.contains('version 2.0')

        where:
        name << ['acme', 'zzz']
    }

    @Unroll
    void "a pack named #name cannot apply a build plugin of the framework in another version"() {
        when:
        generate([name], [pack(name, "buildPlugins:\n  - id: eclipse\n    version: '1.0'\n")])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('build plugin eclipse')
        e.message.contains('without a version')
        e.message.contains('in version 1.0')

        where:
        name << ['acme', 'zzz']
    }

    void "a pack can say again what the framework says"() {
        given:
        Pack pack = pack('acme', 'dependencies:\n  - scope: testImplementation\n    coordinates: org.spockframework:spock-core\n' +
                'buildPlugins:\n  - id: eclipse\nbuild:\n  properties:\n    org.gradle.caching: true\n' +
                'configuration:\n  dataSource.pooled: true\n')

        when:
        Map<String, String> with = generate(['acme'], [pack])
        Map<String, String> without = generate([], [])

        then:
        with['build.gradle'] == without['build.gradle']
        with['gradle.properties'].readLines().toSorted() == without['gradle.properties'].readLines().toSorted()
        new org.yaml.snakeyaml.Yaml().load(with['grails-app/conf/application.yml']) == new org.yaml.snakeyaml.Yaml().load(without['grails-app/conf/application.yml'])
    }

    void "a pack can require one of an exclusive group, which is then not given its default"() {
        given:
        Pack pack = pack('acme-audit', 'requires: [postgres]\n')

        when:
        Map<String, String> output = generate(['acme-audit'], [pack])

        then:
        output['build.gradle'].contains('org.postgresql:postgresql')
        !output['build.gradle'].contains('com.h2database')
        output['grails-forge-cli.yml'].contains('postgres')
    }

    void "what a pack requires through another pack is selected before the defaults too"() {
        given:
        Pack audit = pack('acme-audit', 'requires: [acme-base]\n')
        Pack base = pack('acme-base', 'requires: [mysql]\n')

        when:
        String buildGradle = generate(['acme-audit'], [audit, base])['build.gradle']

        then:
        buildGradle.contains('mysql')
        !buildGradle.contains('com.h2database')
    }

    void "two packs cannot set one setting to two values"() {
        given:
        Pack first = pack('acme-a', 'configuration:\n  acme.audit.enabled: true\n  acme.shared: 1\n')
        Pack second = pack('acme-b', 'configuration:\n  acme.shared: 1\n  acme:\n    audit:\n      enabled: false\n')

        when:
        generate(['acme-a', 'acme-b'], [first, second])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature acme-b sets acme.audit.enabled, which another feature sets to another value'
    }

    @Unroll
    void "no feature can write the file #path"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                [], ConsoleOutput.NOOP)
        context.addTemplate('outside', new PackContent(path, 'x'.bytes))
        MapOutputHandler output = new MapOutputHandler()

        when:
        generator.generate(ApplicationType.WEB, project, output, context)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The features write the file ${path}, which is not a path inside the application"
        output.getProject().isEmpty()

        where:
        path << ['../outside.txt', 'a/../../outside.txt', '/etc/outside.txt', 'a\\..\\outside.txt', 'C:/outside.txt']
    }

    void "an archive takes no file that is not inside it"() {
        given:
        org.grails.forge.io.ZipOutputHandler archive = new org.grails.forge.io.ZipOutputHandler('shop', new ByteArrayOutputStream())

        when:
        archive.write('../outside.txt', new PackContent('../outside.txt', 'x'.bytes))

        then:
        IOException e = thrown()
        e.message == 'The file ../outside.txt is not a path inside the archive'
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

    @Unroll
    void "a pack cannot write #to, which the application has as #what"() {
        given:
        Pack pack = pack('acme-audit', "files:\n  - from: a.txt\n    to: '" + to + "'\n")

        when:
        generate(['acme-audit'], [pack])

        then:
        IllegalArgumentException e = thrown()
        e.message.endsWith(message)

        where:
        to                 | what                           || message
        'build.gradle/x'   | 'a file'                       || 'both as a file and as a directory of other files'
        'grails-app/conf'  | 'a directory'                  || 'both as a file and as a directory of other files'
        'BUILD.GRADLE'     | 'a file in other letters'      || 'more than once'
    }

    void "two packs cannot have one name even when only one of them is for this type of application"() {
        given:
        Pack first = pack('acme-audit', '', [:], 'one')
        Pack second = pack('acme-audit', 'applicationTypes: [rest-api]\n', [:], 'two')

        when:
        generate([], [first, second])

        then:
        IllegalArgumentException e = thrown()
        e.message.endsWith('both have the name acme-audit')
    }

    void "a pack cannot name an exclusive group the framework has for another type of application"() {
        given:
        org.grails.forge.feature.FeatureRegistry registry = beanContext.getBean(org.grails.forge.feature.FeatureRegistry)
        Set<String> groups = registry.all().findAll { it instanceof org.grails.forge.feature.OneOfFeature }
                .collect { ((org.grails.forge.feature.OneOfFeature) it).group } as Set<String>
        Set<String> ofPlugins = registry.availableFeatures(ApplicationType.PLUGIN).getAllFeatures()
                .filter { it instanceof org.grails.forge.feature.OneOfFeature }
                .map { ((org.grails.forge.feature.OneOfFeature) it).group }.toList() as Set<String>
        String group = (groups - ofPlugins).first()

        when:
        generate([], [pack('acme-audit', 'oneOf: ' + group + '\n')], ApplicationType.PLUGIN)

        then:
        IllegalArgumentException e = thrown()
        e.message.endsWith("names the exclusive group ${group} of the framework, which a pack cannot join yet")
    }

    @Unroll
    void "a pack named #name cannot apply a plugin of the framework in another way"() {
        when:
        generate([name], [pack(name, 'buildPlugins:\n  - id: org.apache.grails.gradle.grails-web\n')])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The build plugin org.apache.grails.gradle.grails-web is applied twice, once in the plugins block and once with apply plugin'

        where:
        name << ['acme', 'zzz']
    }

    @Unroll
    void "a pack named #name cannot give a dependency of the framework a version in another scope"() {
        when:
        generate([name], [pack(name, "dependencies:\n  - scope: runtimeOnly\n    coordinates: org.spockframework:spock-core\n    version: '2.0'\n")])

        then:
        IllegalArgumentException e = thrown()
        e.message == "The feature ${name} declares org.spockframework:spock-core in version 2.0, and it is also declared without a version"

        where:
        name << ['acme', 'zzz']
    }

    @Unroll
    void "a pack named #name cannot set what the framework sets in the file of one environment"() {
        when:
        generate([name], [pack(name, 'configuration:\n  environments:\n    development:\n      spring.devtools.livereload.enabled: false\n')])

        then:
        IllegalArgumentException e = thrown()
        e.message == "The feature ${name} sets environments.development.spring.devtools.livereload.enabled, which another feature sets to another value"

        where:
        name << ['acme', 'zzz']
    }

    @Unroll
    void "a mapping without settings in the configuration of a pack named #name sets nothing"() {
        expect:
        new org.yaml.snakeyaml.Yaml().load(generate([name], [pack(name, 'configuration:\n  dataSource: {}\n  acme: {}\n')])['grails-app/conf/application.yml']) ==
                new org.yaml.snakeyaml.Yaml().load(generate([], [])['grails-app/conf/application.yml'])

        where:
        name << ['acme', 'zzz']
    }

    void "a context whose features could not be applied generates nothing afterwards either"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                ['acme'], ConsoleOutput.NOOP, [pack('acme', 'configuration:\n  dataSource.pooled: false\n')])
        MapOutputHandler output = new MapOutputHandler()

        when:
        context.applyFeatures()

        then:
        thrown(IllegalArgumentException)

        when:
        generator.generate(ApplicationType.WEB, project, output, context)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'The feature acme sets dataSource.pooled, which another feature sets to another value'
        output.getProject().isEmpty()
    }

    @Unroll
    void "templates for #first and #second are for one file"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                [], ConsoleOutput.NOOP)
        context.addTemplate('first', new org.grails.forge.template.StringTemplate(first, 'one'))
        context.addTemplate('second', new org.grails.forge.template.StringTemplate(second, 'two'))

        when:
        generator.generate(ApplicationType.WEB, project, new MapOutputHandler(), context)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The features write the file ${second} more than once"

        where:
        first      | second
        'a/b.txt'  | 'a//b.txt'
        'a/b.txt'  | 'a/./b.txt'
        'a/b.txt'  | 'A/B.TXT'
    }

    @Unroll
    void "a context refuses #what declared twice"() {
        given:
        GeneratorContext context = beanContext.getBean(ProjectGenerator).createGeneratorContext(ApplicationType.WEB,
                NameUtils.parse('example.grails.foo'), new Options(), OperatingSystem.LINUX, [], ConsoleOutput.NOOP)

        when:
        declare.call(context)

        then:
        IllegalArgumentException e = thrown()
        e.message == message

        where:
        what                                  | declare || message
        'a dependency in two versions'        | { GeneratorContext it -> it.addDependency(acme().version('1')); it.addDependency(acme().version('2')) } || 'The dependency com.acme:acme is declared twice, as a dependency of version 1 and as a dependency of version 2'
        'a dependency and a platform'         | { GeneratorContext it -> it.addDependency(acme()); it.addDependency(acme().pom(true)) } || 'The dependency com.acme:acme is declared twice, as a dependency without a version and as a platform without a version'
        'a platform and an enforced platform' | { GeneratorContext it -> it.addDependency(acme().pom(true)); it.addDependency(acme().pom(true).enforced(true)) } || 'The dependency com.acme:acme is declared twice, as a platform without a version and as an enforced platform without a version'
        'a build dependency in two versions'  | { GeneratorContext it -> it.addBuildscriptDependency(acme().version('1')); it.addBuildscriptDependency(acme().version('2')) } || 'The dependency com.acme:acme is declared twice, as a dependency of version 1 and as a dependency of version 2'
        'a build plugin in two versions'      | { GeneratorContext it -> it.addBuildPlugin(org.grails.forge.build.gradle.GradlePlugin.builder().id('com.acme').version('1').build()); it.addBuildPlugin(org.grails.forge.build.gradle.GradlePlugin.builder().id('com.acme').build()) } || 'The build plugin com.acme is applied twice, in version 1 and without a version'
    }

    private static org.grails.forge.build.dependencies.Dependency.Builder acme() {
        return org.grails.forge.build.dependencies.Dependency.builder().groupId('com.acme').artifactId('acme').implementation()
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
