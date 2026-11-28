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
package org.grails.forge.cli.command

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.apache.grails.forge.cli.console.ProjectTools
import org.apache.grails.forge.cli.console.ProjectToolsSpec
import org.apache.grails.forge.core.pack.PackArtefact
import org.apache.grails.forge.core.state.StateYaml
import org.grails.forge.cli.CommandLineCompleter
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import picocli.CommandLine
import spock.lang.Unroll

/**
 * The commands a feature pack gives the command line of an application that has its feature, through
 * the command line: an application is created with the pack of a directory and of a jar, and the
 * commands are listed, described, completed and run in it from its record alone, without the pack and
 * without the project generator.
 */
class PackArtefactCommandsSpec extends CommandSpec {

    static final String PLUGIN = 'com.acme:acme-audit-grails:2.3.0'

    static final String DESCRIPTOR = CreateAppFeaturePackSpec.DESCRIPTOR.replace("files:\n  - from: audit.txt\n    to: 'grails-app/conf/{packagePath}/audit.txt'\n", '') + '''\
        artefacts:
          - name: audit-listener
            description: Creates a listener of audit events, 100% yours ${sys:user.home}
            suffix: AuditListener
            template: artefacts/AuditListener.groovy
            to: grails-app/services/{packagePath}/{className}AuditListener.groovy
            testTemplate: artefacts/AuditListenerSpec.groovy
            testTo: src/test/groovy/{packagePath}/{className}AuditListenerSpec.groovy
          - name: audit-report
            template: artefacts/AuditReport.groovy
            to: src/main/groovy/{packagePath}/{className}AuditReport.groovy
          - name: domain-check
            description: Creates a check of a domain class
            template: artefacts/DomainCheck.groovy
            to: src/main/groovy/{packagePath}/{className}DomainCheck.groovy
        '''.stripIndent()

    // with what a template that is rendered would trip over, and what the build of a plugin replaces in its resources
    static final String LISTENER = '@artifact.package@\nclass @artifact.name@AuditListener {\n\n    String name = \'@artifact.propertyName@\'\n' +
            '    String mail = "audit@example.org"\n    String text = "${name} <%= 1 %> @info.app.name@"\n}\n'
    static final String LISTENER_SPEC = '@artifact.package@\nimport spock.lang.Specification\n\nclass @artifact.name@AuditListenerSpec extends Specification {\n}\n'
    static final String REPORT = 'package @artifact.package.name@\n\nclass @artifact.name@AuditReport {\n    // @artifact.package.path@\n}\n'
    static final String CHECK = '@artifact.package@\nclass @artifact.name@DomainCheck {\n}\n'

    static final Map<String, String> TEMPLATES = ['artefacts/AuditListener.groovy'    : LISTENER,
                                                  'artefacts/AuditListenerSpec.groovy': LISTENER_SPEC,
                                                  'artefacts/AuditReport.groovy'      : REPORT,
                                                  'artefacts/DomainCheck.groovy'      : CHECK]

