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
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.io.ConsoleOutput
import spock.lang.Unroll

class CreateAppSelectionSpec extends CommandSpec implements CommandFixture {

    static final String NEO4J_WITH_HIBERNATE7 = 'gorm-neo4j cannot be combined with gorm-hibernate7: an application with GORM for Neo4j uses ' +
            'grails-neo4j-bom, which manages Hibernate 5, and no Grails BOM manages the Neo4j driver of GORM for Neo4j together with Hibernate 7. ' +
            'Select gorm-hibernate5, or no Hibernate implementation, with gorm-neo4j.'

    static final String NEO4J_WITH_MICRONAUT = 'gorm-neo4j cannot be combined with grails-micronaut: an application with GORM for Neo4j uses ' +
            'grails-neo4j-bom, and no Grails BOM manages the Neo4j driver of GORM for Neo4j together with Micronaut.'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "an application is created without the defaults that are excluded, and keeps a record of them"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--exclude-features', 'mockito,scaffolding')

        then:
        exit == 0
        String buildGradle = new File(dir, 'shop/build.gradle').text
        !buildGradle.contains('mockito')
        !buildGradle.contains('grails-scaffolding')
        buildGradle.contains('spock')

        and:
        new File(dir, 'shop/.grails/project.yml').text.contains('excludedFeatures:\n- name: mockito\n- name: scaffolding\n')
        !new File(dir, 'shop/.grails/forge.lock.yml').text.contains('- name: mockito\n')

