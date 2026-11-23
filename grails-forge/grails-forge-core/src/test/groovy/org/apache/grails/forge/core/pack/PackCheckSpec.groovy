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
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The check of a feature pack its author runs before the pack is published, which the Grails Gradle
 * plugin starts in a JVM of its own.
 */
class PackCheckSpec extends Specification {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n'
    static final String COMPATIBILITY = "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"
    static final String TEMPLATE = 'files:\n  - from: Audit.groovy.gsp\n    to: src/main/groovy/{packagePath}/Audit.groovy\n'

    @TempDir
    Path directory

    void "a pack that can be used has no problems, and checking it writes nothing"() {
        given:
        Path pack = pack(TEMPLATE + '  - from: conf/audit.txt\n    to: grails-app/conf/audit.txt\n', [
                'Audit.groovy.gsp': 'package ${project.packageName}\n// ${project.name} ${project.className} ${project.packagePath} ' +
                        '${project.naturalName} ${project.propertyName} ${features} ${applicationType} ${options.jdk} ${options.data} ' +
                        '${options.servlet} ${options.reloading} ${grailsVersion} ${pack.name}\n',
                'conf/audit.txt'  : 'as it is ${not.a.template}',
        ])
        List<String> before = filesBelow(directory)

        expect:
        PackCheck.check(pack) == []

        and:
        filesBelow(directory) == before
    }

    @Unroll
    void "a descriptor that #problem is a problem of the pack"() {
        given:
        Path pack = pack('', [:])
        Files.writeString(pack.resolve('feature.yml'), descriptor)

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 1
        problems[0].startsWith(start)

        where:
        problem                              | descriptor                                                                       || start
        'has a key there is not'             | HEADER + COMPATIBILITY + 'colour: red\n'                                         || "the descriptor: has the key 'colour', which this version of Forge does not read here"
        'has no name'                        | 'format: 1\nkind: feature\ntemplateApi: 1\n' + COMPATIBILITY                     || 'name: '
        'writes outside the application'     | HEADER + COMPATIBILITY + 'files:\n  - from: a.txt\n    to: ../a.txt\n'           || 'files[0].to: '
        'is for versions that are not ones'  | HEADER + "compatibility:\n  grails: 'nine'\n  forge: '[9.0.0,10.0.0)'\n"         || 'compatibility.grails: '
        'names a scope there is not'         | HEADER + COMPATIBILITY + 'dependencies:\n  - scope: api\n    coordinates: a:b\n' || 'dependencies[0].scope: '
        'is not YAML'                        | 'name: [\n'                                                                      || 'It is not YAML this version of Forge can read: '
        'is not a mapping'                   | '- a\n- b\n'                                                                     || 'It must be a mapping of keys to values'
    }

    void "every problem of a descriptor is given, each on a line of its own"() {
        given:
        Path pack = pack('', [:])
        Files.writeString(pack.resolve('feature.yml'), HEADER + COMPATIBILITY + 'colour: red\nshape: round\nfiles:\n  - from: a.txt\n    to: /etc/a.txt\n')

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 3
        problems.every { String problem -> !problem.contains('\n') }
        problems.any { String problem -> problem.startsWith("the descriptor: has the key 'colour'") }
        problems.any { String problem -> problem.startsWith("the descriptor: has the key 'shape'") }
        problems.any { String problem -> problem.startsWith('files[0].to:') }
    }

    void "a directory without a descriptor is not a pack"() {
        expect:
        PackCheck.check(Files.createDirectories(directory.resolve('empty'))) == ['There is no feature.yml in the directory, or it is not a file']
    }

    void "a file the descriptor names and the pack has not is a problem"() {
        given:
        Path pack = pack('files:\n  - from: missing.txt\n    to: missing.txt\n', [:])

        expect:
        PackCheck.check(pack) == ["files[0].from: 'missing.txt' is not a file under templates of the pack, or is a link or is reached through one"]
    }

    @IgnoreIf({ os.windows })
    void "a file of the pack that is a link is a problem"() {
        given:
        Path pack = pack('files:\n  - from: link.txt\n    to: link.txt\n', [:])
        Path elsewhere = Files.writeString(directory.resolve('elsewhere.txt'), 'elsewhere')
        Files.createSymbolicLink(pack.resolve('templates/link.txt'), elsewhere)

        expect:
        PackCheck.check(pack) == ["files[0].from: 'link.txt' is not a file under templates of the pack, or is a link or is reached through one"]
    }

    void "a template that is not text is a problem"() {
        given:
        Path pack = pack(TEMPLATE, [:])
        Files.write(pack.resolve('templates/Audit.groovy.gsp'), [0xC3, 0x28] as byte[])

        expect:
        PackCheck.check(pack) == ["files[0].from: 'Audit.groovy.gsp' is a template and is not text in UTF-8"]
    }

