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
import java.util.jar.JarFile

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.TaskOutcome

import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.GeneratedBuild
import org.grails.forge.cli.command.CreateRestApiCommand
import org.grails.forge.utils.CommandSpec

/**
 * A preset of a company, end to end through what its author and its users run: a project that is
 * only the preset, built with the Grails Gradle plugin, which checks the preset with the Forge of this
 * build and publishes it to a repository; an application made from it with {@code create-restapi
 * --preset} by the coordinates it was published under; and that application compiled, as
 * {@code BundledPackCompileSpec} compiles the applications of the packs of Forge.
 */
class CompanyPresetSpec extends CommandSpec {

    static final String COORDINATES = 'com.acme:acme-service-preset:1.0.0'

    private static final String DESCRIPTOR = '''\
        format: 1
        kind: preset
        templateApi: 1
        name: acme-service
        compatibility:
          grails: '[9.0.0,10.0.0)'
          forge: '[9.0.0,10.0.0)'
        title: Acme Service
        description: How Acme makes a REST service
        applicationType: rest-api
        options: { data: hibernate7, reloading: none }
        features: [grails-quartz]
        configuration:
          acme.service.team: payments
        files:
          - from: Team.groovy.gsp
            to: 'src/main/groovy/{packagePath}/Team.groovy'
          - from: TEAM.md
            to: TEAM.md
        readme: This service follows the Acme service guidelines.
        '''.stripIndent()

    private static final String TEAM = 'package ${project.packageName}\n\n' +
            '// the ${options.data} ${applicationType} of the payments team\n' +
            'class Team {\n    static final String NAME = \'payments\'\n}\n'

    void 'a company preset is checked and published with the Grails Gradle plugin, an application is made from its coordinates, and compiles'() {
        given: 'a project that is the preset and nothing else, built with the Grails Gradle plugin and checked with the Forge of this build'
        presetProject()
        File repository = new File(dir, 'build/presets')

        when:
        BuildResult published = executeGradle('publishMavenPublicationToPresetsRepository')

        then: 'publishing checks the preset first'
        published.task(':checkForgePack').outcome == TaskOutcome.SUCCESS
        published.task(':publishMavenPublicationToPresetsRepository').outcome == TaskOutcome.SUCCESS

        and: 'the jar is the pack, under META-INF/grails-forge, with its name in the manifest'
        File jar = new File(repository, 'com/acme/acme-service-preset/1.0.0/acme-service-preset-1.0.0.jar')
        jar.isFile()
        new JarFile(jar).withCloseable { JarFile file ->
            assert file.manifest.mainAttributes.getValue('Grails-Forge-Pack') == 'acme-service'
            assert file.getEntry('META-INF/grails-forge/feature.yml') != null
            assert file.getEntry('META-INF/grails-forge/templates/Team.groovy.gsp') != null
            true
        }

        when: 'an application is made from the preset by the coordinates it was published under'
        File parent = Files.createTempDirectory('companypreset').toFile()
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', parent.absolutePath)
        int exit
        try {
            exit = CliRunner.run(CreateRestApiCommand, applicationContext, 'com.acme.payments', '--preset', COORDINATES,
                    '--feature-pack-repository', repository.toURI().toString())
        } finally {
            System.setProperty('user.dir', current)
        }
        File application = new File(parent, 'payments')

        then: 'the application has the type, the options, the features and the files of the preset'
        exit == 0
        new File(application, 'src/main/groovy/com/acme/Team.groovy').text.contains('// the hibernate7 rest-api of the payments team')
        new File(application, 'TEAM.md').isFile()
        new File(application, 'build.gradle').text.contains('org.apache.grails:grails-quartz')
        new File(application, 'build.gradle').text.contains('grails-data-hibernate7')
        new File(application, 'grails-app/conf/application.yml').text.contains('team: payments')
        new File(application, '.grails/project.yml').text.contains('preset: acme-service\n')
        new File(application, '.grails/forge.lock.yml').text.contains('  coordinates: com.acme:acme-service-preset\n  version: 1.0.0\n')

        when: 'the application is compiled'
        BuildResult compiled = gradleRunner.withProjectDir(application)
                .withArguments(GeneratedBuild.arguments() + ['compileGroovy', 'compileTestGroovy', '--no-build-cache'])
                .build()

        then: 'the sources were compiled in this build, not taken from a cache'
        compiled.task(':compileGroovy').outcome == TaskOutcome.SUCCESS
        compiled.tasks.every { BuildTask task -> task.outcome != TaskOutcome.FROM_CACHE }

        cleanup:
        parent?.deleteDir()
    }

    /**
     * A project that is only a preset: a plugin generated by Forge, whose classes, views and messages are
     * taken away, with the preset in src/main/forge, a publication to a repository in its build
     * directory, and the Forge of this build to check the preset.
     */
    private void presetProject() {
        generateProjectWithDefaults(ApplicationType.PLUGIN)
        ['src/main/groovy', 'src/test', 'src/integration-test', 'grails-app'].each { String path -> new File(dir, path).deleteDir() }
        File templates = new File(dir, 'src/main/forge/templates')
        templates.mkdirs()
        new File(dir, 'src/main/forge/feature.yml').text = DESCRIPTOR
        new File(templates, 'Team.groovy.gsp').text = TEAM
        new File(templates, 'TEAM.md').text = '# The payments team\n'
        List<String> forge = new File(System.getProperty('forge.packCheck.classpathFile')).readLines('UTF-8')
        new File(dir, 'build.gradle') << """
            apply plugin: 'maven-publish'

            group = 'com.acme'
            version = '1.0.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        artifactId = 'acme-service-preset'
                        from components.java
                    }
                }
                repositories {
                    maven {
                        name = 'presets'
                        url = layout.buildDirectory.dir('presets')
                    }
                }
            }

            dependencies {
                grailsForgePackTooling files(${forge.collect { String path -> "'${path.replace('\\', '\\\\')}'" }.join(', ')})
            }
            """.stripIndent()
    }

    @Override
    String getTempDirectoryPrefix() {
        'companypreset'
    }
}
