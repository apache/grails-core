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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import groovy.json.JsonSlurper
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import spock.lang.Unroll

/**
 * The options of features, which a feature pack declares as {@code parameters} and a command line gives
 * with {@code --feature-option}: when an application is created, in its record, and in the commands that
 * generate it again from the record.
 */
class FeatureOptionSpec extends CommandSpec implements CommandFixture {

    static final String PLUGIN = 'com.acme:acme-audit-grails:2.3.0'

    static final String DESCRIPTOR = CreateAppFeaturePackSpec.DESCRIPTOR.replace("files:\n  - from: audit.txt\n    to: 'grails-app/conf/{packagePath}/audit.txt'\n", '') + '''\
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
        configuration:
          - values:
              acme.audit.console: true
            when: { parameter: { console: true } }
        files:
          - from: audit.properties.gsp
            to: grails-app/conf/audit.properties
        '''.stripIndent()

    static final String TEMPLATE = 'audit.store=${parameters.store}\naudit.retention=${parameters.retention}\n'

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
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        work = Files.createTempDirectory('grailsforgeoptions').toFile()
        home = new File(work, 'home')
        home.mkdirs()
        previousHome = System.getProperty('user.home')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.home', previousHome)
        work.deleteDir()
    }

    @Unroll
    void "an application is created with the options of the feature of the pack of a #from, and generated again with them from its record"() {
        given:
        File app = new File(work, 'app')
        List<String> pack = from == 'directory' ? ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath] :
                ['--features', PLUGIN, '--feature-pack-repository', repository().toPath().toAbsolutePath().normalize().toUri().toString()]
        List<String> again = from == 'directory' ? pack.drop(2) : pack.drop(2)

        when:
        int exit = create(app, pack + ['--feature-option', 'acme-audit.store=collection', '--feature-option=acme-audit.console=true'])

        then: 'the template and the condition have the values that were given, and the default of the one that was not'
        exit == 0
        new File(app, 'grails-app/conf/audit.properties').text == 'audit.store=collection\naudit.retention=90\n'
        new File(app, 'grails-app/conf/application.yml').text.contains('acme')

        and: 'what the feature takes is said with what its pack adds'
        out.toString().contains('    options, given as --feature-option acme-audit.NAME=VALUE: store=table|collection (default table), ' +
                'retention=text (default 90), console=true|false (default false)')

        and: 'the record has what was given'
        new File(app, '.grails/project.yml').text.contains("  featureOptions:\n    acme-audit:\n      console: 'true'\n      store: collection\n")
        new File(app, '.grails/forge.lock.yml').text.contains("  featureOptions:\n    acme-audit:\n      console: 'true'\n      store: collection\n")

        when: 'the record is shown'
        exit = inDirectory(app) { CliRunner.run(ProjectShowCommand, beanContext) }

        then: 'with the options, also in the command line that creates such an application'
        exit == 0
        out.toString().readLines().any { it ==~ /\s+feature option\s+acme-audit\.console=true/ }
        out.toString().readLines().any { it ==~ /\s+feature option\s+acme-audit\.store=collection/ }
        out.toString().contains('--feature-option acme-audit.console=true --feature-option acme-audit.store=collection')

        when:
        exit = inDirectory(app) { CliRunner.run(ProjectShowCommand, beanContext, '--json') }
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        document.featureOptions == ['acme-audit': [console: 'true', store: 'collection']]
        (document.command.arguments as List<String>).join(' ').contains('--feature-option acme-audit.console=true --feature-option acme-audit.store=collection')

        when: 'the application is generated again from its record, to compare'
        exit = inDirectory(app) { CliRunner.run(ProjectRegenerateCommand, beanContext, (['--diff', '--json'] + again) as String[]) }
        document = new JsonSlurper().parseText(out.toString().readLines().last()) as Map

        then: 'it is what was generated: the options of the record were given to the pack again'
        exit == 0
        document.hasChanges == false

        when: 'a feature is added, which generates it again with the pack too'
        exit = inDirectory(app) { CliRunner.run(FeatureAddCommand, beanContext, (['grails-quartz'] + again) as String[]) }

        then: 'and what the options gave is still there'
        exit == 0
        new File(app, 'grails-app/conf/audit.properties').text == 'audit.store=collection\naudit.retention=90\n'
        new File(app, '.grails/project.yml').text.contains("  featureOptions:\n    acme-audit:\n      console: 'true'\n      store: collection\n")
        new File(app, 'build.gradle').text.contains('grails-quartz')

        where:
        from << ['directory', 'jar']
    }

    @Unroll
    void "an option that is none is a mistake in the command line, and nothing is created: #what"() {
        given:
        File app = new File(work, 'app')

        when:
        int exit = create(app, ['--features', 'acme-audit', '--feature-pack-dir', directoryPack().absolutePath] + arguments)

        then:
        exit == 2
        err.toString().contains(message)
        !new File(app, 'build.gradle').exists()

        where:
        what                             | arguments                                             || message
        'not written as one'             | ['--feature-option', 'store=collection']              || "--feature-option takes FEATURE.NAME=VALUE: the name of a feature, a dot, the name of an option of that feature and its value, as in acme-audit.store=collection, but is store=collection"
        'given twice'                    | ['--feature-option', 'acme-audit.store=table', '--feature-option', 'acme-audit.store=collection'] || '--feature-option gives the option store of the feature acme-audit two values'
        'of a feature that is not there' | ['--feature-option', 'acme-other.store=table']        || 'An option was given for the feature acme-other, which the application does not have'
        'the feature does not have'      | ['--feature-option', 'acme-audit.stor=table']         || 'The feature acme-audit has no option stor: its options are store=table|collection (default table), retention=text (default 90), console=true|false (default false)'
        'a value it cannot have'         | ['--feature-option', 'acme-audit.store=file']         || "The option store of the feature acme-audit must be one of [table, collection], but is 'file'"
        'a boolean that is none'         | ['--feature-option', 'acme-audit.console=yes']        || "The option console of the feature acme-audit must be true or false, but is 'yes'"
    }

    void "the list of the features says what the feature of a pack that was given takes"() {
        when:
        int exit = create(new File(work, 'app'), ['--list-features', '--feature-pack-dir', directoryPack().absolutePath])
        List<String> lines = out.toString().readLines()
        int said = lines.indexOf('| The feature acme-audit takes options, each given as --feature-option acme-audit.NAME=VALUE:')

        then:
        exit == 0
        said > 0
        lines.drop(said + 1) == ['    store=table|collection (default table)  Where audit events are kept',
                                 '    retention=text (default 90)',
                                 '    console=true|false (default false)']
    }

    void "the usage of the create commands and of feature add has the option"() {
        when:
        int exit = CliRunner.run(command, beanContext, '--help')

        then:
        exit == 0
        out.toString().contains('--feature-option=FEATURE.NAME=VALUE')

        where:
        command << [CreateAppCommand, CreatePluginCommand, FeatureAddCommand]
    }

    @Unroll
    void "feature add refuses an option that is none: #what"() {
        given:
        File app = new File(work, 'app')
        File pack = directoryPack()
        create(app, ['--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath])
        String before = new File(app, '.grails/project.yml').text

        when:
        int exit = inDirectory(app) { CliRunner.run(FeatureAddCommand, beanContext, (['grails-quartz', '--feature-pack-dir', pack.absolutePath] + arguments) as String[]) }

        then:
        exit == 2
        err.toString().contains(message)
        new File(app, '.grails/project.yml').text == before
        !new File(app, 'build.gradle').text.contains('grails-quartz')

        where:
        what                                | arguments                                        || message
        'of a feature the application has'  | ['--feature-option', 'acme-audit.store=collection'] || 'An option was given for the feature acme-audit, which the application has: adding a feature does not change what the application was created with'
        'of a feature that takes none'      | ['--feature-option', 'grails-quartz.threads=4']  || 'The feature grails-quartz has no option threads: it takes no options'
        'of a feature that is not added'    | ['--feature-option', 'acme-other.store=table']   || 'An option was given for the feature acme-other, which the application does not have'
        'not written as one'                | ['--feature-option', 'quartz']                   || '--feature-option takes FEATURE.NAME=VALUE'
    }

    private int create(File directory, List<String> arguments) {
        directory.mkdirs()
        return inDirectory(directory) { CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop', '--inplace'] + arguments) as String[]) }
    }

    private int inDirectory(File directory, Closure<Integer> run) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', directory.absolutePath)
        try {
            out.reset()
            err.reset()
            return run.call()
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private File directoryPack() {
        File directory = new File(work, 'pack')
        new File(directory, 'templates').mkdirs()
        new File(directory, 'feature.yml').setText(DESCRIPTOR, 'UTF-8')
        new File(directory, 'templates/audit.properties.gsp').setText(TEMPLATE, 'UTF-8')
        return directory
    }

    private File repository() {
        File repository = new File(work, 'repository')
        File directory = new File(repository, 'com/acme/acme-audit-grails/2.3.0')
        directory.mkdirs()
        new ZipOutputStream(new FileOutputStream(new File(directory, 'acme-audit-grails-2.3.0.jar'))).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml'                       : DESCRIPTOR,
             'META-INF/grails-forge/templates/audit.properties.gsp': TEMPLATE].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        return repository
    }
}
