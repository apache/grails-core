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
package org.grails.forge.features.pack

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.command.CreateAppCommand
import org.grails.forge.cli.command.FeatureAddCommand
import org.grails.forge.cli.command.FeatureListCommand
import org.grails.forge.utils.CommandSpec

/**
 * A feature pack written as the guide says and packaged by the tasks of the Grails Gradle plugin,
 * used by the command line the way a team uses it: an application is created with the feature of the
 * jar of the plugin, and with the feature of the pack's directory, each is cloned elsewhere, and a
 * feature of Grails is listed and added to the clone with the same pack given again. Nothing but what a plugin
 * author has is used to make the pack: {@code src/main/forge}, {@code checkForgePack} and {@code jar}.
 */
class ExternalPackJourneySpec extends CommandSpec {

    private static final String DESCRIPTOR = '''\
        format: 1
        kind: feature
        templateApi: 1
        name: acme-audit
        compatibility:
          grails: '[9.0.0,10.0.0)'
          forge: '[9.0.0,10.0.0)'
        title: Acme Audit Trail
        description: Adds the Acme audit plugin
        applicationTypes: [web]
        configuration:
          acme.audit.enabled: true
        files:
          - from: Audit.groovy.gsp
            to: 'src/main/groovy/{packagePath}/Audit.groovy'
        '''.stripIndent()

    private static final String TEMPLATE = 'package ${project.packageName}\n\n// ${applicationType} ${options.data}\nclass Audit {\n}\n'

    private static final String COORDINATES = 'com.acme:acme-audit-grails:1.0.0'

    File work
    String previousHome
    String previousDirectory

    void setup() {
        work = Files.createTempDirectory('externalpackjourney').toFile()
        // the journal of a change is kept in the home directory of whoever runs the command
        File home = new File(work, 'home')
        home.mkdirs()
        previousHome = System.getProperty('user.home')
        previousDirectory = System.getProperty('user.dir')
        System.setProperty('user.home', home.absolutePath)
    }

    void cleanup() {
        System.setProperty('user.home', previousHome)
        System.setProperty('user.dir', previousDirectory)
        work.deleteDir()
    }

