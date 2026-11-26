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
package org.grails.gradle.plugin.commands

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/**
 * The catalog of the application commands of a project, written by {@code grailsToolCatalog}
 * from registrations and descriptors alone. No jar of these projects has a class in it, so a catalog
 * that is written at all was written without loading one.
 */
class CommandCatalogSpec extends CommandProjectSpecification {

    private static final String LEGACY_KEY = 'grails.dev.commands.ApplicationCommand'
    private static final String CATALOG = '.grails/cache/tool-catalog.json'

    private static final String EVERY_SOURCE = '''
        // what the compiler does with a registration written by hand: it copies it into the classes
        // directory, which is in front of the resources directory, and leaves the descriptor where it is
        tasks.named('compileGroovy') {
            def classes = destinationDirectory
            def registration = layout.projectDirectory.file('src/main/resources/META-INF/grails-cli.factories')
            doLast {
                def merged = new File(classes.get().asFile, 'META-INF/grails-cli.factories')
                merged.parentFile.mkdirs()
                merged.text = registration.asFile.text
            }
        }
        dependencies {
            implementation 'org.example:hello-plugin:1.2.3'
            implementation 'org.example:old-plugin:7.0.0'
            grailsCli project(':companion')
        }
    '''

    def "the catalog lists the commands of the application, of companions, of a plugin jar and of a project of the build"() {
        given:
        setupEverySource()

        when:
        BuildResult result = executeTask('grailsToolCatalog')

        then:
        result.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalogText() == JsonOutput.toJson([
                format     : 1,
                complete   : false,
                diagnostics: ['org.example:old-plugin:7.0.0 has commands of Grails 7, which are not listed because they cannot be run: ' +
                                      'set grails { legacyCommandSupport = true }, or use a version of the plugin for this version of Grails.'],
                tools      : [
                        command('config-report', 'Generates an AsciiDoc report of the application configuration', 'org.apache.grails:grails-core-cli:0.0.1-test'),
                        command('hello-world', 'Says "hello" to the world, grüßt 日本', 'org.example:hello-plugin-cli:1.2.3'),
                        command('local', 'A command of the application', 'application'),
                        command('no-descriptor', '', 'org.example:hello-plugin-cli:1.2.3', 'factories'),
                        command('runtime', 'In the jar of the plugin itself', 'org.example:hello-plugin:1.2.3'),
                        command('sibling', 'Of a project of this build', 'org.example.test:companion:1.0.0'),
                ],
                testPhases : [[name: 'unit', task: 'test'], [name: 'integration', task: 'integrationTest']],
        ]) + '\n'

        and: 'the file is ASCII on one line and says nothing of this machine'
        catalogText().bytes.every { byte b -> b == 0x0a || (b >= 0x20 && b <= 0x7e) }
        catalogText().count('\n') == 1
        !catalogText().contains(projectDir.name)

        and: 'what the catalog could not list is said when the task runs'
        result.output.contains('org.example:old-plugin:7.0.0 has commands of Grails 7')
    }

    def "the commands of a Grails 7 plugin are listed, as such, when the project can run them"() {
        given:
        setupEverySource('''
            grails {
                legacyCommandSupport = true
            }
        ''')
        publish('org.apache.grails', 'grails-core-cli-legacy', GRAILS_VERSION, [
                (CLI_FACTORIES): LEGACY_BRIDGE,
                // that the bridge is there is the entry of its class; what is in it is never read
                'org/apache/grails/core/cli/compat/LegacyApplicationCommandProvider.class': 'not a class',
        ])

        when:
        executeTask('grailsToolCatalog')
        Map catalog = catalog()

        then:
        catalog.complete == true
        catalog.diagnostics == []
        catalog.tools*.name == ['config-report', 'hello-legacy-app', 'hello-world', 'local', 'no-descriptor', 'runtime', 'sibling']
        catalog.tools.find { it.name == 'hello-legacy-app' } == command('hello-legacy-app', '', 'org.example:old-plugin:7.0.0', 'legacy')
    }

