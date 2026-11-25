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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import org.yaml.snakeyaml.Yaml
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The pack of a plugin, as a directory and as the jar of the plugin, says what a pack of Grails says of
 * what its feature gives an application: contributions that depend on the application, and every
 * contribution to the build. What it still cannot say is how its feature is selected without being
 * named, and it only adds.
 */
class PackParitySpec extends ApplicationContextSpec {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    static final String CONDITIONAL = '''\
        dependencies:
          - scope: implementation
            coordinates: com.acme:acme-audit-quartz
            version: 2.3.0
            when: { feature: grails-quartz }
          - scope: implementation
            coordinates: com.acme:acme-audit-mongodb
            version: 2.3.0
            when: { option: { data: mongodb } }
          - scope: implementation
            coordinates: com.acme:acme-audit-sql
            version: 2.3.0
            when: { not: { option: { data: mongodb } } }
          - scope: implementation
            coordinates: com.acme:acme-audit-web
            version: 2.3.0
            when: { applicationType: [web, web-plugin] }
          - scope: implementation
            coordinates: com.acme:acme-audit-jdk25
            version: 2.3.0
            when: { jdkAtLeast: 25 }
          - scope: runtimeOnly
            coordinates: com.acme:acme-audit-jetty
            version: 2.3.0
            when: { allOf: [{ option: { servlet: jetty } }, { group: database-driver }] }
        platforms:
          - coordinates: com.acme:acme-quartz-bom
            version: 2.3.0
            when: { feature: grails-quartz }
        buildPlugins:
          - id: com.acme.audit.quartz
            version: 2.3.0
            when: { feature: grails-quartz }
        build:
          extensions:
            acmeAudit:
              - text: retention = 90
              - text: jobs = true
                when: { feature: grails-quartz }
        configuration:
          - values:
              acme.audit.enabled: true
          - values:
              acme.audit.jobs: true
            when: { feature: grails-quartz }
          - values:
              acme.audit.store: collection
            when: { option: { data: mongodb } }
        files:
          - from: Audit.groovy.gsp
            to: 'src/main/groovy/{packagePath}/Audit.groovy'
          - from: AuditJob.groovy.gsp
            to: 'grails-app/jobs/{packagePath}/AuditJob.groovy'
            when: { feature: grails-quartz }
        '''.stripIndent()

    static final Map<String, String> TEMPLATES = ['Audit.groovy.gsp'   : 'package ${project.packageName}\n\nclass Audit {\n}\n',
                                                  'AuditJob.groovy.gsp': 'package ${project.packageName}\n\nclass AuditJob {\n}\n']

    @TempDir
    Path directory

    @Unroll
    void "the pack of a #from gives what holds of the application, and nothing else: #what"() {
        given:
        Pack pack = packOf(from, CONDITIONAL, TEMPLATES)

        when:
        Map<String, String> output = generate(['acme-audit'] + features, [pack], type, options)
        String build = output['build.gradle']
        List<String> artifacts = ['quartz', 'mongodb', 'sql', 'web', 'jdk25', 'jetty'].findAll { build.contains("com.acme:acme-audit-${it}:2.3.0") }
        Map<String, Object> settings = flat(new Yaml().load(output['grails-app/conf/application.yml']) as Map, '')
                .findAll { String setting, Object value -> setting.startsWith('acme.audit.') }
                .collectEntries { String setting, Object value -> [setting.substring('acme.audit.'.length()), value] }

        then:
        artifacts == dependencies
        build.contains('implementation platform("com.acme:acme-quartz-bom:2.3.0")') == quartz
        build.contains('id "com.acme.audit.quartz" version "2.3.0"') == quartz
        build.contains(quartz ? 'acmeAudit {\n    retention = 90\n    jobs = true\n}\n' : 'acmeAudit {\n    retention = 90\n}\n')
        settings == expectedSettings
        output.containsKey('src/main/groovy/example/grails/Audit.groovy')
        output.containsKey('grails-app/jobs/example/grails/AuditJob.groovy') == quartz

        where:
        from        | what                 | features          | type                     | options                    || dependencies              | quartz | expectedSettings
        'directory' | 'a web application'  | []                | ApplicationType.WEB      | new Options()              || ['sql', 'web']            | false  | [enabled: true]
        'jar'       | 'a web application'  | []                | ApplicationType.WEB      | new Options()              || ['sql', 'web']            | false  | [enabled: true]
        'directory' | 'another feature'    | ['grails-quartz'] | ApplicationType.WEB      | new Options()              || ['quartz', 'sql', 'web']  | true   | [enabled: true, jobs: true]
        'jar'       | 'another feature'    | ['grails-quartz'] | ApplicationType.WEB      | new Options()              || ['quartz', 'sql', 'web']  | true   | [enabled: true, jobs: true]
        'directory' | 'another type'       | []                | ApplicationType.REST_API | new Options()              || ['sql']                   | false  | [enabled: true]
        'jar'       | 'the data option'    | []                | ApplicationType.WEB      | options(GormImpl.MONGODB)  || ['mongodb', 'web']        | false  | [enabled: true, store: 'collection']
        'directory' | 'the servlet option' | []                | ApplicationType.WEB      | options(null, ServletImpl.JETTY) || ['sql', 'web', 'jetty'] | false  | [enabled: true]
        'jar'       | 'the version of Java' | []               | ApplicationType.WEB      | options(null, null, JdkVersion.valueOf(25)) || ['sql', 'web', 'jdk25'] | false | [enabled: true]
    }

