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
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The parameters of a feature pack: the options its feature takes, which whoever creates the
 * application gives, the templates and the conditions of the pack read, and the record of the
 * application keeps.
 */
class PackParametersSpec extends ApplicationContextSpec {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"

    static final String PARAMETERS = '''\
        parameters:
          - name: store
            type: choice
            choices: [table, collection]
            default: table
            description: Where audit events are kept
          - name: retention
            type: string
            default: '90'
          - name: console
            type: boolean
            default: false
        dependencies:
          - scope: implementation
            coordinates: com.acme:acme-audit-mongodb
            version: 2.3.0
            when: { parameter: { store: collection } }
          - scope: implementation
            coordinates: com.acme:acme-audit-console
            version: 2.3.0
            when: { parameter: { console: true } }
        files:
          - from: audit.properties.gsp
            to: grails-app/conf/audit.properties
        '''.stripIndent()

    static final Map<String, String> TEMPLATES = ['audit.properties.gsp': 'audit.store=${parameters.store}\naudit.retention=${parameters.retention}\n' +
            '<% if (parameters.console) { %>\naudit.console=true\n<% } %>\n']

    @TempDir
    Path directory

    @Unroll
    void "the templates and the conditions of the pack of a #from read the options that were given, and the defaults of the others: #given"() {
        given:
        Pack pack = packOf(from, PARAMETERS, TEMPLATES)

        when:
        Map<String, String> output = generate([pack], given)

        then:
        output['grails-app/conf/audit.properties'] == properties
        output['build.gradle'].contains('com.acme:acme-audit-mongodb:2.3.0') == mongodb
        output['build.gradle'].contains('com.acme:acme-audit-console:2.3.0') == console

        where:
        from        | given                                              || properties                                                       | mongodb | console
        'directory' | [:]                                                || 'audit.store=table\naudit.retention=90\n'                          | false   | false
        'jar'       | [:]                                                || 'audit.store=table\naudit.retention=90\n'                          | false   | false
        'directory' | [store: 'collection']                              || 'audit.store=collection\naudit.retention=90\n'                     | true    | false
        'jar'       | [store: 'collection', retention: '30 days']        || 'audit.store=collection\naudit.retention=30 days\n'                | true    | false
        'directory' | [console: 'true', retention: '7']                  || 'audit.store=table\naudit.retention=7\naudit.console=true\n'       | false   | true
        'jar'       | [console: 'false', store: 'table']                 || 'audit.store=table\naudit.retention=90\n'                          | false   | false
    }

    void "the options that were given are in the record of the application, and an application without any has the record it had"() {
        given:
        Pack pack = packOf('directory', PARAMETERS, TEMPLATES)

        when:
        Map<String, String> with = generate([pack], [store: 'collection', console: 'true'])
        Map<String, String> without = generate([pack], [:])

        then: 'both files of the record have them, sorted, and only those that were given'
        with['.grails/project.yml'].contains('  featureOptions:\n    acme-audit:\n      console: \'true\'\n      store: collection\n')
        with['.grails/forge.lock.yml'].contains('  featureOptions:\n    acme-audit:\n      console: \'true\'\n      store: collection\n')
        !with['.grails/project.yml'].contains('retention')

        and: 'the record is read again with them'
        ProjectState state = ProjectState.of(with['.grails/project.yml'].bytes, with['.grails/forge.lock.yml'].bytes, with['.grails/baseline/index.yml'].bytes)
        state.manifest.options.featureOptions == ['acme-audit': [console: 'true', store: 'collection']]
        state.lock.options == state.manifest.options

        and: 'an application that was given none has no such member'
        !without['.grails/project.yml'].contains('featureOptions')
        !without['.grails/forge.lock.yml'].contains('featureOptions')
        ProjectState.of(without['.grails/project.yml'].bytes, without['.grails/forge.lock.yml'].bytes, without['.grails/baseline/index.yml'].bytes)
                .manifest.options.featureOptions == [:]
    }