    def "the catalog is the same bytes for the same commands, is not written again when nothing changed, and follows a change"() {
        given:
        setupEverySource()

        when: 'the same build runs again'
        executeTask('grailsToolCatalog')
        String first = catalogText()
        // the tasks of the commands are registered from the jars of grailsCliClasspath while the build is
        // configured, so the jar of the companion project, which the first build made, is an input of the
        // configuration the second build stores
        executeTask('grailsToolCatalog')
        BuildResult again = executeTask('grailsToolCatalog')

        then:
        again.output.contains('Configuration cache entry reused')
        again.task(':grailsToolCatalog').outcome == TaskOutcome.UP_TO_DATE

        when: 'the catalog is written from nothing, in a build without the configuration cache'
        new File(projectDir, 'build').deleteDir()
        new File(projectDir, '.grails').deleteDir()
        BuildResult clean = executeTask('grailsToolCatalog', ['--no-configuration-cache'])

        then:
        clean.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalogText() == first

        when: 'a class of the application changes that is not a command'
        projectFile('src/main/groovy/example/Other.groovy', 'package example\nclass Other { }\n')
        BuildResult unrelated = executeTask('grailsToolCatalog')

        then:
        unrelated.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        unrelated.task(':grailsToolCatalog').outcome == TaskOutcome.UP_TO_DATE

        when: 'the application gets a command'
        projectFile('src/main/resources/META-INF/grails-cli.factories', registrations('example.LocalCommand', 'example.SecondCommand'))
        projectFile("src/main/resources/${descriptorPath('example.SecondCommand')}", descriptor('example.SecondCommand', 'second', 'Added later'))
        BuildResult changed = executeTask('grailsToolCatalog')

        then:
        changed.task(':grailsToolCatalog').outcome == TaskOutcome.SUCCESS
        catalog().tools.find { it.name == 'second' } == command('second', 'Added later', 'application')
    }

    def "a project that has a task of the name keeps it, and is told that the catalog is not written"() {
        given: 'a project whose task is there before the plugin is'
        setupCommandProject()
        projectFile('companion/build.gradle', '''
            plugins {
                id 'java-library'
                id 'org.apache.grails.gradle.grails-cli' apply false
            }
            tasks.register('grailsToolCatalog') {
                doLast { println 'THE TASK OF THE BUILD' }
            }
            apply plugin: 'org.apache.grails.gradle.grails-cli'
        ''')

        when:
        BuildResult result = executeTask(':companion:grailsToolCatalog')

        then:
        result.output.contains('Project :companion already has a task named grailsToolCatalog, so the Grails Gradle plugin does not register ' +
                'its own and the catalog of the commands of the project is not written. Give the task another name.')
        result.output.contains('THE TASK OF THE BUILD')
        !new File(projectDir, 'companion/build/grails/tool-catalog.json').exists()
    }

