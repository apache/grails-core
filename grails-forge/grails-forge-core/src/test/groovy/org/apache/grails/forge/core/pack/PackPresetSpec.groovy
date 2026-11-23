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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.Feature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.util.NameUtils
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A preset: a pack whose kind is {@code preset}, which names the one type of application it is for,
 * gives the options their defaults, asks for features and adds to the application what a feature pack
 * adds. Read by the parser, used by the generator, checked and tested as a feature pack is.
 */
class PackPresetSpec extends ApplicationContextSpec {

    static final String COMPATIBILITY = "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    static final String PRESET = 'format: 1\nkind: preset\nname: acme-service\ntemplateApi: 1\n' + COMPATIBILITY + 'applicationType: rest-api\n'

    static final String FEATURE = 'format: 1\nkind: feature\nname: acme-audit\ntemplateApi: 1\n' + COMPATIBILITY

    @TempDir
    Path directory

    PackDescriptorParser parser = new PackDescriptorParser()

    void "a preset names its type of application, gives options and asks for features"() {
        when:
        PackDescriptor descriptor = parser.parse(PRESET + '''\
            title: Acme Service
            description: The services of Acme
            options: { data: hibernate7, servlet: undertow, reloading: none, jdk: 25 }
            features: [views-json, grails-quartz]
            conflicts: [mockito]
            dependencies:
              - scope: implementation
                coordinates: com.acme:acme-logging
                version: 1.2.0
            configuration:
              acme.service.team: payments
            files:
              - from: TEAM.md
                to: TEAM.md
            '''.stripIndent(), 'acme.yml')

        then:
        descriptor.preset
        descriptor.kind == 'preset'
        descriptor.name == 'acme-service'
        descriptor.title == 'Acme Service'
        descriptor.preset.applicationType == ApplicationType.REST_API
        descriptor.applicationTypes == [ApplicationType.REST_API] as Set
        descriptor.preset.data == GormImpl.HIBERNATE7
        descriptor.preset.servlet == ServletImpl.UNDERTOW
        descriptor.preset.reloading == DevelopmentReloading.NONE
        descriptor.preset.jdk == JdkVersion.JDK_25
        descriptor.preset.options == [data: 'hibernate7', servlet: 'undertow', reloading: 'none', jdk: '25']

        and: 'the features it asks for are selected with it, as the requirements of a feature are'
        descriptor.requires == ['views-json', 'grails-quartz']
        descriptor.conflicts == ['mockito']

        and: 'and it adds what a feature pack adds'
        descriptor.dependencies*.artifactId == ['acme-logging']
        descriptor.configuration == ['acme.service.team': 'payments']
        descriptor.files*.to == ['TEAM.md']

        and: 'it is in no group or category'
        descriptor.oneOf == null
        descriptor.category == 'Other'
    }

    void "a preset without options or features gives none, and a feature pack is no preset"() {
        when:
        PackDescriptor preset = parser.parse(PRESET, 'acme.yml')
        PackDescriptor feature = parser.parse(FEATURE, 'audit.yml')

        then:
        preset.preset.options == [:]
        preset.preset.data == null
        preset.preset.jdk == null
        preset.requires == []

        and:
        !feature.preset
        feature.kind == 'feature'
        feature.preset == null

        and: 'two presets read from the same text are equal, and one with other options is not'
        parser.parse(PRESET + 'options: { jdk: 25 }\n', 'a.yml') == parser.parse(PRESET + 'options: { jdk: 25 }\n', 'b.yml')
        parser.parse(PRESET + 'options: { jdk: 25 }\n', 'a.yml') != parser.parse(PRESET + 'options: { jdk: 26 }\n', 'a.yml')
    }