        and: 'which the commands of the application read back'
        CodeGenConfig config = CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP)
        config.excludedFeatures == ['mockito', 'scaffolding']
        !config.features.contains('mockito')
        config.features.contains('spock')
    }

    void "an application created without the option has the defaults and no record of exclusions"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop')

        then:
        exit == 0
        new File(dir, 'shop/build.gradle').text.contains('mockito')
        new File(dir, 'shop/.grails/project.yml').text.contains('excludedFeatures: []\n')
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).excludedFeatures == []
    }

    void "the option can be given more than once, and with the other create commands"() {
        when:
        int exit = CliRunner.run(CreateRestApiCommand, beanContext, 'com.example.api', '--exclude-features', 'mockito', '--exclude-features', 'spock')

        then:
        exit == 0
        new File(dir, 'api/.grails/project.yml').text.contains('excludedFeatures:\n- name: mockito\n- name: spock\n')
    }

    @Unroll
    void "excluding #excluded with #features selected is refused, and nothing is created: #message"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', features, '--exclude-features', excluded)

        then: 'a mistake in the command line, as an option that does not exist is'
        exit == 2
        err.toString().contains(message)
        err.toString().contains('Usage:')
        !new File(dir, 'shop').exists()

        where:
        features   | excluded             || message
        'postgres' | 'postgres'           || 'The feature postgres is both selected and excluded'
        'postgres' | 'no-such-feature'    || 'The excluded feature does not exist: no-such-feature'
        'postgres' | 'logback'            || 'The feature logback cannot be excluded: it is not a feature that is selected by name'
        'postgres' | 'gorm-hibernate5'    || 'The feature gorm-hibernate5 cannot be excluded: it is the Grails Data implementation of the application. Choose another one (--data), or leave the exclusion out'
        'cache'    | 'gorm-hibernate5,h2' || 'The feature gorm-hibernate5 cannot be excluded: it is the Grails Data implementation of the application. Choose another one (--data), or leave the exclusion out'
        'cache'    | 'zzz-none,aaa-none'  || 'The excluded features do not exist: aaa-none, zzz-none'
        'zzz-none,cache,aaa-none' | 'mockito' || 'The requested features do not exist: aaa-none, zzz-none'
        'cache'    | 'h2'                 || 'The feature h2 cannot be excluded: the feature gorm-hibernate5 requires it'
        'cache'    | 'mockito,spock'      || 'The feature spock cannot be excluded: the feature geb-with-testcontainers requires it'
    }

    @Unroll
    void "an option that is given and does not agree with the features is refused, and nothing is created: #arguments"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop'] + arguments) as String[])

        then:
        exit == code
        err.toString().contains(message)
        !new File(dir, 'shop').exists()

        where:
        arguments                                                         || code | message
        ['--data', 'hibernate5', '--features', 'gorm-hibernate7']         || 2    | 'The option --data hibernate5 does not agree with the features that are selected or excluded: the application would be made without the feature gorm-hibernate5'
        ['--gorm', 'hibernate', '--features', 'gorm-hibernate7']          || 2    | 'The option --gorm hibernate does not agree with the features that are selected or excluded: the application would be made without the feature gorm-hibernate5'
        ['-g', 'HIBERNATE5', '--features', 'gorm-hibernate7']             || 2    | 'The option -g HIBERNATE5 does not agree with the features that are selected or excluded'
        ['--data=hibernate5', '--features', 'gorm-hibernate7']            || 2    | 'The option --data=hibernate5 does not agree with the features that are selected or excluded'
        ['--data', 'hibernate5', '--exclude-features', 'gorm-hibernate5,h2'] || 2 | 'The feature gorm-hibernate5 cannot be excluded: it is the Grails Data implementation of the application'
        ['--data', 'mongodb', '--exclude-features', 'gorm-mongodb']       || 2    | 'The feature gorm-mongodb cannot be excluded: it is the Grails Data implementation of the application'
        ['--jdk', '25', '--reloading', 'devtools', '--features', 'grails-micronaut'] || 2    | 'The option --reloading devtools does not agree with the features that are selected or excluded: the application would be made without Spring Boot DevTools'
        ['--data', 'hibernate7', '--features', 'gorm-hibernate5']         || 2    | 'Only one Grails Data for Hibernate implementation can be selected: gorm-hibernate5 or gorm-hibernate7'
        ['--data', 'neo4j', '--features', 'gorm-mongodb']                 || 2    | 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'
        ['--data', 'mongodb', '--features', 'gorm-neo4j']                 || 2    | 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'
        ['--servlet', 'jetty', '--features', 'spring-boot-starter-tomcat'] || 2   | 'The requested feature does not exist: spring-boot-starter-tomcat'
        ['--features', 'postgres,mysql']                                  || 2    | 'There can only be one of the following features selected: [mysql, postgres]'
    }

    @Unroll
    void "a default gives way to a selected feature where an option that is given does not: #arguments"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop'] + arguments) as String[])

        then:
        exit == 0
        List<String> features = CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).features
        features.containsAll(has)
        !features.any { it in hasNot }

        where:
        arguments                                                  || has                                    | hasNot
        ['--features', 'gorm-hibernate7']                          || ['gorm-hibernate7', 'h2']              | ['gorm-hibernate5']
        ['--data', 'hibernate7', '--features', 'postgres']         || ['gorm-hibernate7', 'postgres']        | ['gorm-hibernate5', 'h2']
        ['--jdk', '25', '--features', 'grails-micronaut']          || ['grails-micronaut']                   | ['spring-boot-devtools']
        ['--jdk', '25', '--reloading', 'none', '--features', 'grails-micronaut'] || ['grails-micronaut']                   | ['spring-boot-devtools']
        ['--data', 'mongodb', '--features', 'gorm-hibernate5']     || ['gorm-mongodb', 'gorm-hibernate5', 'h2'] | []
        ['--servlet', 'jetty']                                     || ['spring-boot-starter-jetty']          | ['spring-boot-starter-tomcat']
        ['--servlet', 'none', '--reloading', 'none']               || ['gorm-hibernate5']                    | ['spring-boot-starter-tomcat', 'spring-boot-devtools']
    }

    @Unroll
    void "an option that asks for none is a choice too: #option none with a feature that requires #required"() {
        given:
        File pack = new File(dir, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').text = CreateAppFeaturePackSpec.DESCRIPTOR + "requires: [${required}]\n"
        new File(pack, 'templates/audit.txt').text = 'kept as it is'

        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', 'acme-audit', option, 'none',
                '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 2
        err.toString().contains("The option ${option} none does not agree with the features that are selected or excluded: " +
                "the application would be made with the feature ${required} (acme-audit requires ${required}). Leave out the option, or the feature that requires it")
        !new File(dir, 'shop').exists()

        when: 'without the option there is its default, which stands aside for what is required'
        int given = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', 'acme-audit',
                '--feature-pack-dir', pack.absolutePath)

        then:
        given == 0
        new File(dir, 'shop/.grails/project.yml').text.contains("${recorded}\n")
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).features.contains(required)
        !CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).features.contains(notThere)

        where:
        option        | required                    || recorded                                 | notThere
        '--servlet'   | 'spring-boot-starter-jetty' || '  servlet: jetty'                         | 'spring-boot-starter-tomcat'
        '--reloading' | 'jrebel'                    || '  reloading: jrebel'                      | 'spring-boot-devtools'
    }

    @Unroll
    void "what is recorded of an option that was not given is what the application has: #arguments"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop'] + arguments) as String[])

        then:
        exit == 0
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())
        state.manifest.options.servlet.name == 'spring-boot-starter-tomcat'
        state.manifest.options.data.name == gorm
        state.manifest.options.reloading.name == reloading
        state.lock.featureNames.contains(gorm)

        and: 'which is what the commands of the application read'
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).reloading.name == reloading

        where:
        arguments                                          || gorm              | reloading
        []                                                 || 'gorm-hibernate5' | 'devtools'
        ['--features', 'gorm-hibernate7']                  || 'gorm-hibernate7' | 'devtools'
        ['--features', 'gorm-hibernate7,postgres']         || 'gorm-hibernate7' | 'devtools'
        ['--jdk', '25', '--features', 'grails-micronaut']  || 'gorm-hibernate5' | 'none'
        ['--data', 'mongodb', '--features', 'gorm-hibernate7'] || 'gorm-mongodb' | 'devtools'
    }

    @Unroll
    void "through the command line as it is run, #arguments is refused in one bounded line that is no markup"() {
        when:
        int exit = ForgeCli.execute(beanContext, (['create-app', 'com.example.shop'] + arguments) as String[])
        List<String> lines = err.toString().readLines()

        then:
        exit == 2
        lines[0] == message
        lines[1].startsWith('Usage: ')
        !err.toString().contains('Exception')
        !err.toString().contains('\u001b')
        !new File(dir, 'shop').exists()

        where:
        arguments                                           || message
        ['--exclude-features', '@|red x|@']                 || 'The excluded feature does not exist: @ |red x|@'
        ['--exclude-features', '@|bogus x|@']               || 'The excluded feature does not exist: @ |bogus x|@'
        ['--features', '@|red x|@']                         || 'The requested feature does not exist: @ |red x|@'
        ['--features', '@|bogus x|@']                       || 'The requested feature does not exist: @ |bogus x|@'
        ['--exclude-features', 'a\u001b[31mb\nsecond']      || 'The excluded feature does not exist: a?[31mb?second'
        ['--features', 'a\u001b[31mb\nsecond']              || 'The requested feature does not exist: a?[31mb?second'
        ['--exclude-features', 'x' * 3000]                  || 'The excluded feature does not exist: ' + ('x' * 80) + '...'
        ['--features', 'x' * 3000]                          || 'The requested feature does not exist: ' + ('x' * 80) + '...'
        ['--exclude-features', ('y' * 3000) + ':b']         || '--exclude-features takes the names of features, and ' + ('y' * 80) + '... is not one: the feature of a plugin is excluded by the name of the feature, not by the coordinates of the plugin'
        ['--exclude-features', 'a:b:1@|red x|@\u001b[0m']   || '--exclude-features takes the names of features, and a:b:1@ |red x|@?[0m is not one: the feature of a plugin is excluded by the name of the feature, not by the coordinates of the plugin'
        ['--exclude-features', '']                          || 'The excluded feature does not exist: (empty)'
        ['--exclude-features', 'logback']                   || 'The feature logback cannot be excluded: it is not a feature that is selected by name'
        ['--features', 'postgres,mysql']                    || 'There can only be one of the following features selected: [mysql, postgres]'
        ['--gorm', 'hibernate', '--features', 'gorm-hibernate7'] || 'The option --gorm hibernate does not agree with the features that are selected or excluded: the application would be made without the feature gorm-hibernate5. Leave out the option, or the feature that takes its place'
    }

    void "a failure that is no mistake in the command line is not reported as one"() {
        given: 'a pack whose template fails when the application is made'
        File pack = new File(dir, 'pack')
        new File(pack, 'templates').mkdirs()
        new File(pack, 'feature.yml').text = CreateAppFeaturePackSpec.DESCRIPTOR.replace('from: audit.txt', 'from: audit.txt.gsp')
        new File(pack, 'templates/audit.txt.gsp').text = '<% throw new IllegalStateException("the template failed") %>'

        when:
        int exit = ForgeCli.execute(beanContext, 'create-app', 'com.example.shop', '--features', 'acme-audit', '--feature-pack-dir', pack.absolutePath)

        then:
        exit == 1
        !err.toString().contains('Usage: ')
    }

    @Unroll
    void "the list of features is held to the exclusions too: #excluded"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'create-app', '--list-features', '--exclude-features', excluded)

        then:
        exit == 2
        err.toString().readLines()[0] == message
        !out.toString().contains('Available Features')

        where:
        excluded  || message
        'no-such' || 'The excluded feature does not exist: no-such'
        'a:b:c'   || '--exclude-features takes the names of features, and a:b:c is not one: the feature of a plugin is excluded by the name of the feature, not by the coordinates of the plugin'
        'h2'      || 'The feature h2 cannot be excluded: the feature gorm-hibernate5 requires it'
    }

    void "the list of features marks the defaults without those that are excluded"() {
        when:
        int all = ForgeCli.execute(beanContext, 'create-app', '--list-features')
        String complete = out.toString()
        out.reset()
        int without = ForgeCli.execute(beanContext, 'create-app', '--list-features', '--exclude-features', 'mockito')
        String excluded = out.toString()

        then:
        all == 0
        without == 0
        complete =~ /mockito \(\+\)/
        !(excluded =~ /mockito \(\+\)/)
        excluded =~ /mockito\s/
        excluded =~ /spock \(\+\)/
    }

    @Unroll
    void "an application with GORM for Neo4j is created with grails-neo4j-bom as its one Grails BOM: #arguments"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop'] + arguments) as String[])

        then:
        exit == 0
        String buildGradle = new File(dir, 'shop/build.gradle').text
        buildGradle.contains('implementation platform("org.apache.grails:grails-neo4j-bom:$grailsVersion")')
        buildGradle.contains('classpath platform("org.apache.grails:grails-neo4j-bom:$grailsVersion")')
        !buildGradle.contains('org.apache.grails:grails-bom:')
        buildGradle.contains('implementation "org.apache.grails:grails-data-neo4j"')
        buildGradle.contains('implementation "org.apache.grails:grails-data-hibernate5"') == hibernate5

        where:
        arguments                                         || hibernate5
        ['--data', 'neo4j']                               || false
        ['--features', 'gorm-neo4j']                      || true
        ['--data', 'hibernate5', '--features', 'gorm-neo4j'] || true
    }

    @Unroll
    void "features that no Grails BOM covers together are refused, and nothing is created: #arguments"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, (['com.example.shop'] + arguments) as String[])

        then:
        exit == 2
        err.toString().contains(other == 'gorm-hibernate7' ? NEO4J_WITH_HIBERNATE7 : NEO4J_WITH_MICRONAUT)
        !new File(dir, 'shop').exists()

        where:
        arguments                                                            || other
        ['--data', 'hibernate7', '--features', 'gorm-neo4j']                 || 'gorm-hibernate7'
        ['--data', 'neo4j', '--features', 'gorm-hibernate7']                 || 'gorm-hibernate7'
        ['--features', 'gorm-hibernate7,gorm-neo4j']                         || 'gorm-hibernate7'
        ['--jdk', '25', '--data', 'neo4j', '--features', 'grails-micronaut'] || 'grails-micronaut'
        ['--jdk', '25', '--features', 'grails-micronaut,gorm-neo4j']         || 'grails-micronaut'
    }

    @Unroll
    void "the list of features says which feature no Grails BOM covers together with a default: #arguments"() {
        when:
        int exit = ForgeCli.execute(beanContext, (['create-app', '--list-features'] + arguments) as String[])
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.find { it.startsWith("  ${marked} ") }.endsWith(" (cannot be combined with ${with}: no Grails BOM covers both)")
        !lines.find { it.startsWith("  ${unmarked} ") }.contains('no Grails BOM')

        where:
        arguments                || marked             | with              | unmarked
        ['--data', 'hibernate7'] || 'gorm-neo4j'       | 'gorm-hibernate7' | 'gorm-mongodb'
        ['--data', 'neo4j']      || 'gorm-hibernate7'  | 'gorm-neo4j'      | 'gorm-hibernate5'
        ['--data', 'neo4j']      || 'grails-micronaut' | 'gorm-neo4j'      | 'gorm-mongodb'
    }

    void "with the default features the list marks no feature for its BOM"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'create-app', '--list-features')

        then:
        exit == 0
        out.toString().contains('  gorm-neo4j ')
        !out.toString().contains('no Grails BOM')
    }

    void "a feature that is not for the type of application is refused"() {
        when:
        int exit = CliRunner.run(CreatePluginCommand, beanContext, 'com.example.shop', '--features', 'asset-pipeline-grails')

        then:
        exit == 2
        err.toString().contains('The requested feature does not exist: asset-pipeline-grails')
        !new File(dir, 'shop').exists()
    }

    void "the excluded features are in the state of the project, sorted, and are read from there"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--exclude-features', 'mockito,scaffolding')

        then:
        exit == 0
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())
        state.manifest.excludedFeatures*.name == ['mockito', 'scaffolding']
        state.manifest.requestedFeatures.empty
        !state.lock.featureNames.contains('mockito')

        when: 'a record as earlier versions wrote it says something else: the state is what is read'
        File record = new File(dir, 'shop/grails-forge-cli.yml')
        String manifest = new File(dir, 'shop/.grails/project.yml').text
        Files.writeString(record.toPath(), 'applicationType: web\ndefaultPackage: com.example\nfeatures: [h2]\nexcludedFeatures: [spock]\n')

        then:
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).excludedFeatures == ['mockito', 'scaffolding']

        when: 'a project without the state is read from the older record'
        new File(dir, 'shop/.grails').deleteDir()

        then:
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).excludedFeatures == ['spock']

        when: 'the names in another order give the same manifest'
        int again = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shopb', '--exclude-features', 'scaffolding', '--exclude-features', 'mockito')

        then:
        again == 0
        new File(dir, 'shopb/.grails/project.yml').text.replace('shopb', 'shop') == manifest
    }

    void "an application without exclusions has an empty list of them in its state"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop')

        then:
        exit == 0
        ProjectState.read(new File(dir, 'shop').toPath()).manifest.excludedFeatures.empty
        new File(dir, 'shop/.grails/project.yml').text.contains('excludedFeatures: []')
    }

    @Unroll
    void "the coordinates of a plugin are not the name of a feature to exclude: #excluded"() {
        when: 'no repository is given, and none is asked'
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', 'postgres', '--exclude-features', excluded)

        then:
        exit == 2
        err.toString().contains("--exclude-features takes the names of features, and ${named} is not one")
        !new File(dir, 'shop').exists()

        where:
        excluded                                   || named
        'com.acme:acme-audit-grails:2.3.0'         || 'com.acme:acme-audit-grails:2.3.0'
        'mockito,com.acme:acme-audit-grails'       || 'com.acme:acme-audit-grails'
        'com.acme:acme-audit-grails#acme-audit'    || 'com.acme:acme-audit-grails#acme-audit'
    }

    void "the feature of a plugin given by its coordinates is selected, excluded and checked by its name"() {
        given:
        File repository = Files.createTempDirectory('grailsforgerepository').toFile()
        String address = repository.toPath().toAbsolutePath().normalize().toUri().toString()
        File directory = new File(repository, 'com/acme/acme-audit-grails/2.3.0')
        directory.mkdirs()
        new ZipOutputStream(new FileOutputStream(new File(directory, 'acme-audit-grails-2.3.0.jar'))).withCloseable { ZipOutputStream zip ->
            ['META-INF/grails-forge/feature.yml': CreateAppFeaturePackSpec.DESCRIPTOR + 'requires: [database-migration]\nconflicts: [gorm-mongodb]\n',
             'META-INF/grails-forge/templates/audit.txt': 'kept as it is'].each { String path, String text ->
                zip.putNextEntry(new ZipEntry(path))
                zip.write(text.getBytes('UTF-8'))
                zip.closeEntry()
            }
        }
        String coordinates = 'com.acme:acme-audit-grails:2.3.0'

        when: 'with exclusions and an option that is given'
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', "${coordinates},postgres", '--data', 'hibernate5',
                '--exclude-features', 'mockito', '--feature-pack-repository', address)

        then:
        exit == 0
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())
        state.manifest.requestedFeatures*.name == ['acme-audit', 'postgres']
        state.manifest.excludedFeatures*.name == ['mockito']
        state.lock.featureNames.containsAll(['acme-audit', 'database-migration', 'postgres'])
        !state.lock.featureNames.contains('mockito')

        when: 'the feature of the plugin is excluded by its name while the plugin is selected'
        int both = CliRunner.run(CreateAppCommand, beanContext, 'com.example.two', '--features', coordinates,
                '--exclude-features', 'acme-audit', '--feature-pack-repository', address)

        then:
        both == 2
        err.toString().contains('The feature acme-audit is both selected and excluded')
        !new File(dir, 'two').exists()

        when: 'what the feature of the plugin requires is excluded'
        int required = CliRunner.run(CreateAppCommand, beanContext, 'com.example.three', '--features', coordinates,
                '--exclude-features', 'database-migration', '--feature-pack-repository', address)

        then:
        required == 2
        err.toString().contains('The feature database-migration cannot be excluded: the feature acme-audit requires it')
        !new File(dir, 'three').exists()

        when: 'an option that is given asks for what the feature of the plugin conflicts with'
        int conflict = CliRunner.run(CreateAppCommand, beanContext, 'com.example.four', '--features', coordinates,
                '--data', 'mongodb', '--feature-pack-repository', address)

        then:
        conflict == 2
        err.toString().contains('The feature acme-audit cannot be used with the feature gorm-mongodb')
        !new File(dir, 'four').exists()

        when: 'an option that is given does not agree with a feature selected next to the plugin'
        int option = CliRunner.run(CreateAppCommand, beanContext, 'com.example.five', '--features', "gorm-hibernate7,${coordinates}",
                '--data', 'hibernate5', '--feature-pack-repository', address)

        then:
        option == 2
        err.toString().contains('The option --data hibernate5 does not agree with the features that are selected or excluded')
        !new File(dir, 'five').exists()

        cleanup:
        repository?.deleteDir()
    }

    @Unroll
    void "an exclusion that would leave what a remaining feature generates without what it uses is refused: #excluded"() {
        when:
        int exit = ForgeCli.execute(beanContext, command, 'com.example.shop', '--exclude-features', excluded)

        then:
        exit == 2
        err.toString().readLines()[0] == message
        !new File(dir, 'shop').exists()

        where:
        command             | excluded                 || message
        'create-app'        | 'gsp,scaffolding'        || 'The feature gsp cannot be excluded: what the feature geb-with-testcontainers generates relies on it'
        'create-app'        | 'gsp,scaffolding,geb-with-testcontainers' || 'The feature gsp cannot be excluded: what the feature grails-url-mappings generates relies on it'
        'create-app'        | 'asset-pipeline-grails'  || 'The feature asset-pipeline-grails cannot be excluded: what the feature grails-url-mappings generates relies on it'
        'create-web-plugin' | 'asset-pipeline-grails'  || 'The feature asset-pipeline-grails cannot be excluded: what the feature gsp generates relies on it'
        'create-web-plugin' | 'gsp,scaffolding'        || 'The feature gsp cannot be excluded: what the feature grails-url-mappings generates relies on it'
    }

    @Unroll
    void "a value an option cannot have is a mistake in the command line: #arguments"() {
        when:
        int exit = ForgeCli.execute(beanContext, (['create-app', 'com.example.shop'] + arguments) as String[])

        then:
        exit == 2
        err.toString().contains(message)
        err.toString().contains('Usage: ')
        !new File(dir, 'shop').exists()

        where:
        arguments                                || message
        ['--jdk', '5']                           || 'Unsupported JDK version: 5. Supported values are'
        ['--java-version', '17']                 || 'Unsupported JDK version: 17. Supported values are'
        ['--servlet', 'websphere']               || 'websphere'
        ['--features', 'mongodb-testing-support'] || 'mongodb-testing-support requires mongo-sync or gorm-mongodb to be selected.'
    }

    void "a plugin is recorded with the options it has: no servlet container"() {
        when:
        int exit = CliRunner.run(CreatePluginCommand, beanContext, 'com.example.shop')

        then:
        exit == 0
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())
        state.manifest.options.servlet.name() == 'NONE'
        state.lock.options.servlet.name() == 'NONE'
        !state.lock.featureNames.any { it.contains('tomcat') }

        when: 'and with the one that is asked for'
        int jetty = CliRunner.run(CreatePluginCommand, beanContext, 'com.example.other', '--servlet', 'jetty')

        then:
        jetty == 0
        ProjectState.read(new File(dir, 'other').toPath()).manifest.options.servlet.name() == 'JETTY'
        ProjectState.read(new File(dir, 'other').toPath()).lock.featureNames.contains('spring-boot-starter-jetty')
    }

    void "the option is in the help of the command"() {
        when:
        CliRunner.run(CreateAppCommand, beanContext, '--help')

        then:
        out.toString().contains('--exclude-features')
    }
}
