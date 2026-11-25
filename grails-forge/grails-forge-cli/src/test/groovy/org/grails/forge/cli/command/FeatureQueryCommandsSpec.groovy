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

import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandLineCompleter
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import org.grails.forge.feature.Feature
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.Parser
import org.jline.reader.impl.DefaultParser
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.springframework.context.ApplicationContext
import spock.lang.Unroll

/**
 * {@code feature search}, {@code feature info} with its other name {@code feature show}, and
 * {@code feature diff}, through the command line: in the directory of an application, where they
 * speak of it by its record, and outside a project and with {@code --type}, where they speak of a
 * new application of a type. None of them writes anything.
 */
class FeatureQueryCommandsSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        // the journal of a change, which feature add looks for, is kept in the home directory of whoever runs it
        home = Files.createTempDirectory('grailsforgehome').toFile()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        home.deleteDir()
    }

    void "outside a project feature search lists the features of a new web application that have the text, and says which it has by default"() {
        when:
        int exit = run('feature', 'search', 'CACHE')
        List<String> lines = out.toString().readLines()

        then: 'in whichever case'
        exit == 0
        err.toString().isEmpty()
        lines[0] ==~ /\| \d+ features of a new application of type web have CACHE in the name, the title, the description or the category:/
        lines.any { it.startsWith('    cache ') }
        lines[-1] == '| More of one feature: grails-forge-cli feature info FEATURE'

        when: 'a feature such an application has without being asked for it'
        reset()
        exit = run('feature', 'search', 'asset-pipeline-grails')

        then:
        exit == 0
        out.toString().readLines().findAll { it.startsWith('    ') }.join(' ').replaceAll(/\s+/, ' ').contains('(by default)')

        when: 'a text no feature has'
        reset()
        exit = run('feature', 'search', 'no-such-text-anywhere')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().trim() == '| No feature of a new application of type web has no-such-text-anywhere in its name, its title, its description or its category'
    }

    @Unroll
    void "feature search --json has every feature of a new #type application that has the text in its name, title, description or category, and no other"() {
        given:
        List<Feature> all = beanContext.getBean(ProjectGenerator).availableFeatures(applicationType, []).getFeatures().toList()
        List<String> expected = all.findAll { Feature feature ->
            [feature.name, feature.title, feature.description, feature.category].any { it != null && it.toLowerCase().contains(text) }
        }*.name.sort()

        when:
        int exit = run((['feature', 'search', text, '--json'] + (type == null ? [] : ['--type', type])) as String[])
        Map document = (Map) new JsonSlurper().parseText(out.toString())
        List<Map> features = (List<Map>) document.features

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines().size() == 1
        document.format == 1
        document.projectDirectory == null
        document.applicationType == (type ?: 'web')
        document.text == text
        !expected.isEmpty()
        features*.name == expected
        features.every { it.keySet() == ['name', 'title', 'description', 'category', 'preview', 'community', 'default', 'inApplication'] as Set }
        features.every { it.inApplication == null && it.default instanceof Boolean }

        where:
        type       | applicationType          | text
        null       | ApplicationType.WEB      | 'database'
        'web'      | ApplicationType.WEB      | 'quartz'
        'rest-api' | ApplicationType.REST_API | 'test'
        'plugin'   | ApplicationType.PLUGIN   | 'gorm'
    }

    void "in an application feature search speaks of it, says which features it has, and writes nothing"() {
        given:
        create(['--features', 'grails-quartz'])
        Map<String, String> before = tree()

        when:
        int exit = run('feature', 'search', 'quartz')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines[0] ==~ /\| \d+ features? of the application shop \(web\) ha(s|ve) quartz in the name, the title, the description or the category:/
        lines.findAll { it.startsWith('    ') }.join(' ').replaceAll(/\s+/, ' ').contains('(the application has it)')

        when:
        reset()
        exit = run('feature', 'search', 'cache', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        document.projectDirectory == dir.canonicalPath
        document.applicationType == 'web'
        ((List<Map>) document.features).find { it.name == 'cache' }.inApplication == false

        when: 'with a type it is a new application of that type that is spoken of'
        reset()
        exit = run('feature', 'search', 'quartz', '--json', '--type', 'rest-api')
        document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        document.projectDirectory == null
        document.applicationType == 'rest-api'
        ((List<Map>) document.features).every { it.inApplication == null }

        and:
        tree() == before
    }

    @Unroll
    void "#args is a mistake in the command line"() {
        when:
        int exit = run(args as String[])

        then:
        exit == 2
        out.toString().isEmpty()
        err.toString().contains(said)

        where:
        args                                           | said
        ['feature', 'search']                          | 'TEXT'
        ['feature', 'search', ' ']                     | 'The text to search the features by is one line of at most 100 characters'
        ['feature', 'search', 'x' * 101]               | 'The text to search the features by is one line of at most 100 characters'
        ['feature', 'search', 'cache', '--type', 'ear'] | 'ear is no type of application: --type is one of web, rest-api, web-plugin, plugin, rest-api-plugin'
        ['feature', 'info']                            | 'FEATURE'
        ['feature', 'info', 'cache', '--type', 'ear']  | 'ear is no type of application'
        ['feature', 'diff']                            | 'FEATURE'
        ['feature', 'diff', 'cache', '--type', 'ear']  | 'ear is no type of application'
        ['feature', 'diff', 'no-such-feature']         | 'no-such-feature'
        ['feature', 'diff', 'h2,postgres']             | 'postgres'
    }

    void "outside a project feature info says what a feature is, and what it changes in a new web application"() {
        when:
        int exit = run('feature', 'info', 'postgres')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines[0].startsWith('Feature: postgres')
        lines.any { it.startsWith('* Category: ') }
        lines.any { it.startsWith('* Application types: web, ') }
        lines.contains('* By default: no, in a new application of type web')
        !lines.any { it.startsWith('* In this application') }
        lines.find { it.startsWith('* Exclusive group database-driver: an application has one of it, so not this feature together with ') }.contains('h2')
        lines.contains('* Takes the place of: h2')

        and: 'the files, and the lines of the build files, as the generator writes them'
        lines.any { it ==~ /\* In a new application of type web it changes \d+ files:/ }
        int build = lines.indexOf('    changed  build.gradle')
        build > 0
        lines.subList(build + 1, lines.size()).takeWhile { it.startsWith('             ') }.any { it.trim().startsWith('+ ') && it.contains('postgresql') }
        lines.subList(build + 1, lines.size()).takeWhile { it.startsWith('             ') }.any { it.trim().startsWith('- ') && it.contains('h2') }
        lines.contains('    changed  grails-app/conf/application.yml')
    }

    void "feature show is feature info, and --json writes one document with what the text says"() {
        when:
        int exit = run('feature', 'info', 'postgres', '--json')
        String info = out.toString()
        reset()
        int shown = run('feature', 'show', 'postgres', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())
        Map feature = (Map) document.feature

        then:
        exit == 0
        shown == 0
        err.toString().isEmpty()
        out.toString() == info
        out.toString().readLines().size() == 1

        and:
        document.format == 1
        document.projectDirectory == null
        document.applicationType == 'web'
        feature.name == 'postgres'
        feature.title
        feature.description
        feature.category
        ((List<String>) feature.applicationTypes).contains('web')
        feature.requires instanceof List
        feature.exclusiveGroup == 'database-driver'
        ((List<String>) feature.conflictsWith).containsAll(['h2', 'mysql'])
        !((List<String>) feature.conflictsWith).contains('postgres')
        feature.default == false
        feature.inApplication == null
        feature.addable == null
        feature.containsKey('documentation') && feature.containsKey('thirdPartyDocumentation')
        feature.featurePack == null
        feature.replaces == ['h2']
        feature.contributionTo == 'new-application'
        ((List<Map>) feature.files).contains([path: 'build.gradle', change: 'changed'])
        Map build = ((List<Map>) feature.build).find { it.file == 'build.gradle' }
        ((List<String>) build.added).any { it.contains('postgresql') }
        ((List<String>) build.removed).any { it.contains('h2') }
    }

    void "what a default contributes is worked out against an application that was created without it"() {
        when:
        int exit = run('feature', 'info', 'mockito', '--json')
        Map feature = (Map) ((Map) new JsonSlurper().parseText(out.toString())).feature

        then:
        exit == 0
        feature.default == true
        feature.contributionTo == 'new-application'
        ((List<Map>) feature.files).contains([path: 'build.gradle', change: 'changed'])
        ((List<String>) ((List<Map>) feature.build).find { it.file == 'build.gradle' }.added).any { it.contains('mockito') }
        ((List<String>) ((List<Map>) feature.build).find { it.file == 'build.gradle' }.removed).isEmpty()

        when: 'a default no application is generated without'
        reset()
        exit = run('feature', 'info', 'h2')
        List<String> lines = out.toString().readLines()

        then: 'what the feature is, is said all the same'
        exit == 0
        err.toString().isEmpty()
        lines.contains('* By default: yes, in a new application of type web')
        lines.any { it.startsWith('* What the feature contributes to a new application of type web was not worked out: The feature h2 cannot be excluded') }
        !lines.any { it.contains('it changes') }
    }

    void "in an application feature info says what feature add would change there, which is what feature add --dry-run says"() {
        given:
        create([])
        Map<String, String> before = tree()

        when:
        int exit = run('feature', 'info', 'grails-quartz', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())
        Map feature = (Map) document.feature
        reset()
        int dryRun = run('feature', 'add', 'grails-quartz', '--dry-run', '--json')
        Map plan = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        dryRun == 0
        document.projectDirectory == dir.canonicalPath
        feature.inApplication == false
        feature.addable == true
        feature.notAddedBecause == null
        feature.contributionTo == 'application'

        and: 'the same files, each added or changed as there'
        ((List<Map>) feature.files).collectEntries { [it.path, it.change] } ==
                ((List<Map>) plan.files).findAll { it.action != 'keep' }.collectEntries { [it.path, it.action == 'add' ? 'added' : it.action == 'remove' ? 'removed' : 'changed'] }
        ((List<String>) ((List<Map>) feature.build).find { it.file == 'build.gradle' }.added).any { it.contains('quartz') }

        when: 'as text'
        reset()
        exit = run('feature', 'info', 'grails-quartz')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines.contains('* In this application: no')
        lines.any { it ==~ /\* In this application feature add would change \d+ files?:/ }
        lines.contains('    changed  build.gradle')
        lines.any { it.startsWith('             + ') && it.contains('quartz') }

        and: 'nothing was written'
        tree() == before
    }

    void "of a feature the application has, feature info says so, and what it changes in a new application of its type"() {
        given:
        create(['--features', 'grails-quartz'])
        Map<String, String> before = tree()

        when:
        int exit = run('feature', 'info', 'grails-quartz')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines.contains('* In this application: yes')
        lines.any { it ==~ /\* In a new application of type web it changes \d+ files?:/ }
        lines.any { it.startsWith('             + ') && it.contains('quartz') }
        tree() == before
    }

    void "feature info says when feature add adds a feature only with --replace, and why it does not add another"() {
        given:
        create(['--features', 'postgres', '--exclude-features', 'mockito'])

        when: 'a feature of the group of one that was asked for by name'
        int exit = run('feature', 'info', 'mysql', '--json')
        Map feature = (Map) ((Map) new JsonSlurper().parseText(out.toString())).feature

        then:
        exit == 0
        feature.replaceOption == true
        feature.replaces == ['postgres']
        feature.addable == true

        when:
        reset()
        exit = run('feature', 'info', 'mysql')

        then:
        exit == 0
        out.toString().readLines().contains('* Takes the place of: postgres, with --replace only: it was asked for by name')

        when: 'a feature the application was created without'
        reset()
        exit = run('feature', 'info', 'mockito')
        List<String> lines = out.toString().readLines()

        then: 'the reason feature add gives, and what it changes in a new application'
        exit == 0
        lines.find { it.startsWith('* feature add does not add it to this application: ') }.contains('left out with --exclude-features when the application was created')
        lines.any { it ==~ /\* In a new application of type web it changes \d+ files?:/ }
    }

    void "a name that is no feature is answered with the names that are like it, and one of another type of application with that type"() {
        given:
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        List<String> ofWeb = generator.availableFeatures(ApplicationType.WEB, []).getFeatures().toList()*.name
        String ofPluginOnly = generator.availableFeatures(ApplicationType.PLUGIN, []).getFeatures().toList()*.name.find { !ofWeb.contains(it) }

        when:
        int exit = run('feature', 'info', 'quartz')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().size() == 1
        err.toString().startsWith('| Error There is no feature quartz for a new application of type web. Did you mean: grails-quartz')
        err.toString().contains('grails-forge-cli feature search TEXT lists the features')

        when: 'for a tool'
        reset()
        exit = run('feature', 'info', 'quartz', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 1
        document.feature == null
        document.error.toString().startsWith('There is no feature quartz')

        when:
        reset()
        exit = ofPluginOnly == null ? 1 : run('feature', 'info', ofPluginOnly)

        then:
        exit == 1
        ofPluginOnly == null || err.toString().contains("${ofPluginOnly} is no feature of a new application of type web: it is a feature of applications of type ")

        when:
        reset()
        exit = ofPluginOnly == null ? 0 : run('feature', 'info', ofPluginOnly, '--type', 'plugin')

        then:
        exit == 0
        ofPluginOnly == null || out.toString().startsWith("Feature: ${ofPluginOnly}")
    }

    void "the feature of a feature pack that is not part of Grails is said with its pack"() {
        given:
        File pack = new File(home, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').setText(CreateAppFeaturePackSpec.DESCRIPTOR, 'UTF-8')
        new File(pack, 'templates/audit.txt').setText('kept as it is', 'UTF-8')

        when:
        int exit = run('feature', 'info', 'acme-audit', '--feature-pack-dir', pack.absolutePath)
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines[0] == 'Feature: acme-audit (Acme Audit Trail)'
        lines.contains('Adds the Acme audit plugin')
        lines.any { it.startsWith('* Feature pack: acme-audit, of ') && it.contains(pack.canonicalFile.name) }
        lines.any { it.startsWith('    added    grails-app/conf/') && it.endsWith('/audit.txt') }
        lines.any { it.startsWith('             + ') && it.contains('com.acme:acme-audit-grails:2.3.0') }

        when: 'and found by feature search'
        reset()
        exit = run('feature', 'search', 'acme', '--json', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 0
        ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).features)*.name == ['acme-audit']
    }

    void "feature diff writes the diff feature add --diff shows, and nothing else, and changes nothing"() {
        given:
        create([])
        Map<String, String> before = tree()

        when:
        int exit = run('feature', 'diff', 'grails-quartz')
        List<String> diff = out.toString().readLines()
        String notes = err.toString()
        reset()
        int add = run('feature', 'add', 'grails-quartz', '--diff')
        List<String> shown = out.toString().readLines()

        then:
        exit == 0
        add == 0
        notes.isEmpty()
        diff.first().startsWith('--- ')
        diff.any { it.startsWith('+') && it.contains('quartz') }

        and: 'the very lines'
        int from = shown.findIndexOf { it.startsWith('--- ') }
        diff == shown.subList(from, shown.size() - 1)
        shown.last() == '| Nothing was changed'

        and:
        tree() == before

        when: 'the feature is added, and there is nothing left to show'
        reset()
        assert run('feature', 'add', 'grails-quartz') == 0
        reset()
        exit = run('feature', 'diff', 'grails-quartz')

        then:
        exit == 0
        out.toString().isEmpty()
        err.toString().contains('| The application already has the feature grails-quartz')
        err.toString().contains('| Nothing would be changed')
    }

    void "feature diff ends as feature add would: with 1 for a file in the way, and with 2 for a feature that is only added with --replace"() {
        given:
        create(['--features', 'postgres'])
        new File(dir, 'gradle').mkdirs()
        new File(dir, 'gradle/asciidoc.gradle').text = '// ours\n'
        Map<String, String> before = tree()

        when: 'a file the feature adds is there already'
        int exit = run('feature', 'diff', 'asciidoctor')
        reset()
        int dryRun = run('feature', 'add', 'asciidoctor', '--dry-run')
        reset()
        exit = run('feature', 'diff', 'asciidoctor')

        then:
        dryRun == 1
        exit == 1
        out.toString().contains('rejected: gradle/asciidoc.gradle')
        err.toString().contains('| Error The feature was not added: 1 file cannot be written without losing what the application has')

        when: 'a feature that takes the place of one that was asked for by name'
        reset()
        exit = run('feature', 'diff', 'mysql')

        then:
        exit == 2
        out.toString().isEmpty()
        err.toString().contains('--replace')

        when:
        reset()
        exit = run('feature', 'diff', 'mysql', '--replace')

        then: 'what is replaced is said where the diff is not'
        exit == 0
        err.toString().contains('postgres')
        out.toString().readLines().first().startsWith('--- ')
        out.toString().readLines().any { it.startsWith('+') && it.contains('mysql') }
        out.toString().readLines().any { it.startsWith('-') && it.contains('postgresql') }

        and:
        tree() == before
    }

    void "outside a project, and with --type, feature diff shows what the features change in a new application"() {
        when:
        int exit = run('feature', 'diff', 'postgres')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        err.toString().isEmpty()
        lines.contains('--- build.gradle')
        lines.contains('+++ build.gradle')
        lines.any { it.startsWith('+') && it.contains('postgresql') }
        lines.any { it.startsWith('-') && it.contains('h2') }
        !lines.any { it.contains('.grails/') }

        when: 'a feature such an application has by default'
        reset()
        exit = run('feature', 'diff', 'h2', '--type', 'rest-api')

        then:
        exit == 0
        out.toString().isEmpty()
        err.toString().contains('| The feature changes nothing in a new application of type rest-api: it has it by default')

        when: 'in an application, for a new one of a type'
        reset()
        create(['--features', 'postgres'])
        Map<String, String> before = tree()
        exit = run('feature', 'diff', 'postgres', '--type', 'web')

        then: 'the application that is there is not looked at'
        exit == 0
        out.toString().readLines().contains('+++ build.gradle')
        tree() == before
    }

    void "in a project without a record feature diff says what feature add says, and that --type shows a new application"() {
        given:
        create([])
        new File(dir, '.grails').deleteDir()

        when:
        int exit = run('feature', 'diff', 'grails-quartz')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().contains('There is no application here that a feature can be added to: .grails/project.yml is missing')
        err.toString().contains('With --type, feature diff shows what the features change in a new application of that type')

        when:
        reset()
        exit = run('feature', 'diff', 'grails-quartz', '--type', 'web')

        then:
        exit == 0
        out.toString().readLines().any { it.startsWith('+') && it.contains('quartz') }
    }

    @Unroll
    void "#args shows the help and does not start the application that generates"() {
        given:
        AtomicInteger started = new AtomicInteger()
        ForgeContext broken = new ForgeContext({
            started.incrementAndGet()
            throw new IllegalStateException('the generator is not to be started')
        } as Supplier<ApplicationContext>)

        when:
        int exit = ForgeCli.execute(broken, args as String[])

        then:
        exit == 0
        started.get() == 0
        out.toString().readLines()[0].startsWith(usage)

        where:
        args                            | usage
        ['feature', 'search', '--help'] | 'Usage: grails-forge-cli feature search '
        ['feature', 'info', '--help']   | 'Usage: grails-forge-cli feature info '
        ['feature', 'show', '-h']       | 'Usage: grails-forge-cli feature info '
        ['feature', 'diff', '--help']   | 'Usage: grails-forge-cli feature diff '
        ['help', 'feature', 'show']     | 'Usage: grails-forge-cli feature info '
        ['help', 'feature']             | 'Usage: grails-forge-cli feature '
        ['plugin', '--help']            | 'Usage: grails-forge-cli plugin '
        ['plugin', 'list', '--help']    | 'Usage: grails-forge-cli plugin list '
        ['plugin', 'search', '--help']  | 'Usage: grails-forge-cli plugin search '
        ['plugin', 'info', '--help']    | 'Usage: grails-forge-cli plugin show '
        ['help', 'plugin', 'show']      | 'Usage: grails-forge-cli plugin show '
    }

    void "the commands are in help --json with their options, found by short forms, and take the options every command takes"() {
        when:
        int exit = run('help', '--json')
        Map feature = ((List<Map>) ((Map) new JsonSlurper().parseText(out.toString())).commands).find { it.name == 'feature' }
        Map<String, Map> commands = ((List<Map>) feature.commands).collectEntries { [it.name, it] }

        then:
        exit == 0
        commands.keySet() == ['add', 'list', 'search', 'info', 'diff'] as Set
        commands.info.aliases == ['show']
        optionNames(commands.search).containsAll(['--json', '--type', '--feature-pack-dir', '--offline'])
        optionNames(commands.info).containsAll(['--json', '--type', '--feature-pack-dir', '--offline'])
        optionNames(commands.diff).containsAll(['--replace', '--type', '--feature-pack-dir', '--offline'])
        !optionNames(commands.diff).contains('--json')
        ((List<Map>) commands.search.parameters)*.label == ['TEXT']
        ((List<Map>) commands.info.parameters)*.label == ['FEATURE']
        ((List<Map>) commands.diff.parameters)*.label == ['FEATURE']
        commands.values().every { it.project == (it.name in ['add', 'list']) }

        when: 'by what is short for the names, plain and never waiting for what is typed'
        reset()
        exit = run('feature', 'se', 'quartz', '--plain-output', '--non-interactive')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines().any { it.startsWith('    grails-quartz ') }
        !out.toString().contains('\u001b[')

        when:
        reset()
        exit = run('--plain-output', '--non-interactive', 'feature', 'i', 'grails-quartz')

        then:
        exit == 0
        out.toString().startsWith('Feature: grails-quartz')
        !out.toString().contains('\u001b[')
    }

    void "the names of features and of the commands are completed in the console"() {
        given:
        Terminal terminal = TerminalBuilder.builder().type(Terminal.TYPE_DUMB).streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()).build()

        expect: 'the commands, by each of their names'
        complete(terminal, 'feature ').containsAll(['add', 'list', 'search', 'info', 'show', 'diff'])
        complete(terminal, 'plugin ').containsAll(['list', 'search', 'show', 'info'])
        complete(terminal, 'feature search --').containsAll(['--json', '--type'])
        complete(terminal, 'feature info --type ').containsAll(['web', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin'])

        when: 'outside a project'
        List<String> info = complete(terminal, 'feature info ')
        List<String> show = complete(terminal, 'feature show ')
        List<String> diff = complete(terminal, 'feature diff ')

        then:
        info.containsAll(['cache', 'grails-quartz', 'postgres', 'asset-pipeline-grails'])
        show == info
        diff.containsAll(['cache', 'grails-quartz', 'postgres'])

        when: 'in an application that has some of them'
        create(['--features', 'grails-quartz'])
        info = complete(terminal, 'feature info ')
        diff = complete(terminal, 'feature diff ')

        then: 'feature info says something of a feature the application has too, and feature diff has nothing to show of it'
        info.containsAll(['cache', 'grails-quartz', 'asset-pipeline-grails'])
        diff.contains('cache')
        !diff.contains('grails-quartz')

        cleanup:
        terminal?.close()
    }

    private int run(String... args) {
        return ForgeCli.execute(beanContext, args)
    }

    private void reset() {
        out.reset()
        err.reset()
    }

    private void create(List<String> arguments) {
        assert CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop', '--inplace'] + arguments) as String[]) == 0
        reset()
    }

    private static List<String> optionNames(Map command) {
        return ((List<Map>) command.options).collectMany { (List<String>) it.names }
    }

    private List<String> complete(Terminal terminal, String typed) {
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build()
        List<Candidate> candidates = []
        new CommandLineCompleter(ForgeCli.createCommandLine(beanContext, true).commandSpec)
                // as the prompt asks on the TAB key: a word that is begun after the last space
                .complete(reader, new DefaultParser().parse(typed, typed.length(), Parser.ParseContext.COMPLETE), candidates)
        return candidates*.value()
    }

    private Map<String, String> tree() {
        Map<String, String> entries = new TreeMap<>()
        Files.walk(dir.toPath()).withCloseable { stream ->
            stream.each { java.nio.file.Path path ->
                entries.put(dir.toPath().relativize(path).join('/'), Files.isDirectory(path) ? 'directory' :
                        MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(path)).encodeHex().toString())
            }
        }
        return entries
    }
}
