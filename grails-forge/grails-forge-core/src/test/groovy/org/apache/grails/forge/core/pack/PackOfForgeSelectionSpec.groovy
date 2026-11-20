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
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.Project
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeaturePhase
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.feature.OneOfFeature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.ServletImpl
import org.grails.forge.template.Template
import org.grails.forge.util.NameUtils
import spock.lang.Unroll

/**
 * How the feature of a pack Forge has in itself takes part in the selection, and what it gives beside
 * other features: what its descriptor says with the keys only such a pack has.
 */
class PackOfForgeSelectionSpec extends ApplicationContextSpec {

    static final String NAME = 'acme-of-forge'
    static final String DEPENDENCY = '''\
dependencies:
  - scope: implementation
    coordinates: com.acme:acme-default
'''

    @Unroll
    void "a pack of Forge that is a default when #condition is in a #type application made with #options: #there"() {
        when:
        Generated generated = generate(pack("default: ${condition}\n" + DEPENDENCY), type, options, [])

        then:
        generated.features.contains(NAME) == there
        generated.files['build.gradle'].contains('com.acme:acme-default') == there

        where:
        condition                                                          | type                     | options                                    || there
        'true'                                                             | ApplicationType.PLUGIN   | new Options()                              || true
        '{ applicationType: [web, rest-api] }'                             | ApplicationType.REST_API | new Options()                              || true
        '{ applicationType: [web, rest-api] }'                             | ApplicationType.PLUGIN   | new Options()                              || false
        '{ option: { servlet: jetty } }'                                   | ApplicationType.WEB      | new Options()                              || false
        '{ option: { servlet: jetty } }'                                   | ApplicationType.WEB      | new Options().withServletImpl(ServletImpl.JETTY) || true
        '{ feature: gsp }'                                                 | ApplicationType.WEB      | new Options()                              || true
        '{ feature: gsp }'                                                 | ApplicationType.REST_API | new Options()                              || false
        '{ allOf: [{ group: database-driver }, { not: views-markup }] }'   | ApplicationType.WEB      | new Options()                              || true
        '{ not: { option: { data: hibernate5 } } }'                        | ApplicationType.WEB      | new Options()                              || false
        '{ not: views-markup }'                                            | ApplicationType.WEB      | new Options()                              || true
        '{ jdkAtLeast: 25 }'                                               | ApplicationType.WEB      | new Options()                              || false
        '{ jdkAtLeast: 25 }'                                               | ApplicationType.WEB      | new Options().withJavaVersion(JdkVersion.JDK_25) || true
    }

    void "a pack of Forge that is not a default is no default feature, and one that is the default of its group is both"() {
        expect:
        !(feature('') instanceof DefaultFeature)
        !(feature('') instanceof OneOfFeature)
        feature('default: true\n') instanceof DefaultFeature
        !(feature('default: true\n') instanceof OneOfFeature)
        feature('oneOf: acme-group\n') instanceof OneOfFeature
        !(feature('oneOf: acme-group\n') instanceof DefaultFeature)
        feature('oneOf: acme-group\ndefault: { not: { group: acme-group } }\n') instanceof OneOfFeature
        feature('oneOf: acme-group\ndefault: { not: { group: acme-group } }\n') instanceof DefaultFeature
    }

    void "a pack of Forge has the place among the features and the visibility its descriptor says"() {
        expect:
        feature('').order == FeaturePhase.DEFAULT.order
        feature('').visible
        feature('order: test\n').order == FeaturePhase.TEST.order
        feature('order: lowest\n').order == FeaturePhase.LOWEST.order
        !feature('visible: false\n').visible
    }

    void "a hidden pack of Forge is not selected by its name"() {
        when: 'the servlet container, which is such a pack'
        beanContext.getBean(ProjectGenerator).createGeneratorContext(ApplicationType.WEB, NameUtils.parse('example.grails.foo'), new Options(),
                OperatingSystem.LINUX, ['spring-boot-starter-tomcat'], ConsoleOutput.NOOP)

        then:
        FeatureSelectionException e = thrown()
        e.message == 'The requested feature does not exist: spring-boot-starter-tomcat'
    }

    void "a pack of Forge adds the features it names once the defaults are chosen, each when its condition holds"() {
        when:
        Generated generated = generate(pack('''\
adds:
  - database-migration
  - feature: views-markup
    when: { applicationType: rest-api }
  - feature: mysql
    when: { not: { group: database-driver } }
'''), ApplicationType.WEB, new Options(), [NAME])

        then: 'the one without a condition; not the one of another type; and not a driver, since the default one is there'
        generated.features.containsAll([NAME, 'database-migration', 'h2'])
        !generated.features.contains('views-markup')
        !generated.features.contains('mysql')
    }