    def "what a dependency says of its commands is held to a form, and what is not as it has to be is left out and named"() {
        given: 'a companion for each thing a jar can get wrong, and one that is as it has to be'
        setupCommandProject("""
            dependencies {
                ${HOSTILE.keySet().collect { String artifact -> "grailsCli 'org.hostile:${artifact}:1.0'" }.join('\n')}
                // after the jar that registers its class again without a descriptor
                grailsCli 'org.hostile:survivor:1.0'
                implementation 'org.example:old-plugin:7.0.0'
                // on the class path of the commands only: what the compiler reads has to be a jar
                grailsCliLegacy 'org.hostile:not-a-jar:1.0'
                grailsCliLegacy 'org.hostile:names-the-bridge:1.0'
                grailsCliLegacy files('libs/upper.JAR', 'libs/commands.zip', 'libs/broken.ZIP', 'libs/notes.txt')
            }
        """)
        publish('org.hostile', 'not-a-jar', '1.0', [:]).bytes = 'not a jar'.bytes
        // a jar that says the bridge for the commands of Grails 7 is there, and does not have it
        publish('org.hostile', 'names-the-bridge', '1.0', [(CLI_FACTORIES): LEGACY_BRIDGE])
        // archives a JVM reads whatever they are called, one that is none, and a file that is not meant to be one
        new File(projectDir, 'libs').mkdirs()
        writeJar(new File(projectDir, 'libs/upper.JAR'), [(CLI_FACTORIES): registrations('h.UpperCommand')])
        writeJar(new File(projectDir, 'libs/commands.zip'), named(['h.ZippedCommand']) { String className, int index -> descriptor(className, 'zipped', 'In a zip') })
        projectFile('libs/broken.ZIP', 'not a zip')
        projectFile('libs/notes.txt', 'not a zip either')
        HOSTILE.each { String artifact, Map<String, Object> entries -> publish('org.hostile', artifact, '1.0', entries) }
        publish('org.hostile', 'survivor', '1.0', [
                (CLI_FACTORIES): registrations('h.SurvivorCommand'),
                (descriptorPath('h.SurvivorCommand')): descriptor('h.SurvivorCommand', 'survivor', MARKUP),
        ])
        publish('org.example', 'old-plugin', '7.0.0', [
                'META-INF/grails.factories': "${LEGACY_KEY}=org.example.legacy.HelloLegacyAppCommand\n".toString(),
        ])

        when:
        executeTask('grailsToolCatalog')
        Map catalog = catalog()
        List<String> diagnostics = catalog.diagnostics

        then: 'the catalog says it is not complete'
        catalog.complete == false

        and: 'the commands that are as they have to be are listed, and no other'
        catalog.tools.findAll { it.origin != 'org.hostile:too-many:1.0' } == [
                command('config-report', 'Generates an AsciiDoc report of the application configuration', 'org.apache.grails:grails-core-cli:0.0.1-test'),
                // its descriptor is in a later jar than the first that registers the class, and its
                // description is what the descriptor says: who shows it takes it for text of a stranger
                command('survivor', MARKUP, 'org.hostile:survivor:1.0'),
                command('upper', '', 'file:upper.JAR', 'factories'),
                command('zipped', 'In a zip', 'file:commands.zip'),
        ]
        catalog.tools.count { it.origin == 'org.hostile:too-many:1.0' } == 256

        and: 'each diagnostic is one line that a terminal can take'
        diagnostics.every { String line -> line ==~ /[\x20-\x7e]+/ && !line.contains('@|') && !line.contains('${') && line.length() <= 600 }

        and: 'each thing that was wrong is named, by the class and where it comes from'
        expectedDiagnostics().each { String line -> assert diagnostics.contains(line) }
        diagnostics.size() == expectedDiagnostics().size()
    }

    private static List<String> expectedDiagnostics() {
        [
                'A command that org.hostile:not-a-class:1.0 registers is not listed because what is registered is not the name of a class.',
                'The archive of org.hostile:not-a-jar:1.0 cannot be read, so its commands are not listed.',
                'The archive of file:broken.ZIP cannot be read, so its commands are not listed.',
                'The command class h.DifferCommand is not listed: it has descriptors that differ, in org.hostile:differ-a:1.0, org.hostile:differ-b:1.0.',
                'The command class h.ArrayCommand of org.hostile:not-an-object:1.0 is not listed: its descriptor is not a JSON object.',
                'The command class h.BadBytesCommand of org.hostile:not-json:1.0 is not listed: its descriptor ' + NOT_JSON + '.',
                'The command class h.BadJsonCommand of org.hostile:not-json:1.0 is not listed: its descriptor ' + NOT_JSON + '.',
                'The command class h.Command of org.hostile:no-name:1.0 is not listed: it has no descriptor, and the name its class name gives cannot be the name of a command.',
                'The command class h.ConsoleCommand of org.hostile:targets:1.0 is not listed: its descriptor is for the execution target console, which this version of Grails cannot run.',
                'The command class h.DeepCommand of org.hostile:not-an-object:1.0 is not listed: its descriptor ' + NOT_JSON + '.',
                'The command class h.FormatTextCommand of org.hostile:formats:1.0 is not listed: its descriptor is not of format 1, the one this version of Grails reads.',
                'The command class h.FormatTwoCommand of org.hostile:formats:1.0 is not listed: its descriptor is not of format 1, the one this version of Grails reads.',
                'The command class h.LargeCommand of org.hostile:too-large:1.0 is not listed: its descriptor has more than 16384 bytes or cannot be read.',
                'The command class h.NoTargetCommand of org.hostile:targets:1.0 is not listed: its descriptor has an execution target that does not exist.',
                'The command class h.OtherCommand of org.hostile:other-class:1.0 is not listed: its descriptor is the descriptor of another class.',
                'The command twice is not listed because more than one class has that name: a.FirstCommand of org.hostile:twice-a:1.0, b.SecondCommand of org.hostile:twice-b:1.0, c.ThirdCommand of org.hostile:twice-c:1.0 and 2 more.',
                'The commands of org.hostile:large-factories:1.0 are not listed: its META-INF/grails-cli.factories has more than 262144 bytes or cannot be read.',
                'org.example:old-plugin:7.0.0 has commands of Grails 7, which are not listed because they cannot be run: ' +
                        'set grails { legacyCommandSupport = true }, or use a version of the plugin for this version of Grails.',
                'org.hostile:too-many:1.0 registers more than 256 commands; the rest are not listed.',
        ] + (0..<BAD_NAMES.size()).collect { int index ->
            "The command class h.Name${index}Command of org.hostile:names:1.0 is not listed: its descriptor has a name that cannot be the name of a command " +
                    '(lower case letters and digits, in words joined by single hyphens, at most 64 characters).'
        } + (0..<BAD_DESCRIPTIONS.size()).collect { int index ->
            "The command class h.Description${index}Command of org.hostile:descriptions:1.0 is not listed: its descriptor has a description that is not one line of at most 512 characters that can be shown."
        }
    }