    @Unroll
    void "the pack of a #from contributes to the build what a pack of Grails does"() {
        given:
        Pack pack = packOf(from, '''\
            requiresJdk: 21
            dependencies:
              - scope: buildscript
                coordinates: com.acme:acme-audit-gradle
                version: 2.3.0
              - scope: testAndDevelopmentOnly
                coordinates: com.acme:acme-audit-console
                version: 2.3.0
              - scope: integrationTestImplementationTestFixtures
                coordinates: com.acme:acme-audit-fixtures
                version: 2.3.0
            buildPlugins:
              - id: com.acme.audit
                apply: true
            build:
              properties:
                acme.audit.verbose: false
              propertyComments:
                - The audit trail of Acme
              extensions:
                tasks.withType(Test).configureEach: systemProperty 'acme.audit', 'test'
              blocks:
                afterPlugins: |
                  def auditStore = 'table'
                end: |
                  tasks.register('auditReport') {
                      doLast { println auditStore }
                  }
            configuration:
              - environmentFile: production
                values:
                  acme.audit.flush: 30s
            files:
              - from: audit.sh
                to: bin/audit.sh
                executable: true
              - from: show.gsp
                to: grails-app/views/audit/show.gsp
                template: false
            readmeLinks:
              - label: Acme Audit
                href: https://acme.example/audit
            ''', ['audit.sh': '#!/bin/sh\necho audit\n', 'show.gsp': '<h1>${audit.name}</h1>\n'])
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler handler = new MapOutputHandler()

        when:
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                ['acme-audit'], ConsoleOutput.NOOP, [pack])
        generator.generate(ApplicationType.WEB, project, handler, context)
        Map<String, String> output = handler.getProject()
        String build = output['build.gradle']

        then: 'a dependency of the build itself, and the plugin it brings, applied after the plugins block'
        build.contains('classpath "com.acme:acme-audit-gradle:2.3.0"')
        build.contains('apply plugin: "com.acme.audit"')
        !build.contains('id "com.acme.audit"')

        and: 'the scopes a pack of Grails declares in'
        build.contains('testAndDevelopmentOnly "com.acme:acme-audit-console:2.3.0"')
        build.contains('integrationTestImplementation testFixtures("com.acme:acme-audit-fixtures:2.3.0")')

        and: 'text at the places of the build file, and a block whose name has a call'
        build.indexOf("def auditStore = 'table'") > build.indexOf('plugins {')
        build.indexOf("def auditStore = 'table'") < build.lastIndexOf('dependencies {')
        build.stripTrailing().endsWith("tasks.register('auditReport') {\n    doLast { println auditStore }\n}")
        build.count('tasks.withType(Test).configureEach {') == 1
        build.contains("    systemProperty 'acme.audit', 'test'\n")

        and: 'a comment of the properties, the configuration file of one environment, and a link of the readme'
        output['gradle.properties'].contains('#The audit trail of Acme\nacme.audit.verbose=false\n')
        output['gradle.properties'].contains('acme.audit.verbose=false\n')
        flat(new Yaml().load(output['grails-app/conf/application-production.yml']) as Map, '') == ['acme.audit.flush': '30s']
        !output['grails-app/conf/application.yml'].contains('flush')
        output['README.md'].contains('[Acme Audit](https://acme.example/audit)')

        and: 'a file that is run, and a view that is written as it is although its name ends with .gsp'
        output['bin/audit.sh'] == '#!/bin/sh\necho audit\n'
        context.getTemplates().values().find { it.getPath() == 'bin/audit.sh' }.isExecutable()
        output['grails-app/views/audit/show.gsp'] == '<h1>${audit.name}</h1>\n'

