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
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The applications Forge generates with the feature pack of a directory for the author of the pack to
 * have built, which the Grails Gradle plugin starts in a JVM of its own.
 */
class PackFixturesSpec extends Specification {

    static final String HEADER = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n"
    static final String FILES = 'files:\n  - from: Audit.groovy.gsp\n    to: src/main/groovy/{packagePath}/Audit.groovy\n'
    static final Map<String, String> TEMPLATES = ['Audit.groovy.gsp': 'package ${project.packageName}\n\n// ${applicationType} ${options.data}\nclass Audit {\n}\n']

    @TempDir
    Path directory

    void "without fixtures an application is generated for each type the pack is for, with the feature of the pack"() {
        given:
        Path pack = pack(FILES, TEMPLATES)
        Path output = directory.resolve('fixtures')

        when:
        Map<String, Object> result = run(pack.toString(), output.toString())

        then:
        result.exit == PackFixtures.GENERATED
        result.out == ['generated web', 'generated rest-api', 'generated web-plugin', 'generated plugin', 'generated rest-api-plugin',
                       'feature pack fixtures: generated=5 refused=0']

        and: 'each is a whole application of its type, in the directory of the fixture, with the file of the pack'
        ['web', 'rest-api', 'web-plugin', 'plugin', 'rest-api-plugin'].collectMany { String type ->
            Path application = output.resolve(type)
            List<String> missing = ['build.gradle', 'settings.gradle', 'gradle/wrapper/gradle-wrapper.properties', 'gradlew.bat',
                                    'src/main/groovy/com/example/Audit.groovy', '.grails/project.yml']
                    .findAll { String file -> !Files.isRegularFile(application.resolve(file)) }
                    .collect { String file -> "${type}: ${file}".toString() }
            if (missing.isEmpty() && !Files.readString(application.resolve('src/main/groovy/com/example/Audit.groovy')).contains("// ${type} hibernate5")) {
                missing << "${type}: the type and the Grails Data of the application in the file of the pack".toString()
            }
            if (missing.isEmpty() && !Files.readString(application.resolve('.grails/project.yml')).contains('acme-audit')) {
                missing << "${type}: the feature of the pack among those of the application".toString()
            }
            missing
        } == []

        and: 'which differ as the types do'
        Files.exists(output.resolve('web/grails-app/views'))
        !Files.exists(output.resolve('rest-api/grails-app/views/layouts'))
        Files.list(output.resolve('plugin/src/main/groovy/com/example')).withCloseable { it.anyMatch { Path file -> file.fileName.toString().endsWith('GrailsPlugin.groovy') } }

        and: 'what the generator says is not among the lines of the fixtures'
        !result.out.any { String line -> line.contains('Feature pack') }
    }

    @IgnoreIf({ os.windows })
    void "an application is generated with the wrapper it is built with, which can be started"() {
        given:
        Path output = directory.resolve('fixtures')

        when:
        run(pack('applicationTypes: [web]\n' + FILES, TEMPLATES).toString(), output.toString())

        then:
        Files.isExecutable(output.resolve('web/gradlew'))
        Files.isRegularFile(output.resolve('web/gradlew.bat'))
    }

