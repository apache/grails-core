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
import org.grails.forge.io.ConsoleOutput
import spock.lang.Unroll

class CreateAppSelectionSpec extends CommandSpec implements CommandFixture {

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
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--exclude-features', 'mockito,asset-pipeline-grails')

        then:
        exit == 0
        String buildGradle = new File(dir, 'shop/build.gradle').text
        !buildGradle.contains('mockito')
        !buildGradle.contains('asset-pipeline')
        buildGradle.contains('spock')

        and:
        List<String> record = new File(dir, 'shop/grails-forge-cli.yml').readLines()
        record.last() == 'excludedFeatures: [asset-pipeline-grails, mockito]'
        !record.find { it.startsWith('features:') }.contains('mockito')

        and: 'which the commands of the application read back'
        CodeGenConfig config = CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP)
        config.excludedFeatures == ['asset-pipeline-grails', 'mockito']
        !config.features.contains('mockito')
        config.features.contains('spock')
    }

    void "an application created without the option has the defaults and no record of exclusions"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop')

        then:
        exit == 0
        new File(dir, 'shop/build.gradle').text.contains('mockito')
        !new File(dir, 'shop/grails-forge-cli.yml').text.contains('excludedFeatures')
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).excludedFeatures == []
    }

    void "the option can be given more than once, and with the other create commands"() {
        when:
        int exit = CliRunner.run(CreateRestApiCommand, beanContext, 'com.example.api', '--exclude-features', 'mockito', '--exclude-features', 'views-json')

        then:
        exit == 0
        new File(dir, 'api/grails-forge-cli.yml').readLines().last() == 'excludedFeatures: [mockito, views-json]'
    }

    @Unroll
    void "excluding #excluded with #features selected is refused, and nothing is created: #message"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--features', features, '--exclude-features', excluded)

        then:
        exit == 1
        err.toString().contains(message)
        !new File(dir, 'shop').exists()

        where:
        features   | excluded             || message
        'postgres' | 'postgres'           || 'The feature postgres is both selected and excluded'
        'postgres' | 'no-such-feature'    || 'The excluded feature does not exist: no-such-feature'
        'postgres' | 'logback'            || 'The feature logback cannot be excluded: it is not a feature that is selected by name'
        'postgres' | 'gorm-hibernate5'    || 'The feature gorm-hibernate5 cannot be excluded: the feature postgres requires it'
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
        ['--gorm', 'hibernate5', '--features', 'gorm-hibernate7']         || 2    | 'The option --data hibernate5 does not agree with the features that are selected or excluded'
        ['--data', 'hibernate5', '--exclude-features', 'gorm-hibernate5,h2'] || 2 | 'The option --data hibernate5 does not agree with the features that are selected or excluded'
        ['--jdk', '25', '--reloading', 'devtools', '--features', 'grails-micronaut'] || 2    | 'The option --reloading devtools does not agree with the features that are selected or excluded: the application would be made without Spring Boot DevTools'
        ['--data', 'hibernate7', '--features', 'gorm-hibernate5']         || 1    | 'Only one Grails Data for Hibernate implementation can be selected: gorm-hibernate5 or gorm-hibernate7'
        ['--data', 'neo4j', '--features', 'gorm-mongodb']                 || 1    | 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'
        ['--data', 'mongodb', '--features', 'gorm-neo4j']                 || 1    | 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'
        ['--servlet', 'jetty', '--features', 'spring-boot-starter-tomcat'] || 1   | 'The requested feature does not exist: spring-boot-starter-tomcat'
        ['--features', 'postgres,mysql']                                  || 1    | 'There can only be one of the following features selected: [mysql, postgres]'
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
    }

    void "a feature that is not for the type of application is refused"() {
        when:
        int exit = CliRunner.run(CreatePluginCommand, beanContext, 'com.example.shop', '--features', 'asset-pipeline-grails')

        then:
        exit == 1
        err.toString().contains('The requested feature does not exist: asset-pipeline-grails')
        !new File(dir, 'shop').exists()
    }

    void "the excluded features are in the state of the project, sorted, and are read from there"() {
        when:
        int exit = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shop', '--exclude-features', 'mockito,asset-pipeline-grails')

        then:
        exit == 0
        ProjectState state = ProjectState.read(new File(dir, 'shop').toPath())
        state.manifest.excludedFeatures*.name == ['asset-pipeline-grails', 'mockito']
        state.manifest.requestedFeatures.empty
        !state.lock.featureNames.contains('mockito')

        when: 'the older record says something else: the state is what is read'
        File record = new File(dir, 'shop/grails-forge-cli.yml')
        String manifest = new File(dir, 'shop/.grails/project.yml').text
        Files.writeString(record.toPath(), record.text.replace('excludedFeatures: [asset-pipeline-grails, mockito]', 'excludedFeatures: [scaffolding]'))

        then:
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).excludedFeatures == ['asset-pipeline-grails', 'mockito']

        when: 'a project without the state is read from the older record'
        new File(dir, 'shop/.grails').deleteDir()

        then:
        CodeGenConfig.load(beanContext, new File(dir, 'shop'), ConsoleOutput.NOOP).excludedFeatures == ['scaffolding']

        when: 'the names in another order give the same manifest'
        int again = CliRunner.run(CreateAppCommand, beanContext, 'com.example.shopb', '--exclude-features', 'asset-pipeline-grails', '--exclude-features', 'mockito')

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
        both == 1
        err.toString().contains('The feature acme-audit is both selected and excluded')
        !new File(dir, 'two').exists()

        when: 'what the feature of the plugin requires is excluded'
        int required = CliRunner.run(CreateAppCommand, beanContext, 'com.example.three', '--features', coordinates,
                '--exclude-features', 'database-migration', '--feature-pack-repository', address)

        then:
        required == 1
        err.toString().contains('The feature database-migration cannot be excluded: the feature acme-audit requires it')
        !new File(dir, 'three').exists()

        when: 'an option that is given asks for what the feature of the plugin conflicts with'
        int conflict = CliRunner.run(CreateAppCommand, beanContext, 'com.example.four', '--features', coordinates,
                '--data', 'mongodb', '--feature-pack-repository', address)

        then:
        conflict == 1
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

    void "the option is in the help of the command"() {
        when:
        CliRunner.run(CreateAppCommand, beanContext, '--help')

        then:
        out.toString().contains('--exclude-features')
    }
}