    static final String IN_PROJECT = 'src/main/templates/features/acme-audit/'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File work
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        work = Files.createTempDirectory('grailsforgeartefacts').toFile()
        home = new File(work, 'home')
        home.mkdirs()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
        // a name that is no command is refused at once: no build of the application lists its commands for it
        ProjectTools.setListing('false')
    }

    void cleanup() {
        ProjectTools.setListing(null)
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
    }

    @Unroll
    void "an application created with the feature of the pack of a #from has the templates and the commands, which write classes from the templates of the project"() {
        given:
        File app = new File(work, 'app')
        List<String> again = from == 'directory' ? ['--feature-pack-dir', directoryPack().absolutePath] :
                ['--feature-pack-repository', repository().toPath().toAbsolutePath().normalize().toUri().toString()]

        when:
        int exit = create(app, ['--features', from == 'directory' ? 'acme-audit' : PLUGIN] + again)

        then: 'the templates are files of the application, byte for byte, and of its record'
        exit == 0
        TEMPLATES.every { String path, String text -> new File(app, IN_PROJECT + path).getText('UTF-8') == text }
        TEMPLATES.every { String path, String text -> new File(app, '.grails/baseline/files/' + IN_PROJECT + path).getText('UTF-8') == text }
        new File(app, '.grails/forge.lock.yml').text.contains('  artefacts:\n  - name: audit-listener\n')

        and: 'what the pack adds says the commands'
        out.toString().contains('commands it gives the command line of the application, whose templates it writes under src/main/templates/features/acme-audit: ' +
                'create-audit-listener, create-audit-report, create-domain-check')

        when: 'the application is generated again from its record, to compare'
        exit = inDirectory(app) { ForgeCli.execute(beanContext, (['project', 'regenerate', '--diff', '--json'] + again) as String[]) }

        then: 'it is what was generated'
        exit == 0
        (new JsonSlurper().parseText(out.toString().readLines().last()) as Map).hasChanges == false

        when: 'a command of the feature is run, on a command line that has no project generator'
        ForgeContext context = new ForgeContext()
        exit = inDirectory(app) { ForgeCli.execute(context, 'create-audit-listener', 'order') }

        then: 'the class and its test are written with the names in the place of the tokens, and nothing else of the template is changed'
        exit == 0
        !context.isStarted()
        new File(app, 'grails-app/services/com/example/OrderAuditListener.groovy').text == 'package com.example\n\nclass OrderAuditListener {\n\n' +
                '    String name = \'order\'\n    String mail = "audit@example.org"\n    String text = "${name} <%= 1 %> @info.app.name@"\n}\n'
        new File(app, 'src/test/groovy/com/example/OrderAuditListenerSpec.groovy').text ==
                'package com.example\n\nimport spock.lang.Specification\n\nclass OrderAuditListenerSpec extends Specification {\n}\n'
        out.toString().readLines() == ['| Rendered audit-listener to grails-app/services/com/example/OrderAuditListener.groovy',
                                       '| Rendered audit-listener to src/test/groovy/com/example/OrderAuditListenerSpec.groovy']
        err.toString().isEmpty()

        when: 'again'
        exit = inDirectory(app) { ForgeCli.execute(context, 'create-audit-listener', 'order') }

        then: 'neither file is replaced'
        exit == 1
        err.toString().contains('grails-app/services/com/example/OrderAuditListener.groovy already exists. Run again with -f to overwrite.')
        err.toString().contains('src/test/groovy/com/example/OrderAuditListenerSpec.groovy already exists. Run again with -f to overwrite.')

        when: 'the template of the project is edited, and the command is forced'
        new File(app, IN_PROJECT + 'artefacts/AuditListener.groovy').setText('// ours\n@artifact.package@\nclass @artifact.name@AuditListener { }\n', 'UTF-8')
        exit = inDirectory(app) { ForgeCli.execute(context, 'create-audit-listener', '--force', 'OrderAuditListener') }

        then: 'the class is written from the template as the project has it; a name that ends with what the kind of class adds is taken without it'
        exit == 0
        new File(app, 'grails-app/services/com/example/OrderAuditListener.groovy').text == '// ours\npackage com.example\n\nclass OrderAuditListener { }\n'
        !new File(app, 'grails-app/services/com/example/OrderAuditListenerAuditListener.groovy').exists()

        when: 'another feature is added, which generates the application again with the pack'
        exit = inDirectory(app) { ForgeCli.execute(beanContext, (['feature', 'add', 'grails-quartz'] + again) as String[]) }

        then: 'the templates are kept, the one that was edited as it was edited, and the record has the commands'
        exit == 0
        new File(app, 'build.gradle').text.contains('grails-quartz')
        new File(app, IN_PROJECT + 'artefacts/AuditListener.groovy').text.startsWith('// ours\n')
        new File(app, IN_PROJECT + 'artefacts/AuditReport.groovy').text == REPORT
        new File(app, '.grails/forge.lock.yml').text.contains('  artefacts:\n  - name: audit-listener\n')

        when: 'a command of the feature is run for a class in a package of its own'
        exit = inDirectory(app) { ForgeCli.execute(context, 'create-audit-report', 'org.acme.reports.monthly') }

        then:
        exit == 0
        !context.isStarted()
        new File(app, 'src/main/groovy/org/acme/reports/MonthlyAuditReport.groovy').text ==
                'package org.acme.reports\n\nclass MonthlyAuditReport {\n    // org/acme/reports\n}\n'

        where:
        from << ['directory', 'jar']
    }

    void "the commands of a feature are listed, described for a tool, completed and found by their short forms, from the record alone"() {
        given:
        File app = new File(work, 'app')
        create(app, ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath])
        // the pack is not needed again
        new File(work, 'pack').deleteDir()
        ForgeContext context = new ForgeContext()

        when:
        int exit = inDirectory(app) { ForgeCli.execute(context, 'help') }
        String help = out.toString().replaceAll(/\s+/, ' ')

        then: 'with what the feature says of them as text, and the feature they are of'
        exit == 0
        help.contains('create-audit-listener Creates a listener of audit events, 100% yours ${sys:user.home} (feature acme-audit)')
        help.contains('create-audit-report Creates a class (audit-report) (feature acme-audit)')

        when:
        exit = inDirectory(app) { ForgeCli.execute(context, 'help', 'create-audit-listener') }

        then:
        exit == 0
        out.toString().readLines()[0] == 'Usage: grails-forge-cli create-audit-listener [-fhvVx] [--non-interactive] [--plain-output] NAME'
        out.toString().readLines()[1] == 'Creates a listener of audit events, 100% yours ${sys:user.home} (feature acme-audit)'
        out.toString().readLines()[2] == 'Short form: cal'
        out.toString().contains('The name of the class to create')
        out.toString().contains('--force')

        when:
        exit = inDirectory(app) { ForgeCli.execute(context, 'help', '--json') }
        Map document = new JsonSlurper().parseText(out.toString()) as Map
        Map listener = (document.commands as List<Map>).find { it.name == 'create-audit-listener' }

        then:
        exit == 0
        listener.origin == 'feature:acme-audit'
        listener.project == true
        listener.description == 'Creates a listener of audit events, 100% yours ${sys:user.home} (feature acme-audit)'
        (listener.parameters as List<Map>)*.label == ['NAME']
        (listener.options as List<Map>).any { '--force' in it.names }
        (document.commands as List<Map>).find { it.name == 'create-controller' }.origin == 'built-in'
        (document.commands as List<Map>)*.name.containsAll(['create-audit-report', 'create-domain-check'])

        and: 'they come after the commands of the command line itself'
        (document.commands as List<Map>)*.name.indexOf('create-audit-listener') > (document.commands as List<Map>)*.name.indexOf('plugin')

        when: 'the console completes a name'
        List<String> offered = inDirectoryOf(app) { complete(ForgeCli.createCommandLine(context, true), 'create-au') }

        then:
        offered.containsAll(['create-audit-listener', 'create-audit-report'])

        when: 'a short form that is one of a command of the command line itself'
        exit = inDirectory(app) { ForgeCli.execute(context, 'cDC', 'book') }

        then: 'is still that command, and stands for one'
        exit == 0
        new File(app, 'grails-app/domain/com/example/Book.groovy').exists()
        !new File(app, 'src/main/groovy/com/example/BookDomainCheck.groovy').exists()

        when: 'a short form no command of the command line itself has'
        exit = inDirectory(app) { ForgeCli.execute(context, 'cAL', 'payment') }

        then:
        exit == 0
        new File(app, 'grails-app/services/com/example/PaymentAuditListener.groovy').exists()

        and: 'none of this started the project generator'
        !context.isStarted()
    }

    @Unroll
    void "a command of a feature is no command #where, which is said with where it is one"() {
        given:
        File directory = new File(work, 'elsewhere')
        directory.mkdirs()
        if (inApplication) {
            create(directory, [])
        }
        ForgeContext context = new ForgeContext()

        when:
        int exit = inDirectory(directory) { ForgeCli.execute(context, 'create-audit-listener', 'order') }

        then:
        exit == 2
        err.toString().contains('There is no command create-audit-listener')
        err.toString().contains('A feature can give the command line a command of this name. It is a command in the project of an application that has the feature, ' +
                'where grails-forge-cli help lists it and grails-forge-cli feature add adds the feature')
        !context.isStarted()
        !new File(directory, 'grails-app/services/com/example/OrderAuditListener.groovy').exists()

        where:
        where                                    | inApplication
        'outside a project'                      | false
        'in an application without the feature'  | true
    }

    void "a name the Grails shell had is answered with what there is in its place, and not as a command of a feature"() {
        when:
        int exit = inDirectory(work) { ForgeCli.execute(new ForgeContext(), 'create-script', 'x') }

        then:
        exit == 2
        err.toString().contains('create-tool')
        !err.toString().contains('A feature can give the command line a command of this name')
    }

    @Unroll
    void "a command of a feature writes nothing when #what"() {
        given:
        File app = new File(work, 'app')
        create(app, ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath])
        change.call(app)
        ForgeContext context = new ForgeContext()

        when:
        int exit = inDirectory(app) { ForgeCli.execute(context, (['create-audit-listener'] + arguments) as String[]) }

        then:
        exit == code
        err.toString().contains(message)
        !new File(app, 'grails-app/services/com/example/OrderAuditListener.groovy').exists()
        !new File(app, 'src/test/groovy/com/example/OrderAuditListenerSpec.groovy').exists()
        !context.isStarted()

        where:
        what | change | arguments || code | message
        'the template of its test is not in the project' | { File at -> assert new File(at, IN_PROJECT + 'artefacts/AuditListenerSpec.groovy').delete() } | ['order'] || 1 |
                'The template src/main/templates/features/acme-audit/artefacts/AuditListenerSpec.groovy is not in the project, and create-audit-listener writes the test from it. ' +
                'It is a file the feature acme-audit wrote when the application was generated: restore it from version control, or from the copy the record of the project ' +
                'keeps of it, .grails/baseline/files/src/main/templates/features/acme-audit/artefacts/AuditListenerSpec.groovy, ' +
                'which grails-forge-cli project regenerate --output DIRECTORY writes too'
        'the template of its class is not in the project' | { File at -> assert new File(at, IN_PROJECT + 'artefacts/AuditListener.groovy').delete() } | ['order'] || 1 |
                'The template src/main/templates/features/acme-audit/artefacts/AuditListener.groovy is not in the project, and create-audit-listener writes its class from it.'
        'a template of the project cannot be rendered' | { File at -> new File(at, IN_PROJECT + 'artefacts/AuditListenerSpec.groovy').text = 'class @artifact.nope@ { }\n' } | ['order'] || 1 |
                'The template src/main/templates/features/acme-audit/artefacts/AuditListenerSpec.groovy cannot be rendered: line 1: there is no @artifact.nope@'
        'a template of the project is not text' | { File at -> new File(at, IN_PROJECT + 'artefacts/AuditListener.groovy').bytes = [0xC3, 0x28] as byte[] } | ['order'] || 1 |
                'The template src/main/templates/features/acme-audit/artefacts/AuditListener.groovy is not used: it is not text in UTF-8'
        'the name is no name of a class' | { File at -> } | ['../order'] || 2 | 'is not a name for a class: it is a path'
        'it is given no name' | { File at -> } | [] || 2 | "Missing required parameter: 'NAME'"
    }

    void "a pack is refused a command the command line has, and nothing is created"() {
        given:
        File app = new File(work, 'app')
        File pack = directoryPack(DESCRIPTOR.replace('name: audit-report', 'name: service'))

        when:
        int exit = create(app, ['--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath])

        then:
        exit != 0
        err.toString().contains('artefacts[1].name: create-service is a command of the command line itself, or was one of the Grails shell, and a pack gives none of that name')
        !new File(app, 'build.gradle').exists()
    }

    void "every command of the command line that creates something is a name no pack can take"() {
        given:
        CommandLine commandLine = inDirectoryOf(new File(work, 'home')) { ForgeCli.createCommandLine(new ForgeContext(), true) }

        expect: 'outside a project, where the commands of every kind of project are there by their names'
        commandLine.getSubcommands().keySet().findAll { it.startsWith(PackArtefact.COMMAND_PREFIX) }.every { String command ->
            PackArtefact.RESERVED.contains(command.substring(PackArtefact.COMMAND_PREFIX.length()))
        }
        commandLine.getSubcommands().keySet().count { it.startsWith(PackArtefact.COMMAND_PREFIX) } >= 18
    }

    void "a command of the application gives way to the command of a feature of the same name, which is said"() {
        given:
        File app = new File(work, 'app')
        create(app, ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath])
        File listed = new File(app, ToolCatalogFile.LOCATION)
        listed.parentFile.mkdirs()
        listed.setText(ProjectToolsSpec.doc([[name: 'create-audit-listener', aliases: [], description: 'Another one', origin: 'org.example:audit-cli:1.0.0',
                                                     target: 'application', registration: 'descriptor']]), StandardCharsets.UTF_8.name())
        ForgeContext context = new ForgeContext()

        when:
        int exit = inDirectory(app) { ForgeCli.execute(context, 'create-audit-listener', 'order') }

        then: 'the command of the feature runs'
        exit == 0
        new File(app, 'grails-app/services/com/example/OrderAuditListener.groovy').exists()
        (out.toString() + err.toString()).replaceAll(/\s+/, ' ').contains('create-audit-listener is a command of the feature acme-audit of the application, ' +
                'so the tool create-audit-listener of org.example:audit-cli:1.0.0 is not a command here. That one is run with: grails-forge-cli run-tool create-audit-listener')
    }

    void "of two features whose record gives the same command, the first has it, and the other is said not to"() {
        given: 'a record no generation writes: the lock is changed by hand and sealed again'
        File app = new File(work, 'app')
        create(app, ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath])
        File lock = new File(app, '.grails/forge.lock.yml')
        String text = lock.getText('UTF-8')
        String first = text.substring(text.indexOf('features:\n- name: ') + 'features:\n- name: '.length()).readLines()[0]
        assert first != 'acme-audit'
        text = text.replaceFirst('(features:\n- name: [^\n]+\n  source: [^\n]+\n)', '$1  artefacts:\n  - name: audit-report\n    description: Another report\n' +
                '    template: R.groovy\n    to: src/main/groovy/{className}R.groovy\n')
        text = text.substring(0, text.lastIndexOf('seal:'))
        lock.setText(text + 'seal:\n  sha256: ' + StateYaml.sha256(text.getBytes(StandardCharsets.UTF_8)) + '\n', 'UTF-8')

        when:
        int exit = inDirectory(app) { ForgeCli.execute(new ForgeContext(), 'help') }

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains("create-audit-report Another report (feature ${first})")
        !out.toString().replaceAll(/\s+/, ' ').contains('create-audit-report Creates a class (audit-report) (feature acme-audit)')
        (out.toString() + err.toString()).replaceAll(/\s+/, ' ').contains('The command create-audit-report of the feature acme-audit is not a command here: ' +
                "create-audit-report is a command of the feature ${first}")
    }

    void "install-templates copies the templates of the command line, and leaves those of the features as they are"() {
        given:
        File app = new File(work, 'app')
        create(app, ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath])
        new File(app, IN_PROJECT + 'artefacts/AuditReport.groovy').text = '// ours\n' + REPORT

        when:
        int exit = inDirectory(app) { ForgeCli.execute(new ForgeContext(), 'install-templates', '--force') }

        then:
        exit == 0
        out.toString().contains('| Copied template to src/main/templates/artifacts/Controller.groovy')
        !out.toString().contains('features/acme-audit')
        new File(app, IN_PROJECT + 'artefacts/AuditReport.groovy').text == '// ours\n' + REPORT
        new File(app, IN_PROJECT + 'artefacts/AuditListener.groovy').text == LISTENER
    }

    private int create(File directory, List<String> arguments) {
        directory.mkdirs()
        return inDirectory(directory) { ForgeCli.execute(beanContext, (['create-app', 'com.example.shop', '--inplace'] + arguments) as String[]) }
    }

    private int inDirectory(File directory, Closure<Integer> run) {
        out.reset()
        err.reset()
        return inDirectoryOf(directory, run)
    }

    private <T> T inDirectoryOf(File directory, Closure<T> run) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return run.call()
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private static List<String> complete(CommandLine commandLine, String typed) {
        Terminal terminal = TerminalBuilder.builder().type(Terminal.TYPE_DUMB)
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build()
        try {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
            List<Candidate> candidates = []
            new CommandLineCompleter(commandLine.commandSpec).complete(reader, new DefaultParser().parse(typed, typed.length()), candidates)
            return candidates*.value()
        } finally {
            terminal.close()
        }
    }

    private File directoryPack(String descriptor = DESCRIPTOR) {
        File directory = new File(work, 'pack')
        new File(directory, 'templates/artefacts').mkdirs()
        new File(directory, 'feature.yml').setText(descriptor, 'UTF-8')
        TEMPLATES.each { String path, String text -> new File(directory, 'templates/' + path).setText(text, 'UTF-8') }
        return directory
    }

    private File repository() {
        File repository = new File(work, 'repository')
        File directory = new File(repository, 'com/acme/acme-audit-grails/2.3.0')
        directory.mkdirs()
        new ZipOutputStream(new FileOutputStream(new File(directory, 'acme-audit-grails-2.3.0.jar'))).withCloseable { ZipOutputStream zip ->
            (['META-INF/grails-forge/feature.yml': DESCRIPTOR] + TEMPLATES.collectEntries { String path, String text ->
                ['META-INF/grails-forge/templates/' + path, text]
            }).each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return repository
    }
}
