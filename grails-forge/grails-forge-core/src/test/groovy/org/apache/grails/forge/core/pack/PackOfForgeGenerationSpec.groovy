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
import org.grails.forge.build.dependencies.LookupFailedException
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils

/**
 * What only a pack Forge has in itself can say, in the application it gives.
 */
class PackOfForgeGenerationSpec extends ApplicationContextSpec {

    void "a build plugin of a pack of Forge has the version Forge knows for its artifact"() {
        when:
        Map<String, String> output = generate(pack('''\
buildPlugins:
  - id: com.acme.shadow
    artifactOfForge: shadow-gradle-plugin
'''))

        then:
        output['build.gradle'] =~ /id "com\.acme\.shadow" version "\d[^"]*"/
    }

    void "a build plugin of a pack of Forge whose artifact Forge does not know is an error"() {
        when:
        generate(pack('''\
buildPlugins:
  - id: com.acme.shadow
    artifactOfForge: no-such-artifact-of-forge
'''))

        then:
        thrown(LookupFailedException)
    }

    void "the templates of a pack of Forge have the versions Forge knows, and the ones the pack gives where it knows none"() {
        when:
        Map<String, String> output = generate(pack('''\
versionsOfForge:
  shadow-gradle-plugin: '0.0.1'
  no-such-artifact-of-forge: '0.0.2'
files:
  - from: versions.txt.gsp
    to: versions.txt
''', ['versions.txt.gsp': 'known ${versionsOfForge[\'shadow-gradle-plugin\']}\nunknown ${versionsOfForge[\'no-such-artifact-of-forge\']}\n']))

        then:
        output['versions.txt'] =~ /known \d[^\n]*\nunknown 0\.0\.2\n/
        !output['versions.txt'].contains('0.0.1')
    }

    void "a template of a pack of Forge that asks for a version the pack does not name fails"() {
        when:
        generate(pack('''\
versionsOfForge:
  shadow-gradle-plugin: '0.0.1'
files:
  - from: versions.txt.gsp
    to: versions.txt
''', ['versions.txt.gsp': '${versionsOfForge.asciidoctorj}']))

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("There is no 'versionsOfForge.asciidoctorj' for a template")
    }

    void "a template of a pack that names no versions has no such name"() {
        when:
        generate(pack('''\
files:
  - from: versions.txt.gsp
    to: versions.txt
''', ['versions.txt.gsp': '${versionsOfForge}']))

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("There is no 'versionsOfForge' for a template")
    }

    void "the blocks of a pack of Forge are in the build file at their places, as they are"() {
        when:
        String build = generate(pack('''\
build:
  blocks:
    end: |
      tasks.register('acmeEnd') {
          group = 'acme'
      }
    afterPlugins: 'apply from: "gradle/acme.gradle"'
    afterDependencies: "ext.acme = 'after dependencies'"
'''))['build.gradle']

        then:
        build.contains('\napply from: "gradle/acme.gradle"\n\nrepositories {')
        build.contains("\next.acme = 'after dependencies'\n\n")
        build.endsWith("\ntasks.register('acmeEnd') {\n    group = 'acme'\n}\n\n")

        and:
        build.indexOf('plugins {') < build.indexOf('apply from: "gradle/acme.gradle"')
        build.indexOf('dependencies {') < build.indexOf('ext.acme')
        build.indexOf('ext.acme') < build.indexOf('tasks.register(\'acmeEnd\')')
    }

    void "the links of a pack of Forge are in the readme"() {
        when:
        Map<String, String> output = generate(pack('''\
readmeLinks:
  - label: Acme Audit
    href: https://acme.example/audit
  - label: Acme Trail
    href: https://acme.example/trail?a=1&b=2
'''))

        then:
        output['README.md'].contains('- [Acme Audit](https://acme.example/audit)')
        output['README.md'].contains('- [Acme Trail](https://acme.example/trail?a=1&b=2)')
    }

    private static Pack pack(String body, Map<String, String> files = [:]) {
        PackDescriptor descriptor = new PackDescriptorParser().parseOfForge(PackGenerationSpec.HEADER + 'name: acme-of-forge\n' + body, 'test')
        Map<String, byte[]> content = files.collectEntries { String from, String text -> [from, text.getBytes(StandardCharsets.UTF_8)] }
        return new Pack(descriptor, 'test', content, true)
    }

    private Map<String, String> generate(Pack pack) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                [pack.descriptor.name], ConsoleOutput.NOOP, [pack])
        generator.generate(ApplicationType.WEB, project, output, context)
        return output.getProject()
    }
}