    void 'the pack of a plugin is checked and packaged by the Gradle plugin, an application is created with it, and a clone gets a feature with it'() {
        given: 'a plugin with a pack, checked and packaged by its build, and its jar in a repository that is a directory'
        pluginWithPack()
        BuildResult built = executeGradle('checkForgePack', 'jar')
        File jar = new File(dir, 'build/libs').listFiles().find { File file -> file.name.endsWith('.jar') && !file.name.endsWith('-plain.jar') }
        File repository = new File(work, 'repository')
        File published = new File(repository, 'com/acme/acme-audit-grails/1.0.0/acme-audit-grails-1.0.0.jar')
        published.parentFile.mkdirs()
        Files.copy(jar.toPath(), published.toPath())
        String address = repository.toPath().toAbsolutePath().normalize().toUri().toString()

        expect:
        built.task(':checkForgePack').outcome == TaskOutcome.SUCCESS
        built.task(':jar').outcome == TaskOutcome.SUCCESS

        when: 'an application is created with the feature of the jar'
        File app = new File(work, 'from-jar')
        int exit = cli(app, CreateAppCommand, 'com.example.shop', '--inplace', '--features', COORDINATES, '--feature-pack-repository', address)

        then:
        exit == 0
        new File(app, 'src/main/groovy/com/example/Audit.groovy').text.startsWith('package com.example\n\n// web ')
        new File(app, '.grails/forge.lock.yml').text.contains('coordinates: com.acme:acme-audit-grails\n  version: 1.0.0\n')

        when: 'the application is cloned elsewhere, and the features that can be added to the clone are listed, without the network'
        File clone = copy(app, new File(work, 'elsewhere/from-jar'))
        String said = listed(clone, '--offline', '--feature-pack-repository', address)

        then: 'they are worked out with the jar of the record, as feature add takes it'
        said.contains('given the feature packs of the application as here')
        said.readLines().any { String line -> line.startsWith('    grails-quartz ') }

        when: 'a feature of Grails is added to the clone, without the network'
        exit = cli(clone, FeatureAddCommand, 'grails-quartz', '--offline', '--feature-pack-repository', address)

        then: 'the clone has both, and what the pack wrote is as it was'
        exit == 0
        new File(clone, 'build.gradle').text.contains('quartz')
        new File(clone, 'src/main/groovy/com/example/Audit.groovy').text == new File(app, 'src/main/groovy/com/example/Audit.groovy').text
        new File(clone, '.grails/forge.lock.yml').text.contains('coordinates: com.acme:acme-audit-grails\n  version: 1.0.0\n')

        when: 'an application is created with the feature of the directory of the pack'
        File fromDirectory = new File(work, 'from-directory')
        exit = cli(fromDirectory, CreateAppCommand, 'com.example.shop', '--inplace', '--features', 'acme-audit', '--feature-pack-dir', pack().absolutePath)

        then: 'its record has the digest of the content of the pack'
        exit == 0
        new File(fromDirectory, '.grails/forge.lock.yml').text =~ /- name: acme-audit\n  source: pack\n  contentSha256: [0-9a-f]{64}\n/

        when: 'the features that can be added to a clone of it are listed without the pack'
        File cloneOfDirectory = copy(fromDirectory, new File(work, 'elsewhere/from-directory'))
        File packElsewhere = copy(pack(), new File(work, 'elsewhere/pack'))
        said = listed(cloneOfDirectory)

        then: 'the list says which pack it needs, and how it is given'
        said.contains('a feature pack that was given as a directory or a file whose content has the SHA-256 ')
        said.contains('a pack of a directory is given with --feature-pack-dir')
        !said.contains('can be added to it with')

        when: 'they are listed with a copy of the pack, elsewhere'
        said = listed(cloneOfDirectory, '--feature-pack-dir', packElsewhere.absolutePath)

        then:
        said.readLines().any { String line -> line.startsWith('    grails-quartz ') }

        when: 'a feature is added with it'
        exit = cli(cloneOfDirectory, FeatureAddCommand, 'grails-quartz', '--feature-pack-dir', packElsewhere.absolutePath)

        then:
        exit == 0
        new File(cloneOfDirectory, 'build.gradle').text.contains('quartz')
    }

    private int cli(File directory, Class<?> command, String... arguments) {
        directory.mkdirs()
        System.setProperty('user.dir', directory.absolutePath)
        try {
            return CliRunner.run(command, applicationContext, arguments)
        } finally {
            System.setProperty('user.dir', previousDirectory)
        }
    }

    // what feature list writes, which ends with exit code 0
    private String listed(File directory, String... arguments) {
        PrintStream original = System.out
        ByteArrayOutputStream written = new ByteArrayOutputStream()
        System.setOut(new PrintStream(written, true, 'UTF-8'))
        try {
            assert cli(directory, FeatureListCommand, arguments) == 0
        } finally {
            System.setOut(original)
        }
        return written.toString('UTF-8')
    }

    /**
     * A generated plugin with a feature pack, whose build checks the pack with the Forge of this build.
     */
    private void pluginWithPack() {
        generateProjectWithDefaults(ApplicationType.PLUGIN)
        File templates = new File(pack(), 'templates')
        templates.mkdirs()
        new File(pack(), 'feature.yml').text = DESCRIPTOR
        new File(templates, 'Audit.groovy.gsp').text = TEMPLATE
        List<String> forge = new File(System.getProperty('forge.packCheck.classpathFile')).readLines('UTF-8')
        new File(dir, 'build.gradle') << """
            dependencies {
                grailsForgePackTooling files(${forge.collect { String path -> "'${path.replace('\\', '\\\\')}'" }.join(', ')})
            }
            """.stripIndent()
    }

    private File pack() {
        return new File(dir, 'src/main/forge')
    }

    private static File copy(File from, File to) {
        Path source = from.toPath()
        Files.walk(source).withCloseable { stream ->
            stream.each { Path path ->
                Path target = to.toPath().resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
        return to
    }

    @Override
    String getTempDirectoryPrefix() {
        'externalpackjourney'
    }
}