    private static final String NOT_JSON = 'is not JSON in UTF-8 as the descriptor format has it (strict JSON, nested at most 4 deep)'

    private static final String MARKUP = 'Is as it has to be, @|red and is not markup|@ ${here}'

    private static final String LEGACY_BRIDGE =
            'org.apache.grails.core.cli.ApplicationCommandProvider=org.apache.grails.core.cli.compat.LegacyApplicationCommandProvider\n'

    private static final List<Object> BAD_NAMES = [
            'Upper', 'two words', '../escape', 'x' * 65, 'esc\u001b[31m', '-lead', 'trail-', 'a--b', '', 'under_score', 'é', '1st', 42, null, ['list'],
    ].asImmutable()

    private static final List<Object> BAD_DESCRIPTIONS = [
            'two\nlines', 'esc\u001b[0m', 'x' * 513, 'right to left ‮', 'line separator  ', 7, ['list'], [object: true],
    ].asImmutable()

    // artifact of the group org.hostile -> the entries of its jar
    private static final Map<String, Map<String, Object>> HOSTILE = [
            'names'          : named((0..<BAD_NAMES.size()).collect { int index -> "h.Name${index}Command".toString() }) { String className, int index ->
                JsonOutput.toJson([format: 1, class: className, name: BAD_NAMES[index]])
            },
            'descriptions'   : named((0..<BAD_DESCRIPTIONS.size()).collect { int index -> "h.Description${index}Command".toString() }) { String className, int index ->
                JsonOutput.toJson([format: 1, class: className, name: "described-${index}".toString(), description: BAD_DESCRIPTIONS[index]])
            },
            'targets'        : named(['h.ConsoleCommand', 'h.NoTargetCommand']) { String className, int index ->
                JsonOutput.toJson([format: 1, class: className, name: "target-${index}".toString(), target: ['console', 'nowhere \u001b[31m'][index]])
            },
            'formats'        : named(['h.FormatTwoCommand', 'h.FormatTextCommand']) { String className, int index ->
                JsonOutput.toJson([format: [2, '1'][index], class: className, name: "format-${index}".toString()])
            },
            'other-class'    : named(['h.OtherCommand']) { String className, int index ->
                descriptor('h.SomeOtherCommand', 'other-class')
            },
            'not-json'       : named(['h.BadJsonCommand', 'h.BadBytesCommand']) { String className, int index ->
                index == 0 ? '{"format": 1, "name": nope' : ([0x7b, 0x22, 0xff, 0xfe, 0x22, 0x7d] as byte[])
            },
            'not-an-object'  : named(['h.ArrayCommand', 'h.DeepCommand']) { String className, int index ->
                index == 0 ? '[1]' : '[' * 5000 + ']' * 5000
            },
            'too-large'      : named(['h.LargeCommand']) { String className, int index ->
                JsonOutput.toJson([format: 1, class: className, name: 'large', description: 'short', padding: 'x' * 20000])
            },
            'not-a-class'    : [(CLI_FACTORIES): registrations('../../../etc/passwd', 'two words', 'h.Trailing.', 'x' * 300)] as Map<String, Object>,
            'large-factories': [(CLI_FACTORIES): '#' + 'x' * (300 * 1024) + '\n' + registrations('h.HiddenCommand')] as Map<String, Object>,
            'too-many'       : [(CLI_FACTORIES): registrations((0..256).collect { int index -> "h.many.M${index}Command".toString() } as String[])] as Map<String, Object>,
            'no-name'        : [(CLI_FACTORIES): registrations('h.Command')] as Map<String, Object>,
            'twice-a'        : named(['a.FirstCommand']) { String className, int index -> descriptor(className, 'twice') },
            'twice-b'        : named(['b.SecondCommand']) { String className, int index -> descriptor(className, 'twice') },
            'twice-c'        : named(['c.ThirdCommand']) { String className, int index -> descriptor(className, 'twice') },
            'twice-d'        : named(['d.FourthCommand']) { String className, int index -> descriptor(className, 'twice') },
            'twice-e'        : named(['e.FifthCommand']) { String className, int index -> descriptor(className, 'twice') },
            // one class with two descriptors that are not the same: neither is used
            'differ-a'       : named(['h.DifferCommand']) { String className, int index -> descriptor(className, 'differ', 'One') },
            'differ-b'       : named(['h.DifferCommand']) { String className, int index -> descriptor(className, 'differ', 'Another') },
            // and with the same descriptor twice it is one command
            // a descriptor is read for a registered class only, and a class is described by the first element that has its descriptor
            'unregistered'   : [(descriptorPath('h.StrayCommand')): descriptor('h.StrayCommand', 'stray')] as Map<String, Object>,
            'survivor-again' : [(CLI_FACTORIES): registrations('h.SurvivorCommand')] as Map<String, Object>,
    ].asImmutable()

