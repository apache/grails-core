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

import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.options.GormImpl
import org.grails.forge.options.Options
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The keys of a descriptor with which a pack Forge has in itself says how its feature is selected and
 * what it gives beside other features: what is read, what is refused, and that the pack of a plugin
 * can say none of it.
 */
class PackOfForgeDescriptorSpec extends Specification {

    static final String MINIMAL = PackDescriptorParserSpec.MINIMAL
    static final String CONDITION_KEYS = '[feature, group, anyOf, allOf, not, applicationType, option, jdkAtLeast]'

    PackDescriptorParser parser = new PackDescriptorParser()

    void "a pack of Forge says how its feature is selected"() {
        when:
        PackOfForge ofForge = parser.parseOfForge(MINIMAL + '''\
oneOf: database-driver
default:
  allOf:
    - option: { data: [hibernate5, hibernate7] }
    - not: { group: database-driver }
visible: false
order: test
requiresJdk: 25
adds:
  - testcontainers
  - feature: gorm-hibernate5
    when: { not: { anyOf: [gorm-hibernate5, gorm-hibernate7] } }
reliesOn:
  - gsp
  - feature: asset-pipeline-grails
    when: { applicationType: web }
dataSource:
  dataSource.username: sa
  environments:
    test:
      dataSource.url: 'jdbc:h2:mem:testDb'
dataSourceOfDriver: true
''', 'forge.yml').ofForge

        then:
        ofForge.byDefault.toString() == 'allOf(data=hibernate5|hibernate7, not group=database-driver)'
        !ofForge.visible
        ofForge.order == FeaturePhase.TEST.order
        ofForge.requiresJdk == 25
        ofForge.adds.collect { [it.feature, it.when.toString()] } == [['testcontainers', 'always'], ['gorm-hibernate5', 'not anyOf(gorm-hibernate5, gorm-hibernate7)']]
        ofForge.reliesOn.collect { [it.feature, it.when.toString()] } == [['gsp', 'always'], ['asset-pipeline-grails', 'applicationType=web']]
        ofForge.dataSource == ['dataSource.username': 'sa', environments: [test: ['dataSource.url': 'jdbc:h2:mem:testDb']]]
        ofForge.dataSourceOfDriver
    }

    void "a pack of Forge says what it gives beside other features"() {
        when:
        PackDescriptor descriptor = parser.parseOfForge(MINIMAL + '''\
dependencies:
  - scope: buildscript
    coordinates: com.acme:a
    when: { feature: gsp }
  - scope: testAndDevelopmentOnly
    coordinates: com.acme:b
buildPlugins:
  - id: com.acme.audit
    apply: true
    when: { applicationType: [web, web-plugin] }
files:
  - from: a.gsp
    to: a.gsp
    template: false
    when: { jdkAtLeast: 25 }
  - from: run.sh
    to: run.sh
    executable: true
configuration:
  - values:
      acme.always: 1
  - when: { not: gorm-mongodb }
    environmentFile: test
    values:
      acme.tested: 2
build:
  propertyComments:
    - ' a comment'
  extensions:
    acme: 'plain = 1'
    tasks.withType(Test).configureEach:
      - text: useJUnitPlatform()
      - when: { feature: geb-with-local-browsers }
        text: |
          systemProperty('geb.env', 'x')
''', 'forge.yml')

        then:
        descriptor.dependencies.collect { [it.scope, it.when.toString()] } == [['buildscript', 'gsp'], ['testAndDevelopmentOnly', 'always']]
        descriptor.buildPlugins.collect { [it.id, it.apply, it.when.toString()] } == [['com.acme.audit', true, 'applicationType=web|web-plugin']]
        descriptor.files.collect { [it.from, it.isTemplate(), it.executable, it.when.toString()] } ==
                [['a.gsp', false, false, 'jdkAtLeast=25'], ['run.sh', false, true, 'always']]
        descriptor.configuration == [:]
        descriptor.ofForge.settings.collect { [it.when.toString(), it.environmentFile, it.values] } ==
                [['always', null, ['acme.always': 1]], ['not gorm-mongodb', 'test', ['acme.tested': 2]]]
        descriptor.ofForge.propertyComments == [' a comment']
        descriptor.buildExtensions == [acme: 'plain = 1']
        descriptor.ofForge.buildExtensionParts.collectEntries { String name, List<PackOfForge.Part> parts -> [name, parts.collect { [it.text, it.when.toString()] }] } ==
                ['tasks.withType(Test).configureEach': [['useJUnitPlatform()', 'always'], ["systemProperty('geb.env', 'x')\n", 'geb-with-local-browsers']]]
    }

