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

/**
 * The template API a pack declares: which ones this Forge renders, and that the names of the one
 * there is are those its documentation gives, in the class and in the guide.
 */
class PackTemplateApiSpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    void "the template APIs this Forge renders are numbered from 1, and the latest is the one a new pack names"() {
        expect:
        PackModel.APIS == [1]
        PackDescriptor.TEMPLATE_API == PackModel.APIS.max()
    }

    void "a descriptor has the template API it declares"() {
        expect:
        new PackDescriptorParser().parse(PackGenerationSpec.HEADER + 'name: acme-audit\n', 'test').templateApi == 1
    }

    @Unroll
    void "a pack written against template API #declared, which this Forge does not render, is refused with the ones it renders"() {
        given:
        Path packDirectory = Files.createDirectories(directory.resolve('acme/templates')).parent
        Files.writeString(packDirectory.resolve('feature.yml'),
                PackGenerationSpec.HEADER.replace('templateApi: 1', "templateApi: ${declared}") + 'name: acme-audit\n')

        when:
        new PackLoader().fromDirectory(packDirectory)

        then:
        PackDescriptorException e = thrown()
        e.problems == ["templateApi: must be one of the template APIs this version of Forge renders, [1], but is the number ${declared}".toString()]

        where:
        declared << [2, 3, 0, -1, 2147483647]
    }

    void "a template is given the names of the template API its pack declares, and is told which API that is"() {
        when:
        generate(['acme-audit'], pack(['t.txt.gsp': '${nope}']))

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("There is no 'nope' for a template: template API 1 has [applicationType, features, grailsVersion, options, pack, parameters, project]")

        when:
        generate(['acme-audit'], pack(['t.txt.gsp': '${options.nope}']))

        then:
        e = thrown()
        e.message.contains("There is no 'options.nope' for a template: template API 1 has [options.data, options.jdk, options.reloading, options.servlet]")
    }

    @IgnoreIf(value = { !RepositoryFiles.hasGuide() }, reason = 'these sources are no checkout of Grails, and the guide is not beside Forge in them')
    void "the names of template API 1 are those the class and the guide say it has"() {
        given: 'the names a template is given'
        String nested = generate(['acme-audit'], pack(['t.txt.gsp':
                '${project.keySet().collect { "project.$it" }.join(",")},${options.keySet().collect { "options.$it" }.join(",")},' +
                        '${pack.keySet().collect { "pack.$it" }.join(",")}']))['t.txt']
        Set<String> names = nested.split(',') as Set<String>
        try {
            generate(['acme-audit'], pack(['t.txt.gsp': '${nope}']))
        } catch (IllegalArgumentException e) {
            String top = e.message.find(/template API 1 has \[([^\]]*)\]/) { String all, String list -> list }
            names.addAll(top.split(', ').findAll { !(it in ['project', 'options', 'pack']) })
        }

        and: 'the table of the class, and the table of the guide'
        String javadoc = RepositoryFiles.packModel().getText('UTF-8')
        Set<String> ofClass = (javadoc =~ /<tr><td>\{@code ([\w.]+)\}<\/td>/).collect { List<String> match -> match[1] } as Set<String>
        String guide = RepositoryFiles.guide().getText('UTF-8')
        String table = guide.substring(guide.indexOf('|Name |Value'))
        table = table.substring(0, table.indexOf('|==='))
        // a row is the names, then what they are, with an empty line before it
        Set<String> ofGuide = table.split(/\r?\n\r?\n/).drop(1)
                .collectMany { String row -> (row.readLines()[0] =~ /`([\w.]+)`/).collect { List<String> match -> match[1] } } as Set<String>

        and: 'the names of the applications that are made up for the check of a pack'
        PackDescriptor descriptor = new PackDescriptorParser().parse(PackGenerationSpec.HEADER + 'name: acme-audit\n', 'test')
        List<Set<String>> ofSamples = PackModel.samples(descriptor).values().collect { Map<String, Object> sample ->
            sample.collectMany { String name, Object value ->
                // the parameters of a pack that declares none are a name without names below it
                value instanceof Map && !((Map) value).isEmpty() ? ((Map) value).keySet().collect { "${name}.${it}".toString() } : [name]
            } as Set<String>
        }

        expect:
        names.size() == 15
        ofClass == names
        ofGuide == names
        ofSamples.size() > 1
        ofSamples.every { it == names }
    }

    void "a model that is made up for a check tells a template which template API it is of"() {
        given:
        PackDescriptor descriptor = new PackDescriptorParser().parse(PackGenerationSpec.HEADER + 'name: acme-audit\n', 'test')

        when:
        ((Map) PackModel.samples(descriptor).values().first().get('options')).get('nope')

        then:
        MissingPropertyException e = thrown()
        e.message == "There is no 'options.nope' for a template: template API 1 has [options.data, options.jdk, options.reloading, options.servlet]"
    }

    private Pack pack(Map<String, String> files) {
        Path packDirectory = Files.createDirectories(directory.resolve("pack${Files.list(directory).withCloseable { it.count() }}"))
        Files.writeString(packDirectory.resolve('feature.yml'), PackGenerationSpec.HEADER + 'name: acme-audit\n' +
                'files:\n' + files.keySet().collect { String from -> "  - from: ${from}\n    to: ${from - '.gsp'}\n" }.join(''))
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text -> Files.writeString(packDirectory.resolve('templates').resolve(path), text) }
        return new PackLoader().fromDirectory(packDirectory)
    }

    private Map<String, String> generate(List<String> features, Pack pack) {
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        Project project = NameUtils.parse('example.grails.foo')
        MapOutputHandler output = new MapOutputHandler()
        GeneratorContext context = generator.createGeneratorContext(ApplicationType.WEB, project, new Options(), OperatingSystem.LINUX,
                new ArrayList<String>(features), ConsoleOutput.NOOP, [pack])
        generator.generate(ApplicationType.WEB, project, output, context)
        return output.getProject()
    }
}