    @Unroll
    void "a template that #problem is a problem of the pack"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': source])

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 1
        problems[0].startsWith('files[0].from: The template Audit.groovy.gsp cannot be rendered: ')
        problems[0].contains(message)
        !problems[0].contains('\n')

        where:
        problem                                  | source                                                   || message
        'does not compile'                       | 'class Audit {\n<% if (true) { %>\n    unclosed\n}\n'    || 'for an application with applicationType web'
        'has code that is not Groovy'            | '<% int = = 3 %>'                                        || 'for an application with applicationType web'
        'names what the template API has not'    | 'package ${packageName}'                                 || "There is no 'packageName' for a template: template API 1 has "
        'names what the project has not'         | 'package ${project.pakageName}'                          || "There is no 'project.pakageName' for a template: template API 1 has "
        'names an option there is not'           | '${options.database}'                                    || "There is no 'options.database' for a template"
        'calls what there is not'                | '${features.nope()}'                                     || 'nope'
        'throws'                                 | '<% throw new IllegalStateException("no\\nway") %>'      || 'no way'
    }

    @Unroll
    void "a template that #problem is named with the line of it the problem is on"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': source])

        expect:
        PackCheck.check(pack) == ["files[0].from: The template Audit.groovy.gsp cannot be rendered: ${says}".toString()]

        where:
        problem                          | source                                                                    || says
        'throws'                         | '<%-- a\n header --%>\npackage a\n<% throw new IllegalStateException("no") %>\n' || 'line 4: no (for an application with applicationType web, options [data:hibernate5, jdk:' + org.grails.forge.options.JdkVersion.DEFAULT_OPTION.majorVersion() + ', reloading:devtools, servlet:tomcat])'
        'has code that is not Groovy'    | 'package a\n\n<% int = = 3 %>\n'                                          || 'line 3: it does not compile: ' + "Unexpected input: '='" + ' (for an application with applicationType web, options [data:hibernate5, jdk:' + org.grails.forge.options.JdkVersion.DEFAULT_OPTION.majorVersion() + ', reloading:devtools, servlet:tomcat])'
    }

    void "a template is compiled once for a check, and rendered for every application that is made up"() {
        given: 'a template that has the class of its page remembered each time it is rendered'
        String seen = 'org.apache.grails.forge.core.pack.PackCheckSpec.seen'
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': "<% System.properties.computeIfAbsent('${seen}') { [] } << getClass() %>x".toString()])

        when:
        List<String> problems = PackCheck.check(pack)
        List<Class<?>> classes = System.properties[seen] as List<Class<?>>

        then:
        problems == []
        classes.size() > 1
        classes.every { it.is(classes[0]) }

        cleanup:
        System.properties.remove(seen)
    }

    @Unroll
    void "a template that fails only for #what fails the check, which says for what"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': source])

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 1
        problems[0].contains("There is no 'nope' for a template")
        problems[0].contains(sample)

        where:
        what                           | source                                                         || sample
        'one Grails Data'              | '<% if (options.data == "neo4j") { %>${nope}<% } %>'           || 'data:neo4j'
        'one servlet container'        | '<% if (options.servlet == "undertow") { %>${nope}<% } %>'     || 'servlet:undertow'
        'one way of reloading'         | '<% if (options.reloading == "jrebel") { %>${nope}<% } %>'     || 'reloading:jrebel'
        'one type of application'      | '<% if (applicationType == "web-plugin") { %>${nope}<% } %>'   || 'applicationType web-plugin'
    }

    void "a template is checked for the types of application the descriptor names and no other"() {
        given:
        Path pack = pack('applicationTypes: [web, rest-api]\n' + TEMPLATE,
                ['Audit.groovy.gsp': '<% if (applicationType in ["plugin", "web-plugin"]) { %>${nope}<% } %>'])

        expect:
        PackCheck.check(pack) == []
    }

    void "a template has the feature of the pack and those it requires, as an application with it has"() {
        given:
        Path pack = pack('requires: [database-migration]\n' + TEMPLATE,
                ['Audit.groovy.gsp': '<% assert features == ["acme-audit", "database-migration"] %>'])

        expect:
        PackCheck.check(pack) == []
    }

    void "each template with a problem is named once, by the place the descriptor names it first"() {
        given:
        Path pack = pack('files:\n  - from: a.txt.gsp\n    to: a.txt\n  - from: fine.txt.gsp\n    to: fine.txt\n' +
                '  - from: b.txt.gsp\n    to: b.txt\n  - from: a.txt.gsp\n    to: again.txt\n',
                ['a.txt.gsp': '${nope}', 'fine.txt.gsp': '${pack.name}', 'b.txt.gsp': '<% if (true) { %>'])

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 2
        problems[0].startsWith('files[0].from: The template a.txt.gsp cannot be rendered: ')
        problems[1].startsWith('files[2].from: The template b.txt.gsp cannot be rendered: ')
    }

    @Unroll
    void "a pack that is not for the #of that checks it is a problem"() {
        given:
        Path pack = pack('', [:])
        Files.writeString(pack.resolve('feature.yml'), HEADER + "compatibility:\n  grails: '${grails}'\n  forge: '${forge}'\n")

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 1
        problems[0].startsWith("compatibility.${key}: the pack is for ${of} [1.0.0,2.0.0), and it is checked with ${of} ")

        where:
        of       | key      | grails           | forge
        'Grails' | 'grails' | '[1.0.0,2.0.0)'  | '[9.0.0,10.0.0)'
        'Forge'  | 'forge'  | '[9.0.0,10.0.0)' | '[1.0.0,2.0.0)'
    }

    void "the problems are written one on a line, and the result says there are some"() {
        given:
        Path pack = pack(TEMPLATE + 'colour: red\n', ['Audit.groovy.gsp': 'x'])
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()

        when:
        int result = PackCheck.run([pack.toString()] as String[], new PrintStream(out, true, 'UTF-8'), new PrintStream(err, true, 'UTF-8'))

        then:
        result == PackCheck.PROBLEMS
        out.toString('UTF-8').readLines().size() == 1
        out.toString('UTF-8').startsWith("the descriptor: has the key 'colour'")
        err.toString('UTF-8') == ''
    }

    void "a pack that can be used is said to be checked, with how much of it was"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': 'package ${project.packageName}'])
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()

        when:
        int result = PackCheck.run([pack.toString()] as String[], new PrintStream(out, true, 'UTF-8'), new PrintStream(err, true, 'UTF-8'))

        then:
        result == PackCheck.VALID
        out.toString('UTF-8').readLines() == ['feature pack checked: templates=1 applications=5']
        err.toString('UTF-8') == ''
    }

    void "the Grails Gradle plugin starts this class, and reads what it writes for a pack that can be used"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': 'package ${project.packageName}'])
        ByteArrayOutputStream out = new ByteArrayOutputStream()

        when:
        PackCheck.run([pack.toString()] as String[], new PrintStream(out, true, 'UTF-8'), new PrintStream(new ByteArrayOutputStream(), true, 'UTF-8'))

        then:
        PackCheck.name == System.getProperty('forge.packCheck.mainClassOfGradlePlugin')
        out.toString('UTF-8').readLines().size() == 1
        out.toString('UTF-8').readLines()[0] ==~ System.getProperty('forge.packCheck.checkedLineOfGradlePlugin')
        [PackCheck.VALID, PackCheck.PROBLEMS, PackCheck.NOT_CHECKED] == [0, 1, 2]
    }

    void "a descriptor that cannot be used hides what is wrong with the templates, which it names"() {
        given:
        Path pack = pack(TEMPLATE + 'colour: red\n', ['Audit.groovy.gsp': '${nope}'])

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 1
        problems[0].startsWith("the descriptor: has the key 'colour'")
    }

    void "every version of Java an application can be for is in an application a template is rendered for"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': '<% if (options.jdk == ' + jdk + ') { %>${nope}<% } %>'])

        when:
        List<String> problems = PackCheck.check(pack)

        then:
        problems.size() == 1
        problems[0].contains("jdk:${jdk}")

        where:
        jdk << org.grails.forge.options.JdkVersion.values()*.majorVersion()
    }

    void "the applications a template is rendered for are the ones the guide lists"() {
        given:
        PackDescriptor descriptor = new PackLoader().fromDirectory(pack('', [:])).descriptor

        expect:
        PackModel.samples(descriptor).keySet().toList() == [
                'applicationType web, options [data:hibernate5, jdk:21, reloading:devtools, servlet:tomcat]',
                'applicationType rest-api, options [data:hibernate7, jdk:25, reloading:jrebel, servlet:jetty]',
                'applicationType web-plugin, options [data:mongodb, jdk:26, reloading:none, servlet:undertow]',
                'applicationType plugin, options [data:neo4j, jdk:21, reloading:devtools, servlet:none]',
                'applicationType rest-api-plugin, options [data:hibernate5, jdk:25, reloading:jrebel, servlet:tomcat]',
        ]
    }

    void "a pack is checked in production, whatever environment the build that starts the check gives its JVM"() {
        given: 'a template that says the environment it is rendered in'
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': '<% System.out.println("environment " + grails.util.Environment.current.name) %>package ${project.packageName}'])

        when: 'the JVM is given the environment of a project, as GRAILS_OPTS or a build can give it'
        Map<String, Object> result = forkWithJvmArguments(['-Dgrails.env=development'], pack.toString())

        then: 'the environment is the one of Forge, which renders the templates of a pack as the command line does'
        result.exit == PackCheck.VALID
        ((String) result.err).readLines().findAll { String line -> line.startsWith('environment ') }.unique() == ['environment production']
    }

    void "a template that ends the JVM does not make its pack one that was checked"() {
        given:
        Path pack = pack(TEMPLATE, ['Audit.groovy.gsp': '<% System.exit(0) %>${nope}'])

        when:
        Map<String, Object> result = fork(pack.toString())

        then:
        result.exit == 0
        result.out == []
    }

    @Unroll
    void "#arguments are not a pack to check"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()

        when:
        int result = PackCheck.run(arguments as String[], new PrintStream(out, true, 'UTF-8'), new PrintStream(err, true, 'UTF-8'))

        then:
        result == PackCheck.NOT_CHECKED
        out.toString('UTF-8') == ''
        err.toString('UTF-8').readLines() == [message]

        where:
        arguments    || message
        []           || 'Give the directory of the feature pack to check, and nothing else'
        ['a', 'b']   || 'Give the directory of the feature pack to check, and nothing else'
        ['']         || 'Give the directory of the feature pack to check, and nothing else'
        ['a\u0000b'] || 'The directory of the feature pack is not a path'
    }

    void "a JVM that has only what Forge needs to run checks a pack, and ends with what it found"() {
        given: 'the class path of this module without its tests, which is what the Grails Gradle plugin resolves'
        Path valid = pack(TEMPLATE, ['Audit.groovy.gsp': '<% System.out.println("said by the template") %>package ${project.packageName}'], 'valid')
        Path invalid = pack(TEMPLATE + 'colour: red\n', ['Audit.groovy.gsp': 'x'], 'invalid')
        Path template = pack(TEMPLATE, ['Audit.groovy.gsp': 'package ${project.pakageName}'], 'template')

        when:
        Map<String, Object> ofValid = fork(valid.toString())
        Map<String, Object> ofInvalid = fork(invalid.toString())
        Map<String, Object> ofTemplate = fork(template.toString())
        Map<String, Object> ofNothing = fork()

        then: 'what a template says is not taken for a problem'
        ofValid.exit == PackCheck.VALID
        ofValid.out == ['feature pack checked: templates=1 applications=5']
        ofValid.err.contains('said by the template')

        and:
        ofInvalid.exit == PackCheck.PROBLEMS
        ((List<String>) ofInvalid.out).size() == 1
        ((List<String>) ofInvalid.out)[0].startsWith("the descriptor: has the key 'colour'")

        and:
        ofTemplate.exit == PackCheck.PROBLEMS
        ((List<String>) ofTemplate.out).size() == 1
        ((List<String>) ofTemplate.out)[0].contains("There is no 'project.pakageName' for a template")

        and:
        ofNothing.exit == PackCheck.NOT_CHECKED
        ofNothing.out == []
    }

    private Map<String, Object> fork(String... arguments) {
        return forkWithJvmArguments([], arguments)
    }

    private Map<String, Object> forkWithJvmArguments(List<String> jvmArguments, String... arguments) {
        String classpath = Files.readAllLines(Path.of(System.getProperty('forge.packCheck.classpathFile'))).join(File.pathSeparator)
        // in a file, since a class path can be longer than a command line
        Path options = Files.createTempFile(directory, 'java', '.args')
        Files.writeString(options, '-cp\n"' + classpath.replace('\\', '\\\\') + '"\n')
        List<String> command = [Path.of(System.getProperty('java.home'), 'bin', 'java').toString(), "@${options}".toString()]
        command.addAll(jvmArguments)
        command.add(PackCheck.name)
        command.addAll(arguments)
        Path out = Files.createTempFile(directory, 'out', '.txt')
        Path err = Files.createTempFile(directory, 'err', '.txt')
        Process process = new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(err.toFile()).start()
        assert process.waitFor(120, TimeUnit.SECONDS)
        return [exit: process.exitValue(), out: Files.readAllLines(out, StandardCharsets.UTF_8), err: Files.readString(err, StandardCharsets.UTF_8)]
    }

    private Path pack(String body, Map<String, String> files, String name = 'pack') {
        Path packDirectory = Files.createDirectories(directory.resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), HEADER + COMPATIBILITY + body)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text ->
            Path file = packDirectory.resolve('templates').resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, text)
        }
        return packDirectory
    }

    private static List<String> filesBelow(Path root) {
        return Files.walk(root).withCloseable { paths ->
            paths.map { Path path -> root.relativize(path).toString() + ' ' + (Files.isRegularFile(path) ? Files.size(path) : 'directory') }
                    .sorted().collect(Collectors.toList())
        }
    }
}