    void "a pack of Forge that says none of it is the descriptor a plugin would have, and what it says is part of what it is"() {
        given:
        String text = MINIMAL + 'default: { feature: gsp }\nadds:\n  - spock\n'

        expect:
        parser.parseOfForge(MINIMAL, 'forge.yml').ofForge == PackOfForge.NONE
        parser.parseOfForge(MINIMAL, 'forge.yml') == parser.parse(MINIMAL, 'acme.yml')
        parser.parse(MINIMAL, 'acme.yml').ofForge.is(PackOfForge.NONE)
        parser.parseOfForge(text, 'forge.yml') == parser.parseOfForge(text, 'again.yml')
        parser.parseOfForge(text, 'forge.yml').hashCode() == parser.parseOfForge(text, 'again.yml').hashCode()
        [['feature: gsp', 'feature: gson'], ['- spock', '- mockito'], ['default: { feature: gsp }', 'default: true']].every { List<String> change ->
            parser.parseOfForge(text, 'forge.yml') != parser.parseOfForge(text.replace(change[0], change[1]), 'other.yml')
        }
    }

    void "what a pack of Forge says of its selection cannot be changed once it is read"() {
        given:
        PackOfForge ofForge = parser.parseOfForge(MINIMAL + 'oneOf: database-driver\nadds:\n  - spock\nreliesOn:\n  - gsp\ndataSource:\n  a: 1\n' +
                'configuration:\n  - values:\n      a: 1\nbuild:\n  propertyComments:\n    - c\n  extensions:\n    acme:\n      - text: x\n', 'forge.yml').ofForge

        when:
        change(ofForge)

        then:
        thrown(UnsupportedOperationException)

        where:
        change << [
                { PackOfForge it -> it.adds.clear() },
                { PackOfForge it -> it.reliesOn.clear() },
                { PackOfForge it -> it.dataSource.put('b', 2) },
                { PackOfForge it -> it.settings.clear() },
                { PackOfForge it -> it.settings.first().values.put('b', 2) },
                { PackOfForge it -> it.propertyComments.clear() },
                { PackOfForge it -> it.buildExtensionParts.clear() },
                { PackOfForge it -> it.buildExtensionParts.acme.clear() },
        ]
    }

    @Unroll
    void "the condition #condition holds of a web application with gsp and a database driver, made with Hibernate 7: #holds"() {
        given:
        Feature gsp = Stub(Feature) { getName() >> 'gsp' }
        OneOfFeature h2 = Stub(OneOfFeature) { getName() >> 'h2'; getGroup() >> 'database-driver' }
        PackCondition read = parser.parseOfForge(MINIMAL + "default: ${condition}\n", 'forge.yml').ofForge.byDefault

        expect:
        read.test(PackCondition.of(ApplicationType.WEB, new Options().withGormImpl(GormImpl.HIBERNATE7), [gsp, h2])) == holds

        where:
        condition                                                     || holds
        'true'                                                        || true
        '{ feature: gsp }'                                            || true
        '{ feature: openapi }'                                        || false
        '{ feature: database-driver }'                                || false
        '{ group: database-driver }'                                  || true
        '{ group: h2 }'                                               || false
        '{ anyOf: [openapi, gsp] }'                                   || true
        '{ anyOf: [openapi, { group: geb }] }'                        || false
        '{ allOf: [gsp, { group: database-driver }] }'                || true
        '{ allOf: [gsp, openapi] }'                                   || false
        '{ not: openapi }'                                            || true
        '{ not: { group: database-driver } }'                         || false
        '{ applicationType: web }'                                    || true
        '{ applicationType: [rest-api, plugin] }'                     || false
        '{ option: { data: hibernate7 } }'                            || true
        '{ option: { data: [hibernate5, mongodb] } }'                 || false
        '{ option: { servlet: tomcat } }'                             || true
        '{ option: { servlet: [jetty, undertow, none] } }'            || false
        '{ option: { reloading: devtools } }'                         || true
        '{ option: { reloading: none } }'                             || false
        '{ jdkAtLeast: 21 }'                                          || true
        '{ jdkAtLeast: 25 }'                                          || false
        '{ allOf: [{ not: { anyOf: [openapi, { not: gsp }] } }, h2] }' || true
    }