    @Unroll
    void "an option that is none is refused before anything is generated: #what"() {
        given:
        Pack pack = packOf('directory', PARAMETERS, TEMPLATES)

        when:
        generate([pack], [:], ['acme-audit'] + features, options)

        then:
        FeatureSelectionException e = thrown()
        e.message == message

        where:
        what                           | features          | options || message
        'of a feature that is not there' | []              | ['acme-other': [store: 'table']] ||
                'An option was given for the feature acme-other, which the application does not have: an option is that of a feature that is selected'
        'the feature does not have'    | []                | ['acme-audit': [stor: 'table']] ||
                'The feature acme-audit has no option stor: its options are store=table|collection (default table), retention=text (default 90), ' +
                'console=true|false (default false)'
        'of a feature without options' | ['grails-quartz'] | ['grails-quartz': [threads: '4']] || 'The feature grails-quartz has no option threads: it takes no options'
        'a choice there is not'        | []                | ['acme-audit': [store: 'file']] ||
                "The option store of the feature acme-audit must be one of [table, collection], but is 'file'"
        'a boolean that is none'       | []                | ['acme-audit': [console: 'yes']] ||
                "The option console of the feature acme-audit must be true or false, but is 'yes'"
        'text of two lines'            | []                | ['acme-audit': [retention: '1\n2']] ||
                'The option retention of the feature acme-audit must be one line of text of at most 200 characters that is not blank and has no control ' +
                "characters, but is '1?2'"
        'text with an escape'          | []                | ['acme-audit': [retention: '\u001b[31m']] ||
                'The option retention of the feature acme-audit must be one line of text of at most 200 characters that is not blank and has no control ' +
                "characters, but is '?[31m'"
        'text that is blank'           | []                | ['acme-audit': [retention: ' ']] ||
                'The option retention of the feature acme-audit must be one line of text of at most 200 characters that is not blank and has no control ' +
                "characters, but is ' '"
    }