    void "what a pack of Forge adds cannot be excluded"() {
        when:
        generate(pack('adds:\n  - database-migration\n'), ApplicationType.WEB, new Options(), [NAME], ['database-migration'])

        then:
        FeatureSelectionException e = thrown()
        e.message == "The feature database-migration cannot be excluded: the feature ${NAME} requires it"
    }

    void "what a pack of Forge adds is not what it requires: it is not among the features it says it depends on"() {
        expect:
        feature('adds:\n  - database-migration\n').dependentFeatures == []
        feature('requires:\n  - database-migration\n').dependentFeatures == ['database-migration']
    }

    void "a pack of Forge relies on the features it names for the types of application it names them for"() {
        given:
        PackFeature feature = feature('''\
reliesOn:
  - gsp
  - feature: asset-pipeline-grails
    when: { applicationType: web }
''')

        expect:
        feature.getFeaturesReliedOn(ApplicationType.WEB) == ['gsp', 'asset-pipeline-grails']
        feature.getFeaturesReliedOn(ApplicationType.WEB_PLUGIN) == ['gsp']
    }

    void "a feature that a pack of Forge relies on cannot be excluded beside it"() {
        when:
        generate(pack('reliesOn:\n  - mockito\n'), ApplicationType.WEB, new Options(), [NAME], ['mockito'])

        then:
        FeatureSelectionException e = thrown()
        e.message == "The feature mockito cannot be excluded: what the feature ${NAME} generates relies on it"
    }

    void "a pack of Forge that needs a JDK is refused with an earlier one"() {
        when:
        generate(pack('requiresJdk: 25\n'), ApplicationType.WEB, new Options(), [NAME])

        then:
        FeatureSelectionException e = thrown()
        e.message == "${NAME} requires JDK 25 or later (selected: JDK 21)."

        when:
        Generated generated = generate(pack('requiresJdk: 25\n'), ApplicationType.WEB, new Options().withJavaVersion(JdkVersion.JDK_25), [NAME])

        then:
        generated.features.contains(NAME)
    }

    @Unroll
    void "what a pack of Forge gives when a condition holds is there with #selected: #there"() {
        when:
        Generated generated = generate(pack('''\
dependencies:
  - scope: implementation
    coordinates: com.acme:acme-always
  - scope: implementation
    coordinates: com.acme:acme-markup
    when: { feature: views-markup }
buildPlugins:
  - id: com.acme.markup
    version: '1.0'
    when: { feature: views-markup }
files:
  - from: markup.txt
    to: markup.txt
    when: { feature: views-markup }
  - from: always.txt
    to: always.txt
configuration:
  - values:
      acme.always: true
  - when: { feature: views-markup }
    values:
      acme.markup: true
  - when: { not: views-markup }
    values:
      acme.plain: true
build:
  extensions:
    acme:
      - text: first = 1
      - when: { feature: views-markup }
        text: markup = 2
      - text: last = 3
''', ['markup.txt': 'markup', 'always.txt': 'always']), ApplicationType.WEB, new Options(), selected)

        then:
        generated.files['build.gradle'].contains('com.acme:acme-always')
        generated.files['build.gradle'].contains('com.acme:acme-markup') == there
        generated.files['build.gradle'].contains('id "com.acme.markup" version "1.0"') == there
        generated.files.containsKey('markup.txt') == there
        generated.files['always.txt'] == 'always'
        generated.files['grails-app/conf/application.yml'].contains('always: true')
        generated.files['grails-app/conf/application.yml'].contains('markup: true') == there
        generated.files['grails-app/conf/application.yml'].contains('plain: true') != there
        generated.files['build.gradle'].contains(there ? 'acme {\n    first = 1\n    markup = 2\n    last = 3\n}\n' : 'acme {\n    first = 1\n    last = 3\n}\n')

        where:
        selected               || there
        [NAME]                 || false
        [NAME, 'views-markup'] || true
    }

    void "the settings of a pack of Forge for the file of an environment are in that file"() {
        when:
        Generated generated = generate(pack('''\
configuration:
  - environmentFile: staging
    values:
      acme.staging: 1
  - environmentFile: test
    values:
      acme.tested: 2
'''), ApplicationType.WEB, new Options(), [NAME])

        then:
        generated.files['grails-app/conf/application-staging.yml'].contains('acme.staging: 1')
        generated.files['src/test/resources/application-test.yml'].contains('acme.tested: 2')
        !generated.files['grails-app/conf/application.yml'].contains('acme')
    }