    void "an application that is generated without a settings file gets one that names it, and one that has a settings file keeps it"() {
        given:
        Path output = directory.resolve('fixtures')
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), 'plain web\nwith-settings web features=gradle-settings-file\n')

        when:
        Map<String, Object> result = run(pack(FILES, TEMPLATES).toString(), output.toString(), fixtures.toString())

        then: 'so that it is a build of its own where it is, below the build of the plugin'
        result.exit == PackFixtures.GENERATED
        Files.readString(output.resolve('plain/settings.gradle')).readLines().last() == "rootProject.name = 'shop'"
        Files.readString(output.resolve('plain/settings.gradle')).readLines().size() == 2

        and:
        Files.readString(output.resolve('with-settings/settings.gradle')).contains('rootProject.name = "shop"')
        !Files.readString(output.resolve('with-settings/settings.gradle')).contains('Written for the fixture')
    }

    void "the fixtures of a pack that names its types of application are of those types"() {
        given:
        Path output = directory.resolve('fixtures')

        when:
        Map<String, Object> result = run(pack('applicationTypes: [rest-api, web]\n' + FILES, TEMPLATES).toString(), output.toString())

        then:
        result.exit == PackFixtures.GENERATED
        result.out == ['generated web', 'generated rest-api', 'feature pack fixtures: generated=2 refused=0']
        Files.list(output).withCloseable { it.count() } == 2
    }

    void "the fixtures of a list are generated with their options and features"() {
        given:
        Path output = directory.resolve('fixtures')
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), '# what the build asked for\n\n' +
                'mongo rest-api data=mongodb jdk=25\n' +
                'jetty web servlet=jetty reloading=none features=database-migration,views-json\n')

        when:
        Map<String, Object> result = run(pack(FILES, TEMPLATES).toString(), output.toString(), fixtures.toString())

        then:
        result.exit == PackFixtures.GENERATED
        result.out == ['generated mongo', 'generated jetty', 'feature pack fixtures: generated=2 refused=0']

        and:
        Files.readString(output.resolve('mongo/src/main/groovy/com/example/Audit.groovy')).contains('// rest-api mongodb')
        Files.readString(output.resolve('mongo/build.gradle')).contains('mongodb')
        Files.readString(output.resolve('mongo/.grails/project.yml')).contains('acme-audit')

        and:
        Files.readString(output.resolve('jetty/build.gradle')).contains('spring-boot-starter-jetty')
        Files.readString(output.resolve('jetty/build.gradle')).contains('grails-data-hibernate5-dbmigration')
        !Files.readString(output.resolve('jetty/build.gradle')).contains('spring-boot-devtools')
    }

    void "a fixture of a type the pack is not for is refused, and the others are generated"() {
        given:
        Path output = directory.resolve('fixtures')
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), 'one web\ntwo plugin\n')

        when:
        Map<String, Object> result = run(pack('applicationTypes: [web, rest-api]\n' + FILES, TEMPLATES).toString(), output.toString(), fixtures.toString())

        then:
        result.exit == PackFixtures.REFUSED
        result.out == ['generated one',
                       'refused two: The feature pack acme-audit is not for an application of the type plugin: its descriptor names [web, rest-api]',
                       'feature pack fixtures: generated=1 refused=1']
        Files.isRegularFile(output.resolve('one/build.gradle'))
        !Files.exists(output.resolve('two'))
    }

    @Unroll
    void "a fixture the generator refuses is said with what the generator says: #refusal"() {
        given:
        Path output = directory.resolve('fixtures')
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), "odd ${fixture}\nfine web\n")

        when:
        Map<String, Object> result = run(pack(body, templates).toString(), output.toString(), fixtures.toString())
        List<String> out = result.out as List<String>

        then:
        result.exit == PackFixtures.REFUSED
        out.size() == 3
        out[0].startsWith('refused odd: ')
        out[0].contains(says)
        !Files.exists(output.resolve('odd'))

        and:
        out[1..2] == [others, summary]

        where:
        refusal                                | fixture                           | body                                 | templates                           || says                    | others           | summary
        'a feature there is not'               | 'web features=no-such-feature'    | FILES                                | TEMPLATES                           || 'no-such-feature'       | 'generated fine' | 'feature pack fixtures: generated=1 refused=1'
        'an option the pack conflicts with'    | 'web data=mongodb'                | 'conflicts: [gorm-mongodb]\n' + FILES | TEMPLATES                           || 'gorm-mongodb'          | 'generated fine' | 'feature pack fixtures: generated=1 refused=1'
        'a template that fails for it'         | 'rest-api'                        | FILES                                | ['Audit.groovy.gsp': '<% if (applicationType == "rest-api") { %>${nope}<% } %>'] || "There is no 'nope' for a template" | 'generated fine' | 'feature pack fixtures: generated=1 refused=1'
    }

    void "a pack that cannot be read has its problems said, and nothing is generated"() {
        given:
        Path output = directory.resolve('fixtures')

        when:
        Map<String, Object> result = run(pack('colour: red\nfiles:\n  - from: a.txt\n    to: /etc/a.txt\n', [:]).toString(), output.toString())
        List<String> out = result.out as List<String>

        then:
        result.exit == PackFixtures.REFUSED
        out.size() == 3
        out.any { String line -> line.startsWith("pack: the descriptor: has the key 'colour'") }
        out.any { String line -> line.startsWith('pack: files[0].to:') }
        out.last() == 'feature pack fixtures: generated=0 refused=0'
        !Files.exists(output)
    }

    void "a pack that is not for the Grails that generates is refused for every fixture"() {
        given:
        Path pack = pack(FILES, TEMPLATES)
        Files.writeString(pack.resolve('feature.yml'), HEADER.replace("grails: '[9.0.0,10.0.0)'", "grails: '[1.0.0,2.0.0)'") + 'applicationTypes: [web]\n' + FILES)

        when:
        Map<String, Object> result = run(pack.toString(), directory.resolve('fixtures').toString())
        List<String> out = result.out as List<String>

        then:
        result.exit == PackFixtures.REFUSED
        out.size() == 2
        out[0].startsWith('refused web: The feature pack acme-audit from ')
        out[0].contains('is for Grails [1.0.0,2.0.0)')
        out[1] == 'feature pack fixtures: generated=0 refused=1'
    }

    void "what a refusal says is one line, in bounds, without what acts on a terminal"() {
        given:
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), "odd web features=${'x' * 5000}\n")

        when:
        Map<String, Object> result = run(pack(FILES, TEMPLATES).toString(), directory.resolve('fixtures').toString(), fixtures.toString())
        List<String> out = result.out as List<String>

        then:
        result.exit == PackFixtures.REFUSED
        out.size() == 2
        out[0].length() < 2100
        !out[0].contains('x' * 200)
    }

    @Unroll
    void "#arguments are not fixtures to generate"() {
        when:
        Map<String, Object> result = run(arguments as String[])

        then:
        result.exit == PackFixtures.NOT_GENERATED
        result.out == []
        result.err.readLines() == [message]

        where:
        arguments             || message
        []                    || 'Give the directory of the feature pack, the directory to generate the fixtures in and, optionally, the file that lists the fixtures, and nothing else'
        ['a']                 || 'Give the directory of the feature pack, the directory to generate the fixtures in and, optionally, the file that lists the fixtures, and nothing else'
        ['a', 'b', 'c', 'd']  || 'Give the directory of the feature pack, the directory to generate the fixtures in and, optionally, the file that lists the fixtures, and nothing else'
        ['a', '']             || 'Give the directory of the feature pack, the directory to generate the fixtures in and, optionally, the file that lists the fixtures, and nothing else'
        ['a\u0000b', 'c']     || 'A directory or the file of the fixtures is not a path'
        ['a', 'b', 'missing'] || 'The fixtures of the feature pack could not be generated: The file of the fixtures is not a file'
    }

    @Unroll
    void "a list of fixtures that #problem is not generated from"() {
        given:
        Path output = directory.resolve('fixtures')
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), list)

        when:
        Map<String, Object> result = run(pack(FILES, TEMPLATES).toString(), output.toString(), fixtures.toString())

        then:
        result.exit == PackFixtures.NOT_GENERATED
        result.out == []
        result.err.readLines() == ["The fixtures of the feature pack could not be generated: ${message}".toString()]
        !Files.exists(output)

        where:
        problem                               | list                                  || message
        'names none'                          | '# none\n\n'                          || 'The list of fixtures names no fixture'
        'has a name that is not one'          | 'Web web\n'                           || 'Line 1 of the fixtures: the name of a fixture is lower case letters, digits and single hyphens, starts with a letter and has at most 60 characters, and this is Web'
        'has a name that leaves a directory'  | 'one web\n../two web\n'               || 'Line 2 of the fixtures: the name of a fixture is lower case letters, digits and single hyphens, starts with a letter and has at most 60 characters, and this is ../two'
        'has a name that acts on a terminal'  | 'a\u001b[31m web\n'                   || 'Line 1 of the fixtures: the name of a fixture is lower case letters, digits and single hyphens, starts with a letter and has at most 60 characters, and this is a?[31m'
        'has a name twice'                    | 'one web\none rest-api\n'             || 'Line 2 of the fixtures: there are two fixtures of the name one'
        'has a fixture without a type'        | 'one\n'                               || 'Line 1 of the fixtures: the fixture one has no type of application'
        'has a type there is not'             | 'one desktop\n'                       || 'Line 1 of the fixtures: the type of application of the fixture one is one of [web, rest-api, web-plugin, plugin, rest-api-plugin], and this is desktop'
        'has an option there is not'          | 'one web database=h2\n'               || 'Line 1 of the fixtures: the fixture one can have data=, servlet=, reloading=, jdk= and features=, each with a value, and this is database=h2'
        'has an option without a value'       | 'one web data=\n'                     || 'Line 1 of the fixtures: the fixture one can have data=, servlet=, reloading=, jdk= and features=, each with a value, and this is data='
        'has an option twice'                 | 'one web data=mongodb data=neo4j\n'   || 'Line 1 of the fixtures: the fixture one has data twice'
        'has a value an option has not'       | 'one web data=oracle\n'               || 'The fixture one has data=oracle, and data is one of [hibernate5, hibernate7, mongodb, neo4j]'
        'has a Java there is not'             | 'one web jdk=8\n'                     || 'The fixture one has jdk=8, and jdk is one of [21, 25, 26]'
        'has more fixtures than can be'       | (1..33).collect { "f${it} web\n" }.join('') || 'There are more than 32 fixtures'
    }

    void "a list of fixtures that is too long is not read"() {
        given:
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), '# ' + 'x' * (64 * 1024) + '\none web\n')

        when:
        Map<String, Object> result = run(pack(FILES, TEMPLATES).toString(), directory.resolve('fixtures').toString(), fixtures.toString())

        then:
        result.exit == PackFixtures.NOT_GENERATED
        result.err.readLines() == ['The fixtures of the feature pack could not be generated: The file of the fixtures has more than 65536 bytes']
    }

    void "a fixture is not generated over what is there, and then none is"() {
        given:
        Path output = directory.resolve('fixtures')
        Files.createDirectories(output.resolve('rest-api'))

        when:
        Map<String, Object> result = run(pack(FILES, TEMPLATES).toString(), output.toString())

        then:
        result.exit == PackFixtures.NOT_GENERATED
        result.out == []
        result.err.readLines().last() == 'The fixtures of the feature pack could not be generated: The directory of the fixture rest-api is there already, and is not written over'
        !Files.exists(output.resolve('web'))
    }

    void "the Grails Gradle plugin starts this class, and reads what it writes"() {
        given:
        Path fixtures = Files.writeString(directory.resolve('fixtures.txt'), 'one web\ntwo plugin\n')

        when:
        Map<String, Object> generated = run(pack('applicationTypes: [web]\n' + FILES, TEMPLATES, 'first').toString(), directory.resolve('a').toString())
        Map<String, Object> refused = run(pack('applicationTypes: [web]\n' + FILES, TEMPLATES, 'second').toString(), directory.resolve('b').toString(), fixtures.toString())
        Map<String, Object> unreadable = run(pack('colour: red\n', [:], 'third').toString(), directory.resolve('c').toString())

        then:
        PackFixtures.name == System.getProperty('forge.packFixtures.mainClassOfGradlePlugin')
        PackFixtures.GENERATED == Integer.getInteger('forge.packFixtures.generatedExitOfGradlePlugin')
        PackFixtures.REFUSED == Integer.getInteger('forge.packFixtures.refusedExitOfGradlePlugin')
        !(PackFixtures.NOT_GENERATED in [PackFixtures.GENERATED, PackFixtures.REFUSED])

        and: 'what it writes at most, a line for each fixture or problem and the last line, is no more than the plugin reads'
        PackFixtures.MAX_FIXTURES + 1 <= Integer.getInteger('forge.packFixtures.linesReadByGradlePlugin')
        PackDescriptorParser.MAX_PROBLEMS + 1 <= Integer.getInteger('forge.packFixtures.linesReadByGradlePlugin')

        and: 'and each line starts as the plugin reads it'
        "${PackFixtures.GENERATED_FIXTURE}web" ==~ System.getProperty('forge.packFixtures.generatedLineOfGradlePlugin')
        "${PackFixtures.REFUSED_FIXTURE}web: why" ==~ System.getProperty('forge.packFixtures.refusedLineOfGradlePlugin')
        "${PackFixtures.PACK_PROBLEM}a problem" ==~ System.getProperty('forge.packFixtures.packProblemLineOfGradlePlugin')
        "${PackFixtures.SUMMARY}generated=1 refused=0" ==~ System.getProperty('forge.packFixtures.summaryLineOfGradlePlugin')

        and:
        generated.out.size() == 2
        generated.out[0] ==~ System.getProperty('forge.packFixtures.generatedLineOfGradlePlugin')
        generated.out[1] ==~ System.getProperty('forge.packFixtures.summaryLineOfGradlePlugin')

        and:
        refused.out.size() == 3
        refused.out[0] ==~ System.getProperty('forge.packFixtures.generatedLineOfGradlePlugin')
        refused.out[1] ==~ System.getProperty('forge.packFixtures.refusedLineOfGradlePlugin')
        refused.out[2] ==~ System.getProperty('forge.packFixtures.summaryLineOfGradlePlugin')

        and:
        unreadable.out.size() == 2
        unreadable.out[0] ==~ System.getProperty('forge.packFixtures.packProblemLineOfGradlePlugin')
        unreadable.out[1] ==~ System.getProperty('forge.packFixtures.summaryLineOfGradlePlugin')
    }

    void "a JVM that has only what Forge needs to run generates the fixtures, with the repositories it is given"() {
        given: 'the class path of this module without its tests, which is what the Grails Gradle plugin resolves'
        Path valid = pack('applicationTypes: [web, plugin]\n' + FILES,
                ['Audit.groovy.gsp': '<% System.out.println("said by the template") %>package ${project.packageName}'], 'valid')
        Path output = directory.resolve('fixtures')
        Path repository = Files.createDirectories(directory.resolve('repository'))

        when:
        Map<String, Object> ofValid = fork([GRAILS_REPO_URL: "${repository};https://repo.example.org/maven".toString()], valid.toString(), output.toString())
        Map<String, Object> ofNothing = fork([:])

        then: 'what a template says is not taken for a fixture'
        ofValid.exit == PackFixtures.GENERATED
        ofValid.out == ['generated web', 'generated plugin', 'feature pack fixtures: generated=2 refused=0']
        ofValid.err.contains('said by the template')

        and: 'the applications resolve from the repositories of the environment, as one of the create commands does'
        ['web', 'plugin'].every { String fixture ->
            String build = Files.readString(output.resolve(fixture).resolve('build.gradle'))
            // a backslash of the path is written escaped into the string of the build file
            build.contains(repository.toString().replace('\\', '\\\\')) && build.contains('https://repo.example.org/maven')
        }

        and:
        ofNothing.exit == PackFixtures.NOT_GENERATED
        ofNothing.out == []
    }

    void "the fixtures are generated in production, whatever environment the build that starts it gives its JVM"() {
        given: 'a template that says the environment it is rendered in'
        Path pack = pack('applicationTypes: [web]\n' + FILES,
                ['Audit.groovy.gsp': '<% System.out.println("environment " + grails.util.Environment.current.name) %>package ${project.packageName}'])

        when: 'the JVM is given the environment of a project in GRAILS_ENV'
        Map<String, Object> result = fork([GRAILS_ENV: 'development'], pack.toString(), directory.resolve('fixtures').toString())

        then: 'the environment is the one of Forge, which generates applications as the command line does'
        result.exit == PackFixtures.GENERATED
        ((String) result.err).readLines().findAll { String line -> line.startsWith('environment ') }.unique() == ['environment production']
    }

    void "a template that ends the JVM does not make its fixtures ones that were generated"() {
        given:
        Path pack = pack('applicationTypes: [web]\n' + FILES, ['Audit.groovy.gsp': '<% System.exit(0) %>'])

        when:
        Map<String, Object> result = fork([:], pack.toString(), directory.resolve('fixtures').toString())

        then:
        result.exit == 0
        result.out == []
    }

    private static Map<String, Object> run(String... arguments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        int exit = PackFixtures.run(arguments, new PrintStream(out, true, 'UTF-8'), new PrintStream(err, true, 'UTF-8'))
        return [exit: exit, out: out.toString('UTF-8').readLines(), err: err.toString('UTF-8')]
    }

    private Map<String, Object> fork(Map<String, String> environment, String... arguments) {
        String classpath = Files.readAllLines(Path.of(System.getProperty('forge.packCheck.classpathFile'))).join(File.pathSeparator)
        // in a file, since a class path can be longer than a command line
        Path options = Files.createTempFile(directory, 'java', '.args')
        Files.writeString(options, '-cp\n"' + classpath.replace('\\', '\\\\') + '"\n')
        List<String> command = [Path.of(System.getProperty('java.home'), 'bin', 'java').toString(), "@${options}".toString(), PackFixtures.name]
        command.addAll(arguments)
        Path out = Files.createTempFile(directory, 'out', '.txt')
        Path err = Files.createTempFile(directory, 'err', '.txt')
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(out.toFile()).redirectError(err.toFile())
        builder.environment().remove('GRAILS_REPO_URL')
        builder.environment().putAll(environment)
        Process process = builder.start()
        assert process.waitFor(180, TimeUnit.SECONDS)
        return [exit: process.exitValue(), out: Files.readAllLines(out, StandardCharsets.UTF_8), err: Files.readString(err, StandardCharsets.UTF_8)]
    }

    private Path pack(String body, Map<String, String> files, String name = 'pack') {
        Path packDirectory = Files.createDirectories(directory.resolve(name))
        Files.writeString(packDirectory.resolve('feature.yml'), HEADER + body)
        Files.createDirectories(packDirectory.resolve('templates'))
        files.each { String path, String text ->
            Path file = packDirectory.resolve('templates').resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, text)
        }
        return packDirectory
    }
}