    @Unroll
    void "a preset is refused when #problem"() {
        when:
        parser.parse(descriptor, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]

        where:
        problem                                 | descriptor                                                          || message
        'it names no type of application'       | PRESET.replace('applicationType: rest-api\n', '')                   || 'applicationType: is required'
        'its type of application is unknown'    | PRESET.replace('rest-api', 'service')                               || "applicationType: must be one of [plugin, rest-api, rest-api-plugin, web, web-plugin], but is 'service'"
        'it names types as a feature does'      | PRESET + 'applicationTypes: [web]\n'                                || 'applicationTypes: a preset is for one type of application, which it names with applicationType'
        'it requires as a feature does'         | PRESET + 'requires: [gsp]\n'                                        || 'requires: a preset asks for features with features'
        'it is in a group'                      | PRESET + 'oneOf: database-driver\n'                                 || 'oneOf: a preset is in no exclusive group'
        'it has a category'                     | PRESET + 'category: Database\n'                                     || 'category: a preset is not among the features that are listed, and has no category'
        'it gives parameters'                   | PRESET + 'parameters: { team: payments }\n'                         || "the descriptor: has the key 'parameters', which this version of Forge does not read here; the keys are [format, kind, name, templateApi, compatibility, title, description, documentation, thirdPartyDocumentation, applicationType, options, features, conflicts, dependencies, platforms, buildPlugins, build, configuration, files, readme, instructions]"
        'it gives an option there is not'       | PRESET + 'options: { test: junit }\n'                               || "options: has the key 'test', which this version of Forge does not read here; the keys are [data, servlet, reloading, jdk]"
        'its options are a list'                | PRESET + 'options: [jdk]\n'                                         || 'options: must be a mapping of keys to values, but is a list'
        'its Grails Data is unknown'            | PRESET + 'options: { data: hibernate }\n'                           || "options.data: must be one of [hibernate5, hibernate7, mongodb, neo4j], as --data takes it, but is 'hibernate'"
        'its servlet container is unknown'      | PRESET + 'options: { servlet: Tomcat }\n'                           || "options.servlet: must be one of [tomcat, jetty, undertow, none], as --servlet takes it, but is 'Tomcat'"
        'its reloading is unknown'              | PRESET + 'options: { reloading: spring }\n'                         || "options.reloading: must be one of [devtools, jrebel, none], as --reloading takes it, but is 'spring'"
        'its Java is not one Forge has'         | PRESET + 'options: { jdk: 17 }\n'                                   || 'options.jdk: must be one of [21, 25, 26], the versions of Java this version of Forge generates for, but is the number 17'
        'its Java is text'                      | PRESET + "options: { jdk: '25' }\n"                                 || "options.jdk: must be one of [21, 25, 26], the versions of Java this version of Forge generates for, but is '25'"
        'it asks for a feature twice'           | PRESET + 'features: [views-json, views-json]\n'                     || "features[1]: 'views-json' is listed earlier too"
        'it asks for what is no feature'        | PRESET + "features: ['../x']\n"                                     || "features[0]: must be the name of a feature, or 'group:artifact#name' for the feature of another pack, but is '../x'"
        'it asks for itself'                    | PRESET + 'features: [acme-service]\n'                               || "features[0]: must not name the feature itself, 'acme-service'"
        'it asks for a feature it conflicts with' | PRESET + 'features: [mockito]\nconflicts: [mockito]\n'            || "conflicts: 'mockito' is also asked for"
    }