    // a jar that registers these classes, each with the descriptor the closure gives
    private static Map<String, Object> named(List<String> classNames, Closure<Object> content) {
        Map<String, Object> entries = [(CLI_FACTORIES): registrations(classNames as String[])] as Map<String, Object>
        classNames.eachWithIndex { String className, int index -> entries[descriptorPath(className)] = content(className, index) }
        entries
    }

    private void setupEverySource(String more = '') {
        setupCommandProject(EVERY_SOURCE + more)
        // the plugin: its jar names its companion, and registers a command of its own, as plugins did before companions
        publish('org.example', 'hello-plugin', '1.2.3', [
                (CLI_FACTORIES): registrations('com.example.RuntimeCommand'),
                (descriptorPath('com.example.RuntimeCommand')): descriptor('com.example.RuntimeCommand', 'runtime', 'In the jar of the plugin itself'),
        ], ['Grails-Cli-Artifact': 'org.example:hello-plugin-cli'])
        publish('org.example', 'hello-plugin-cli', '1.2.3', [
                (CLI_FACTORIES): registrations('com.example.HelloWorldCommand', 'com.example.NoDescriptorCommand'),
                (descriptorPath('com.example.HelloWorldCommand')): descriptor('com.example.HelloWorldCommand', 'hello-world', 'Says "hello" to the world, grüßt 日本'),
        ])
        publish('org.example', 'old-plugin', '7.0.0', [
                'META-INF/grails.factories': "${LEGACY_KEY}=org.example.legacy.HelloLegacyAppCommand\n".toString(),
        ])
        projectFile("companion/src/main/resources/${CLI_FACTORIES}", registrations('sibling.SiblingCommand'))
        projectFile("companion/src/main/resources/${descriptorPath('sibling.SiblingCommand')}", descriptor('sibling.SiblingCommand', 'sibling', 'Of a project of this build'))
        // a command of the application that is registered and described by hand, as the guide says to do for
        // one the compiler does not describe: both files are in the resources
        projectFile("src/main/resources/${CLI_FACTORIES}", registrations('example.LocalCommand'))
        projectFile("src/main/resources/${descriptorPath('example.LocalCommand')}", descriptor('example.LocalCommand', 'local', 'A command of the application'))
    }

    private static Map<String, Object> command(String name, String description, String origin, String registration = 'descriptor') {
        [name: name, aliases: [], description: description, origin: origin, target: 'application', startsApplication: true, registration: registration]
    }

    private String catalogText() {
        new File(projectDir, CATALOG).getText('US-ASCII')
    }

    private Map catalog() {
        (Map) new JsonSlurper().parseText(catalogText())
    }
}