    @Unroll
    void "the parameters of a descriptor are held to what they are: #problem"() {
        when:
        new PackDescriptorParser().parse(HEADER + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        text | problem
        'parameters: { store: table }\n' | 'parameters: must be a list of mappings, but is a mapping'
        'parameters:\n  - name: Store\n    type: string\n    default: a\n' |
                "parameters[0].name: must be a name a template can read, of at most 40 letters and digits, starting with a lower case letter, but is 'Store'"
        'parameters:\n  - name: audit-store\n    type: string\n    default: a\n' |
                "parameters[0].name: must be a name a template can read, of at most 40 letters and digits, starting with a lower case letter, but is 'audit-store'"
        'parameters:\n  - name: a\n    type: string\n    default: a\n  - name: a\n    type: boolean\n    default: true\n' |
                "parameters[1].name: 'a' is declared by an earlier entry too"
        'parameters:\n  - name: a\n    type: number\n    default: 1\n' | "parameters[0].type: must be one of [string, boolean, choice], but is 'number'"
        'parameters:\n  - name: a\n    type: string\n' | 'parameters[0].default: is required: the value the parameter has when none is given'
        'parameters:\n  - name: a\n    type: string\n    default: 90\n' | 'parameters[0].default: must be text, but is the number 90; put it in quotes'
        'parameters:\n  - name: a\n    type: boolean\n    default: "true"\n' | "parameters[0].default: must be true or false, but is 'true'"
        'parameters:\n  - name: a\n    type: choice\n    choices: [x, y]\n    default: z\n' | "parameters[0].default: must be one of [x, y], but is 'z'"
        'parameters:\n  - name: a\n    type: choice\n    choices: [x]\n    default: x\n' | 'parameters[0].choices: must list 2 to 20 values a choice can have'
        'parameters:\n  - name: a\n    type: string\n    choices: [x, y]\n    default: x\n' | 'parameters[0].choices: is for a parameter of the type choice'
        'parameters:\n  - name: a\n    type: string\n    default: x\n    secret: true\n' |
                "parameters[0]: has the key 'secret', which this version of Forge does not read here; the keys are [name, type, default, choices, description]"
        'parameters:\n' + (1..21).collect { "  - name: a${it}\n    type: string\n    default: x\n" }.join('') | 'parameters: can have 20 parameters, but has 21'
        'parameters:\n  - name: a\n    type: boolean\n    default: true\nfiles:\n  - from: a.txt\n    to: a.txt\n    when: { parameter: { b: true } }\n' |
                'files[0].when.parameter: must be one of the parameters the pack declares, [a], with a value or a list of values, but is a mapping'
        'parameters:\n  - name: a\n    type: boolean\n    default: true\nfiles:\n  - from: a.txt\n    to: a.txt\n    when: { parameter: { a: maybe } }\n' |
                "files[0].when.parameter.a: must be true or false, or a list of such values, but is 'maybe'"
        'files:\n  - from: a.txt\n    to: a.txt\n    when: { parameter: { a: true } }\n' |
                'files[0].when.parameter: must be one of the parameters the pack declares, [], with a value or a list of values, but is a mapping'
    }

    void "a preset declares no parameters"() {
        when:
        new PackDescriptorParser().parse(HEADER.replace('kind: feature', 'kind: preset') + 'applicationType: web\nparameters:\n  - name: a\n    type: boolean\n    default: true\n', 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems[0].startsWith("the descriptor: has the key 'parameters', which this version of Forge does not read here")
    }

    void "the check of a pack renders its templates with every value of a boolean and a choice, and says which"() {
        given:
        Path pack = directory.resolve('checked')
        Files.createDirectories(pack.resolve('templates'))
        Files.writeString(pack.resolve('feature.yml'), HEADER + PARAMETERS)
        Files.writeString(pack.resolve('templates/audit.properties.gsp'), template)

        expect:
        PackCheck.check(pack) == problems

        where:
        template || problems
        TEMPLATES['audit.properties.gsp'] || []
        '${parameters.stor}' || ['files[0].from: The template audit.properties.gsp cannot be rendered: line 1: There is no \'parameters.stor\' for a template: ' +
                                         'template API 1 has [parameters.console, parameters.retention, parameters.store] (for an application with applicationType web, ' +
                                         'options [data:hibernate5, jdk:21, reloading:devtools, servlet:tomcat], parameters [console:false, retention:90, store:table])']
        '<% if (parameters.store == "collection") { %>${nope}<% } %>' ||
                ['files[0].from: The template audit.properties.gsp cannot be rendered: line 1: There is no \'nope\' for a template: template API 1 has ' +
                         '[applicationType, features, grailsVersion, options, pack, parameters, project] (for an application with applicationType rest-api, ' +
                         'options [data:hibernate7, jdk:25, reloading:jrebel, servlet:jetty], parameters [console:true, retention:90, store:collection])']
    }

    void "what a pack adds says the options its feature takes"() {
        expect:
        packOf('directory', PARAMETERS, TEMPLATES).summary().contains('options, given as --feature-option acme-audit.NAME=VALUE: ' +
                'store=table|collection (default table), retention=text (default 90), console=true|false (default false)')
    }

    private Pack packOf(String from, String body, Map<String, String> files = [:]) {
        String descriptor = HEADER + body
        if (from == 'jar') {
            Path jar = directory.resolve('acme-audit.jar')
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
        Path packDirectory = Files.createDirectories(directory.resolve('acme-audit'))
        Files.writeString(packDirectory.resolve('feature.yml'), descriptor)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text -> Files.writeString(packDirectory.resolve('templates').resolve(path), text) }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private Map<String, String> generate(List<Pack> packs, Map<String, String> given, List<String> features = ['acme-audit'],
                                         Map<String, Map<String, String>> options = null) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        Options ofFeatures = new Options().withFeatureOptions(options != null ? options : given.isEmpty() ? [:] : ['acme-audit': given])
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, ofFeatures, OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, packs)
        generator.generate(ApplicationType.WEB, project, output, context)
        return output.getProject()
    }
}