        where:
        from << ['directory', 'jar']
    }

    void "a pack that requires a later version of Java is refused for an application of an earlier one"() {
        given:
        Pack pack = packOf('directory', 'requiresJdk: 25\n')

        when:
        generate(['acme-audit'], [pack], ApplicationType.WEB, options(null, null, JdkVersion.valueOf(21)))

        then:
        IllegalArgumentException e = thrown()
        e.message == 'acme-audit requires JDK 25 or later (selected: JDK 21).'

        expect:
        generate(['acme-audit'], [pack], ApplicationType.WEB, options(null, null, JdkVersion.valueOf(25)))['build.gradle']
    }

    @Unroll
    void "a pack only adds, with a condition too: #what"() {
        given:
        Pack pack = packOf('directory', body)
        Pack other = packOf('directory', otherBody, [:], 'acme-other')

        when: 'the condition does not hold'
        Map<String, String> output = generate(['acme-audit', 'acme-other'], [pack, other], ApplicationType.WEB, new Options())

        then: 'what the pack would give is not there to differ'
        output['build.gradle']

        when: 'it holds'
        generate(['acme-audit', 'acme-other', 'grails-quartz'], [pack, other], ApplicationType.WEB, new Options())

        then: 'the feature that is applied second, by its name, is the one that finds the other'
        IllegalArgumentException e = thrown()
        e.message == message

        where:
        what                                  | body | otherBody || message
        'a setting'                           | 'configuration:\n  - values:\n      acme.shared: 1\n    when: { feature: grails-quartz }\n' |
                'configuration:\n  acme.shared: 2\n' || 'The feature acme-other sets acme.shared, which another feature sets to another value'
        'a setting of an environment file'    | 'configuration:\n  - environmentFile: production\n    values:\n      acme.shared: 1\n    when: { feature: grails-quartz }\n' |
                'configuration:\n  - environmentFile: production\n    values:\n      acme.shared: 2\n' ||
                'The feature acme-other sets acme.shared for the environment production, which another feature sets to another value'
        'the version of a dependency'         | 'dependencies:\n  - scope: implementation\n    coordinates: com.acme:shared\n    version: 1.0.0\n    when: { feature: grails-quartz }\n' |
                'dependencies:\n  - scope: runtimeOnly\n    coordinates: com.acme:shared\n    version: 2.0.0\n' ||
                'The feature acme-other declares com.acme:shared in version 2.0.0, and it is also declared in version 1.0.0'
        'the version of a build dependency'   | 'dependencies:\n  - scope: buildscript\n    coordinates: com.acme:shared-gradle\n    version: 1.0.0\n    when: { feature: grails-quartz }\n' |
                'dependencies:\n  - scope: buildscript\n    coordinates: com.acme:shared-gradle\n    version: 2.0.0\n' ||
                'The feature acme-other declares com.acme:shared-gradle in version 2.0.0, and it is also declared in version 1.0.0'
        'the version of a platform'           | 'platforms:\n  - coordinates: com.acme:shared-bom\n    version: 1.0.0\n    when: { feature: grails-quartz }\n' |
                'platforms:\n  - coordinates: com.acme:shared-bom\n    version: 2.0.0\n' ||
                'The feature acme-other declares com.acme:shared-bom in version 2.0.0, and it is also declared in version 1.0.0'
    }

    @Unroll
    void "the pack of a plugin still cannot say how its feature is selected without being named, or read what is of Forge: #key"() {
        when:
        new PackDescriptorParser().parse(HEADER + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith("${place}: has the key '${key}', which this version of Forge does not read here; the keys are [")

        where:
        key                  | place             | text
        'default'            | 'the descriptor'  | 'default: true\n'
        'adds'               | 'the descriptor'  | 'adds:\n  - gsp\n'
        'order'              | 'the descriptor'  | 'order: test\n'
        'visible'            | 'the descriptor'  | 'visible: false\n'
        'reliesOn'           | 'the descriptor'  | 'reliesOn:\n  - gsp\n'
        'dataSource'         | 'the descriptor'  | 'oneOf: database-driver\ndataSource:\n  a: 1\n'
        'dataSourceOfDriver' | 'the descriptor'  | 'dataSourceOfDriver: true\n'
        'versionsOfForge'    | 'the descriptor'  | "versionsOfForge:\n  asciidoctorj: '3.0.0'\n"
        'guideOfGrails'      | 'the descriptor'  | "guideOfGrails: 'guide/a.html'\n"
        'versionOfForge'     | 'dependencies[0]' | 'dependencies:\n  - scope: implementation\n    coordinates: com.acme:a\n    versionOfForge: true\n'
        'artifactOfForge'    | 'buildPlugins[0]' | 'buildPlugins:\n  - id: com.gradleup.shadow\n    artifactOfForge: shadow\n'
    }

    void "the pack of a plugin cannot join a group of Grails"() {
        given:
        Pack pack = packOf('directory', 'oneOf: database-driver\n')

        when:
        generate(['acme-audit'], [pack], ApplicationType.WEB, new Options())

        then:
        PackRefusedException e = thrown()
        e.message.endsWith('names the exclusive group database-driver of the framework, which a pack cannot join yet')
    }

    @Unroll
    void "the check of a pack reads the keys every pack has: #problem"() {
        given:
        Path pack = directory.resolve('checked')
        Files.createDirectories(pack.resolve('templates'))
        Files.writeString(pack.resolve('feature.yml'), HEADER + text)
        Files.writeString(pack.resolve('templates/a.txt.gsp'), template)

        expect:
        PackCheck.check(pack) == (problem == null ? [] : [problem])

        where:
        text | template || problem
        'files:\n  - from: a.txt.gsp\n    to: a.txt\n    when: { feature: grails-quartz }\n' | '${project.name}' || null
        'files:\n  - from: a.txt.gsp\n    to: a.txt\n    when: { feature: grails-quartz }\n' | '${project.nam}' ||
                'files[0].from: The template a.txt.gsp cannot be rendered: line 1: There is no \'project.nam\' for a template: ' +
                'template API 1 has [project.className, project.name, project.naturalName, project.packageName, project.packagePath, project.propertyName] ' +
                '(for an application with applicationType web, options [data:hibernate5, jdk:21, reloading:devtools, servlet:tomcat])'
        'files:\n  - from: a.txt.gsp\n    to: a.gsp\n    template: false\n' | '${project.nam}' || null
        'files:\n  - from: a.txt.gsp\n    to: a.txt\n    when: { features: [gsp] }\n' | 'a' ||
                "files[0].when: has the key 'features', and a condition has one of the keys [feature, group, anyOf, allOf, not, applicationType, option, jdkAtLeast, parameter]"
        'dependencies:\n  - scope: buildscript\n    coordinates: com.acme:a\n    when: { option: { data: oracle } }\n' | 'a' ||
                "dependencies[0].when.option.data: must be one of [hibernate5, hibernate7, mongodb, neo4j], or a list of them, but is 'oracle'"
        'build:\n  blocks:\n    start: x\n' | 'a' || "build.blocks: has text for [afterPlugins, afterDependencies, end], but one place is 'start'"
        'requiresJdk: java25\n' | 'a' || "requiresJdk: must be the number of a JDK, such as 25, but is 'java25'"
    }

    void "what a pack adds says which of it is code of the build"() {
        given:
        Pack pack = packOf('directory', '''\
            dependencies:
              - scope: buildscript
                coordinates: com.acme:acme-audit-gradle
                version: 2.3.0
              - scope: implementation
                coordinates: com.acme:acme-audit-grails
            build:
              extensions:
                acmeAudit:
                  - text: retention = 90
              blocks:
                end: println 'audit'
            configuration:
              - values:
                  acme.audit.enabled: true
            ''')

        expect:
        pack.summary() == ['build file blocks, which are code of the build: acmeAudit',
                           'text it adds to the build file as it is, which is code of the build: end',
                           'dependencies of the build itself, which are code of the build: com.acme:acme-audit-gradle:2.3.0',
                           'dependencies: implementation com.acme:acme-audit-grails',
                           'configuration: 1 setting']
    }

    // the settings of a configuration by their whole names, however the file nests them
    private static Map<String, Object> flat(Map<String, Object> settings, String prefix) {
        Map<String, Object> all = new TreeMap<>()
        settings.each { String key, Object value ->
            if (value instanceof Map) {
                all.putAll(flat((Map<String, Object>) value, prefix + key + '.'))
            } else {
                all.put(prefix + key, value)
            }
        }
        return all
    }

    private static Options options(GormImpl data, ServletImpl servlet = null, JdkVersion jdk = null) {
        return new Options(DevelopmentReloading.DEFAULT_OPTION, data ?: GormImpl.DEFAULT_OPTION, servlet ?: ServletImpl.DEFAULT_OPTION,
                jdk ?: JdkVersion.DEFAULT_OPTION)
    }

    private Pack packOf(String from, String body, Map<String, String> files = [:], String name = 'acme-audit') {
        String descriptor = HEADER.replace('name: acme-audit', "name: ${name}") + body.stripIndent()
        if (from == 'jar') {
            Path jar = directory.resolve("${name}.jar")
            new ZipOutputStream(Files.newOutputStream(jar)).withCloseable { ZipOutputStream zip ->
                (['META-INF/grails-forge/feature.yml': descriptor] + files.collectEntries { String path, String text ->
                    ["META-INF/grails-forge/templates/${path}".toString(), text]
                }).each { String path, String text ->
                    zip.putNextEntry(new ZipEntry(path))
                    zip.write(text.getBytes('UTF-8'))
                    zip.closeEntry()
                }
            }
            return new PackLoader().fromJar(jar)
        }
        Path packDirectory = Files.createDirectories(directory.resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), descriptor)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text ->
            Path file = packDirectory.resolve('templates').resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, text)
        }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private Map<String, String> generate(List<String> features, List<Pack> packs, ApplicationType type, Options options) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(type, project, options, OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, packs)
        generator.generate(type, project, output, context)
        return output.getProject()
    }
}