    @Unroll
    void "a feature pack is refused when it has the key #key of a preset"() {
        when:
        parser.parse(FEATURE + line, 'audit.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [message]

        where:
        key               | line                          || message
        'applicationType' | 'applicationType: web\n'      || 'applicationType: only a preset (kind: preset) names one type of application; a feature names the types it is for with applicationTypes'
        'options'         | 'options: { jdk: 25 }\n'      || 'options: only a preset (kind: preset) gives options'
        'features'        | 'features: [views-json]\n'    || 'features: only a preset (kind: preset) asks for features; a feature requires them with requires'
    }

    void "a kind there is not is refused, and a pack of Forge is a feature"() {
        when:
        parser.parse(FEATURE.replace('kind: feature', 'kind: profile'), 'audit.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == ["kind: must be 'feature' or 'preset', the kinds this version of Forge reads, but is 'profile'"]

        when:
        parser.parseOfForge(PRESET, 'bundled.yml')

        then:
        PackDescriptorException ofForge = thrown()
        ofForge.problems.contains("kind: must be 'feature', the only kind of a pack Forge has in itself, but is 'preset'")
    }

    void "an application made from a preset has its features, its options as defaults and what it adds"() {
        given:
        Pack preset = pack(PRESET + '''\
            options: { data: mongodb, servlet: undertow, jdk: 25 }
            features: [views-json, grails-quartz]
            dependencies:
              - scope: implementation
                coordinates: com.acme:acme-logging
                version: 1.2.0
            configuration:
              acme.service.team: payments
            files:
              - from: TEAM.md.gsp
                to: TEAM.md
            '''.stripIndent(), ['TEAM.md.gsp': '${pack.name} ${applicationType} ${options.data} ${options.servlet} ${options.jdk}\n'])

        when: 'the options are those of the preset, as the create commands give them'
        GeneratorContext context = context(ApplicationType.REST_API, preset.descriptor.preset.optionsWith(null, null, null, null, OperatingSystem.LINUX),
                ['acme-service'], [preset])
        Map<String, String> output = generate(ApplicationType.REST_API, context)

        then: 'the features it asks for, and its own, which is one of a pack'
        context.features.containsAll(['acme-service', 'views-json', 'grails-quartz', 'gorm-mongodb', 'grails-undertow'])
        output['build.gradle'].contains('implementation "com.acme:acme-logging:1.2.0"')
        output['build.gradle'].contains('org.apache.grails:grails-quartz')
        context.jdkVersion == JdkVersion.JDK_25
        output['grails-app/conf/application.yml'].contains('team: payments')
        output['TEAM.md'] == 'acme-service rest-api mongodb undertow 25\n'

        and: 'the record says which preset it was made from, and has it as the feature of a pack'
        output['.grails/project.yml'].contains('preset: acme-service\n')
        output['.grails/project.yml'].contains('requestedFeatures:\n- name: acme-service\n')
        output['.grails/forge.lock.yml'].contains('- name: acme-service\n  source: pack\n')

        and: 'the features it asks for are not asked for by the record: the preset asks for them again'
        !output['.grails/project.yml'].contains('- name: views-json\n')
    }

    void "an option that is given is used in place of the one of the preset"() {
        given:
        Pack preset = pack(PRESET + 'options: { data: mongodb, servlet: undertow, jdk: 25 }\n', [:])

        when:
        GeneratorContext context = context(ApplicationType.REST_API,
                preset.descriptor.preset.optionsWith(null, GormImpl.HIBERNATE7, null, JdkVersion.JDK_21, OperatingSystem.LINUX), ['acme-service'], [preset])

        then:
        context.features.containsAll(['gorm-hibernate7', 'grails-undertow'])
        !context.features.contains('gorm-mongodb')
        context.jdkVersion == JdkVersion.JDK_21
    }

    void "an option a preset does not give is the default of the framework"() {
        given:
        Pack preset = pack(PRESET, [:])

        when:
        Options options = preset.descriptor.preset.optionsWith(null, null, null, null, OperatingSystem.LINUX)
        GeneratorContext context = context(ApplicationType.REST_API, options, ['acme-service'], [preset])

        then:
        options.javaVersion == JdkVersion.DEFAULT_OPTION
        context.features.contains(GormImpl.DEFAULT_OPTION.name)
        context.applicationOptions.servletImpl == ServletImpl.DEFAULT_OPTION
    }

    void "a preset is not a feature that is listed or selected by its name"() {
        given:
        Pack preset = pack(PRESET, [:])

        when:
        def available = beanContext.getBean(ProjectGenerator).availableFeatures(ApplicationType.REST_API, [preset])

        then:
        !available.features.anyMatch { Feature feature -> feature.name == 'acme-service' }
        available.findFeature('acme-service').present
    }

    @Unroll
    void "a preset is refused when #what"() {
        given:
        Pack preset = pack(PRESET, [:])
        Pack other = pack(PRESET.replace('acme-service', 'acme-batch'), [:], 'acme-batch')

        when:
        beanContext.getBean(ProjectGenerator).availableFeatures(type, presets.collect { it == 'other' ? other : preset })

        then:
        PackRefusedException e = thrown()
        e.message.replace(directory.toString(), 'DIR').replace(File.separator, '/') == message
        !e.ofThePack

        where:
        what                                             | type                     | presets             || message
        'an application of another type is made from it' | ApplicationType.WEB      | ['preset']          || 'The preset acme-service from DIR/acme-service is for an application of the type rest-api, and this is one of the type web'
        'a second preset is given'                       | ApplicationType.REST_API | ['preset', 'other'] || 'The presets acme-service from DIR/acme-service and acme-batch from DIR/acme-batch were both given, and an application is made from one preset'
    }

    void "a preset cannot write a file of the framework, nor ask for a feature there is not"() {
        given:
        Pack writesBuild = pack(PRESET + 'files:\n  - from: build.gradle\n    to: build.gradle\n', ['build.gradle': '// mine\n'])
        Pack asksForMissing = pack(PRESET.replace('acme-service', 'acme-views') + 'features: [acme-missing]\n', [:], 'acme-views')

        when:
        generate(ApplicationType.REST_API, context(ApplicationType.REST_API, new Options(), ['acme-service'], [writesBuild]))

        then:
        IllegalArgumentException written = thrown()
        written.message == 'The features write the file build.gradle more than once'

        when:
        context(ApplicationType.REST_API, new Options(), ['acme-views'], [asksForMissing])

        then:
        IllegalArgumentException asked = thrown()
        asked.message.contains('acme-missing')
    }

    void "the check of a pack takes a preset, and its fixture is the type of application it is for, with its options"() {
        given:
        Path pack = directory(PRESET + "options: { data: mongodb }\nfiles:\n  - from: Kind.groovy.gsp\n    to: 'src/main/groovy/{packagePath}/Kind.groovy'\n",
                ['Kind.groovy.gsp': 'package ${project.packageName}\n// ${applicationType} ${options.data} ${pack.name}\nclass Kind {\n}\n'])
        Path output = directory.resolve('fixtures')

        expect:
        PackCheck.check(pack) == []

        when:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        int exit = PackFixtures.run([pack.toString(), output.toString()] as String[], new PrintStream(out, true, 'UTF-8'),
                new PrintStream(new ByteArrayOutputStream(), true, 'UTF-8'))

        then:
        exit == PackFixtures.GENERATED
        out.toString('UTF-8').readLines() == ['generated rest-api', 'feature pack fixtures: generated=1 refused=0']
        Files.readString(output.resolve('rest-api/src/main/groovy/com/example/Kind.groovy')).contains('// rest-api mongodb acme-service')
        Files.readString(output.resolve('rest-api/.grails/project.yml')).contains('preset: acme-service\n')

        when: 'a fixture that gives an option has it in place of the one of the preset'
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), 'hibernate rest-api data=hibernate7\n')
        int listed = PackFixtures.run([pack.toString(), directory.resolve('listed').toString(), fixtures.toString()] as String[],
                new PrintStream(new ByteArrayOutputStream(), true, 'UTF-8'), new PrintStream(new ByteArrayOutputStream(), true, 'UTF-8'))

        then:
        listed == PackFixtures.GENERATED
        Files.readString(directory.resolve('listed/hibernate/src/main/groovy/com/example/Kind.groovy')).contains('// rest-api hibernate7 acme-service')
    }

    private Pack pack(String descriptor, Map<String, String> files, String name = 'acme-service') {
        return new PackLoader().fromDirectory(directory(descriptor, files, name))
    }

    private Path directory(String descriptor, Map<String, String> files, String name = 'acme-service') {
        Path packDirectory = Files.createDirectories(directory.resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), descriptor)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text ->
            Path file = packDirectory.resolve('templates').resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, text, StandardCharsets.UTF_8)
        }
        return packDirectory
    }

    private GeneratorContext context(ApplicationType type, Options options, List<String> features, List<Pack> packs) {
        Project project = NameUtils.parse('example.grails.foo')
        return beanContext.getBean(ProjectGenerator).createGeneratorContext(type, project, options, OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, packs)
    }

    private Map<String, String> generate(ApplicationType type, GeneratorContext context) {
        MapOutputHandler output = new MapOutputHandler()
        beanContext.getBean(ProjectGenerator).generate(type, NameUtils.parse('example.grails.foo'), output, context)
        return output.getProject()
    }
}