    void "a pack of Forge can apply a build plugin as the plugins of Grails are applied, and add to the build of the build"() {
        when:
        String build = generate(pack('''\
buildPlugins:
  - id: com.acme.applied
    apply: true
dependencies:
  - scope: buildscript
    coordinates: com.acme:acme-gradle
    version: '1.0'
  - scope: testAndDevelopmentOnly
    coordinates: com.acme:acme-webjar
  - scope: integrationTestImplementationTestFixtures
    coordinates: com.acme:acme-fixtures
'''), ApplicationType.WEB, new Options(), [NAME]).files['build.gradle']

        then:
        build.contains('apply plugin: "com.acme.applied"')
        build.contains('classpath "com.acme:acme-gradle:1.0"')
        build.contains('testAndDevelopmentOnly "com.acme:acme-webjar"')
        build.contains('integrationTestImplementation testFixtures("com.acme:acme-fixtures")')
    }

    void "a pack of Forge can copy a file that looks like a template, and write one that is run"() {
        when:
        Generated generated = generate(pack('''\
files:
  - from: page.gsp
    to: grails-app/views/page.gsp
    template: false
  - from: run.sh
    to: run.sh
    executable: true
  - from: rendered.txt.gsp
    to: rendered.txt
''', ['page.gsp': '<g:if test="${x}">${y}</g:if>', 'run.sh': '#!/bin/sh\n', 'rendered.txt.gsp': '${project.name}']),
                ApplicationType.WEB, new Options(), [NAME])

        then:
        generated.files['grails-app/views/page.gsp'] == '<g:if test="${x}">${y}</g:if>'
        generated.files['rendered.txt'] == 'foo'
        generated.templates.find { it.path == 'run.sh' }.executable
        !generated.templates.find { it.path == 'rendered.txt' }.executable
    }

    void "the comments of a pack of Forge are in front of its build properties"() {
        when:
        String properties = generate(pack('''\
build:
  propertyComments:
    - ' Set it to false to turn it off'
  properties:
    acme.on: 'true'
'''), ApplicationType.WEB, new Options(), [NAME]).files['gradle.properties']

        then:
        properties.contains('# Set it to false to turn it off\nacme.on=true\n')
    }

    void "a build extension of a pack of Forge can have a call in its name"() {
        when:
        String build = generate(pack('''\
build:
  extensions:
    tasks.withType(Jar).configureEach: |
      archiveClassifier = 'acme'
'''), ApplicationType.WEB, new Options(), [NAME]).files['build.gradle']

        then:
        build.contains("tasks.withType(Jar).configureEach {\n    archiveClassifier = 'acme'\n}\n")
    }

    void "a Grails Data implementation takes the settings of the database driver before its own"() {
        when:
        String configuration = generate(pack('''\
dataSourceOfDriver: true
configuration:
  dataSource.acme: true
'''), ApplicationType.WEB, new Options(), [NAME, 'postgres']).files['grails-app/conf/application.yml']

        then: 'they are the same as those the Grails Data implementation of the application took, so they are there once'
        configuration.count('driverClassName: org.postgresql.Driver') == 1
        configuration.contains('acme: true')
    }

    void "a feature that takes the settings of the database driver is refused in an application without one"() {
        when:
        generate(pack('dataSourceOfDriver: true\n'), ApplicationType.WEB, new Options().withGormImpl(org.grails.forge.options.GormImpl.MONGODB), [NAME])

        then:
        IllegalStateException e = thrown()
        e.message == "The feature ${NAME} takes the data source of the database driver, and the application has no database driver"
    }

    private static Pack pack(String body, Map<String, String> files = [:]) {
        PackDescriptor descriptor = new PackDescriptorParser().parseOfForge(PackGenerationSpec.HEADER + "name: ${NAME}\n" + body, 'test')
        Map<String, byte[]> content = files.collectEntries { String from, String text -> [from, text.getBytes(StandardCharsets.UTF_8)] }
        return new Pack(descriptor, 'test', content, true)
    }

    private static PackFeature feature(String body) {
        return PackFeature.of(pack(body), { String name -> Optional.<Feature>empty() }, { -> null })
    }

    private Generated generate(Pack pack, ApplicationType type, Options options, List<String> selected, List<String> excluded = []) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(type, project, options, OperatingSystem.LINUX, selected, ConsoleOutput.NOOP,
                [pack], excluded)
        generator.generate(type, project, output, context)
        return new Generated(features: context.features.features*.name, files: output.getProject(), templates: context.templates.values().toList())
    }

    static class Generated {

        List<String> features
        Map<String, String> files
        List<Template> templates
    }
}