    void "a condition says which features it asks for, and whether it asks for the type of the application only"() {
        given:
        PackCondition condition = parser.parseOfForge(MINIMAL + 'default: { allOf: [gsp, { not: { anyOf: [openapi, { group: geb }] } }] }\n', 'forge.yml').ofForge.byDefault
        PackCondition ofType = parser.parseOfForge(MINIMAL + 'default: { not: { anyOf: [{ applicationType: web }, { applicationType: plugin }] } }\n', 'forge.yml').ofForge.byDefault

        expect:
        condition.features == ['gsp', 'openapi'] as Set
        !condition.ofApplicationType
        ofType.features.isEmpty()
        ofType.ofApplicationType
    }

    @Unroll
    void "what a pack of Forge says of its selection is held to what it is: #problem"() {
        when:
        parser.parseOfForge(MINIMAL + text, 'forge.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        text                                                              | problem
        'default: false\n'                                                | 'default: must be true, for a feature every application has, or the condition the feature is selected on, but is the value false'
        'default: gsp\n'                                                  | "default: must be true, for a feature every application has, or the condition the feature is selected on, but is 'gsp'"
        'default: { feature: gsp, group: geb }\n'                         | "default: must be a condition, a mapping with one of the keys ${CONDITION_KEYS}, but is a mapping"
        'default: { kind: gsp }\n'                                        | "default: has the key 'kind', and a condition has one of the keys ${CONDITION_KEYS}"
        'default: { feature: Gsp }\n'                                     | "default.feature: must name a feature of Forge, but is 'Gsp'"
        'default: { feature: [gsp] }\n'                                   | 'default.feature: must be text, but is a list'
        "default: { group: 'a b' }\n"                                     | "default.group: must be the identifier of an exclusive group, but is 'a b'"
        'default: { anyOf: [] }\n'                                        | 'default.anyOf: must be a list of conditions and names of features, but is a list'
        'default: { allOf: gsp }\n'                                       | "default.allOf: must be a list of conditions and names of features, but is 'gsp'"
        'default: { anyOf: [gsp, 1] }\n'                                  | "default.anyOf[1]: must be a condition, a mapping with one of the keys ${CONDITION_KEYS}, but is the number 1"
        'default: { not: [gsp] }\n'                                       | "default.not: must be a condition, a mapping with one of the keys ${CONDITION_KEYS}, but is a list"
        'default: { applicationType: desktop }\n'                         | "default.applicationType: must be one of [web, rest-api, web-plugin, plugin, rest-api-plugin], or a list of them, but is 'desktop'"
        'default: { applicationType: [] }\n'                              | 'default.applicationType: must be one of [web, rest-api, web-plugin, plugin, rest-api-plugin], or a list of them, but is a list'
        'default: { option: { data: oracle } }\n'                         | "default.option.data: must be one of [hibernate5, hibernate7, mongodb, neo4j], or a list of them, but is 'oracle'"
        'default: { option: { servlet: [tomcat, netty] } }\n'             | 'default.option.servlet: must be one of [tomcat, jetty, undertow, none], or a list of them, but is a list'
        'default: { option: { jdk: 21 } }\n'                              | 'default.option: must be one of the options [data, servlet, reloading] with a value or a list of values, but is a mapping'
        'default: { option: tomcat }\n'                                   | "default.option: must be one of the options [data, servlet, reloading] with a value or a list of values, but is 'tomcat'"
        "default: { jdkAtLeast: '25' }\n"                                 | "default.jdkAtLeast: must be the number of a JDK, such as 25, but is '25'"
        'default: { jdkAtLeast: 7 }\n'                                    | 'default.jdkAtLeast: must be the number of a JDK, such as 25, but is the number 7'
        'default: { not: { not: { not: { not: { not: { not: gsp } } } } } }\n' | 'default.not.not.not.not.not: has conditions more than 5 deep'
        "default: { anyOf: [${(1..50).collect { 'f' + it }.join(', ')}] }\n" | 'default: has more than 50 conditions'
        'adds: gsp\n'                                                     | "adds: must be a list, but is 'gsp'"
        'adds:\n  - acme-audit\n'                                         | "adds[0]: must not name the feature itself, 'acme-audit'"
        'adds:\n  - gsp\n  - feature: gsp\n'                              | "adds[1]: 'gsp' is listed earlier too"
        'adds:\n  - com.acme:audit#gsp\n'                                 | "adds[0]: must name a feature of Forge, but is 'com.acme:audit#gsp'"
        'adds:\n  - feature: gsp\n    unless: x\n'                        | "adds[0]: has the key 'unless', which this version of Forge does not read here; the keys are [feature, when]"
        'adds:\n  - when: { feature: gsp }\n'                             | 'adds[0].feature: is required'
        'adds:\n' + ('  - gsp\n' * 21)                                    | 'adds: can name 20 features, but names 21'
        'reliesOn:\n  - feature: gsp\n    when: { feature: openapi }\n'   | 'reliesOn[0].when: can ask for the type of the application only'
        'reliesOn:\n  - feature: gsp\n    when: { option: { data: mongodb } }\n' | 'reliesOn[0].when: can ask for the type of the application only'
        'order: first\n'                                                  | "order: must be one of [lowest, low, default, language, test, build, high, highest], but is 'first'"
        'order: 400\n'                                                    | 'order: must be text, but is the number 400; put it in quotes'
        'visible: no\n'                                                   | "visible: must be true or false, but is 'no'"
        'requiresJdk: 7\n'                                                | 'requiresJdk: must be the number of a JDK, such as 25, but is the number 7'
        "requiresJdk: '25'\n"                                             | "requiresJdk: must be the number of a JDK, such as 25, but is '25'"
        'dataSource:\n  dataSource.url: x\n'                              | 'dataSource: is for a feature of the group database-driver, which oneOf does not name'
        'oneOf: database-driver\ndataSource: x\n'                         | "dataSource: must be a mapping of keys to values, but is 'x'"
        'oneOf: database-driver\ndataSource:\n  environments:\n    a b: {}\n' | "dataSource.environments: the name of an environment is letters, digits, '-' and '_', starting with a letter, but one is 'a b'"
        'dataSourceOfDriver: false\n'                                     | 'dataSourceOfDriver: must be true or be left out, but is the value false'
    }

    @Unroll
    void "what a pack of Forge gives beside other features is held to what it is: #problem"() {
        when:
        parser.parseOfForge(MINIMAL + text, 'forge.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        text                                                                              | problem
        'dependencies:\n  - scope: implementation\n    coordinates: com.acme:a\n    when: gsp\n' | "dependencies[0].when: must be a condition, a mapping with one of the keys ${CONDITION_KEYS}, but is 'gsp'"
        'dependencies:\n  - scope: api\n    coordinates: com.acme:a\n'                    | "dependencies[0].scope: must be one of [implementation, compileOnly, runtimeOnly, developmentOnly, testImplementation, testCompileOnly, testRuntimeOnly, testAndDevelopmentOnly, integrationTestImplementationTestFixtures, buildscript], but is 'api'"
        'buildPlugins:\n  - id: a.b\n    apply: false\n'                                  | 'buildPlugins[0].apply: must be true or be left out, but is the value false'
        'buildPlugins:\n  - id: a.b\n    when: { feature: }\n'                            | 'buildPlugins[0].when.feature: must be text, but is empty'
        'files:\n  - from: a.gsp\n    to: a.txt\n    template: true\n'                    | 'files[0].template: must be false or be left out, but is the value true'
        'files:\n  - from: a.gsp\n    to: a.txt\n    executable: 1\n'                     | 'files[0].executable: must be true or be left out, but is the number 1'
        'configuration:\n  - when: { feature: gsp }\n'                                    | 'configuration[0].values: is required'
        'configuration:\n  - values: x\n'                                                 | "configuration[0].values: must be a mapping of keys to values, but is 'x'"
        "configuration:\n  - environmentFile: '../x'\n    values:\n      a: 1\n"          | "configuration[0].environmentFile: must be the name of an environment: letters, digits, '-' and '_', starting with a letter, but is '../x'"
        'configuration:\n  - values:\n      a: 1\n    file: x\n'                          | "configuration[0]: has the key 'file', which this version of Forge does not read here; the keys are [when, environmentFile, values]"
        'configuration:\n  - a\n'                                                         | "configuration[0]: must be a mapping of keys to values, but is 'a'"
        'build:\n  extensions:\n    acme: []\n'                                           | 'build.extensions.acme: must have 1 to 20 parts, but has 0'
        'build:\n  extensions:\n    acme:\n      - when: { feature: gsp }\n'              | 'build.extensions.acme[0].text: is required'
        'build:\n  extensions:\n    acme:\n      - x\n'                                   | "build.extensions.acme[0]: must be a mapping of text and when, but is 'x'"
        'build:\n  extensions:\n    acme(a, b): x\n'                                      | "build.extensions: the name of an extension is one or more identifiers joined by '.', but one is 'acme(a, b)'"
        'build:\n  propertyComments:\n    - "caf\\u00e9"\n'                               | "build.propertyComments[0]: must be one line of printable ASCII characters, but is 'café'"
        'build:\n  propertyComments: x\n'                                                 | "build.propertyComments: must be a list, but is 'x'"
        "guideOfGrails: '/guide/a.html'\n"                                                | "guideOfGrails: must be a page of the documentation of Grails, such as 'guide/cache.html' or 'guide/REST.html#openApi', but is '/guide/a.html'"
    }

    void "a pack of Forge can name any page of the documentation of this Grails"() {
        expect:
        parser.parseOfForge(MINIMAL + "guideOfGrails: '${page}'\n", 'forge.yml').documentation.endsWith('/' + page)

        where:
        page << ['guide/async.html', 'grails-data/graphql/manual/', 'grails-data/hibernate5/manual/index.html#databaseMigration']
    }

    @Unroll
    void "the pack of a plugin cannot say #key at #place, which is for the packs of Forge"() {
        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems.first().startsWith("${place}: has the key '${key}', which this version of Forge does not read here; the keys are [")
        !e.problems.first().contains("${key},") && !e.problems.first().contains("${key}]")

        where:
        key                  | place             | text
        'default'            | 'the descriptor'  | 'default: true\n'
        'adds'               | 'the descriptor'  | 'adds:\n  - gsp\n'
        'order'              | 'the descriptor'  | 'order: test\n'
        'visible'            | 'the descriptor'  | 'visible: false\n'
        'reliesOn'           | 'the descriptor'  | 'reliesOn:\n  - gsp\n'
        'dataSource'         | 'the descriptor'  | 'oneOf: database-driver\ndataSource:\n  a: 1\n'
        'dataSourceOfDriver' | 'the descriptor'  | 'dataSourceOfDriver: true\n'
    }

    @Unroll
    void "the pack of a plugin cannot write what only a pack of Forge writes otherwise: #problem"() {
        when:
        parser.parse(MINIMAL + text, 'acme.yml')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        text                                                                      | problem
        "guideOfGrails: 'guide/a.html'\n"                                         | "the descriptor: has the key 'guideOfGrails', which this version of Forge does not read here; the keys are [format, kind, name, templateApi, compatibility, title, description, category, documentation, applicationTypes, requires, conflicts, oneOf, dependencies, platforms, buildPlugins, build, configuration, files, readme, instructions, readmeLinks, requiresJdk, thirdPartyDocumentation]"
    }
}
