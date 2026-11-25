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

import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CodeGenConfig
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.options.ServletImpl

/**
 * {@code create-rest-api-plugin}: a Grails plugin with the content of a REST API application, and what
 * the commands of a project make of its record.
 */
class CreateRestApiPluginCommandSpec extends CommandSpec {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    File home
    String previousHome

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out))
        System.setErr(new PrintStream(err))
        // where a change of a project that was interrupted is looked for
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

    void "create-rest-api-plugin creates a plugin set up for JSON views, with no mappings, controller or views of its own, and nothing for HTML"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'create-rest-api-plugin', 'com.example.shopApi')
        File project = new File(dir, 'shopApi')
        List<String> files = filesOf(project)
        String build = new File(project, 'build.gradle').getText('UTF-8')
        String mappings = new File(project, 'grails-app/controllers/com/example/UrlMappings.groovy').getText('UTF-8')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().contains('Application created')

        and: 'it is a plugin: the descriptor class, the plugin source, and the Gradle plugin of a plugin'
        new File(project, 'src/main/groovy/com/example/ShopApiGrailsPlugin.groovy').getText('UTF-8').contains('class ShopApiGrailsPlugin extends Plugin {')
        new File(project, 'grails-app/init/com/example/Application.groovy').getText('UTF-8').contains('@PluginSource')
        build.contains('apply plugin: "org.apache.grails.gradle.grails-plugin"')
        !build.contains('apply plugin: "org.apache.grails.gradle.grails-web"')
        files.contains('grails-app/i18n/shop-api.properties')
        !files.contains('grails-app/i18n/messages.properties')

        and: 'set up as a REST API application is'
        build.contains('apply plugin: "org.apache.grails.gradle.grails-gson"')
        build.contains('implementation "org.apache.grails:grails-views-gson"')
        build.contains('implementation "org.springframework.boot:spring-boot-starter-tomcat"')

        and: 'with nothing that would reach the applications that use the plugin'
        mappings.contains('// provide plugin url mappings here')
        !mappings.contains('action:')
        !mappings.contains('view:')
        !files.contains('grails-app/controllers/com/example/ApplicationController.groovy')
        !files.any { it.startsWith('grails-app/views/') }

        and: 'nothing of GSP, the asset pipeline or scaffolding'
        !files.any { it.endsWith('.gsp') || it.startsWith('grails-app/assets/') || it.startsWith('grails-app/taglib/') }
        !build.contains('grails-gsp')
        !build.contains('asset-pipeline')
        !build.contains('scaffolding')
        !mappings.contains('view:"/index"')
        !new File(project, 'src/main/groovy/com/example/ShopApiGrailsPlugin.groovy').getText('UTF-8').contains('.gsp')

        when: 'the record of the project is read'
        ProjectState state = ProjectState.read(project.toPath())
        state.verifyBaseline(project.toPath())
        CodeGenConfig config = CodeGenConfig.load(beanContext, project, ConsoleOutput.NOOP)

        then:
        new File(project, '.grails/project.yml').getText('UTF-8').contains('applicationType: rest-api-plugin\n')
        state.manifest.applicationType == ApplicationType.REST_API_PLUGIN
        state.manifest.options.servlet == ServletImpl.TOMCAT
        state.lock.featureNames.containsAll(['views-json', 'views-json-testing-support', 'grails-web', 'grails-url-mappings', 'spring-boot-starter-tomcat'])
        !state.lock.featureNames.any { it in ['gsp', 'asset-pipeline-grails', 'scaffolding', 'grails-layout', 'geb-with-testcontainers'] }
        config.applicationType == ApplicationType.REST_API_PLUGIN
        !config.legacy
    }

    void "the record of a REST API plugin is shown, the plugin is generated again from it, and a feature is added to it"() {
        given:
        assert ForgeCli.execute(beanContext, 'create-rest-api-plugin', 'com.example.shopApi', '--exclude-features', 'mockito') == 0
        File project = new File(dir, 'shopApi')
        File again = new File(dir, 'again')
        out.reset()

        when:
        int shown = inProject(project, 'project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        shown == 0
        lines.any { it.contains('rest-api-plugin') }
        lines.last() == 'grails-forge-cli create-rest-api-plugin com.example.shopApi --exclude-features mockito'

        when:
        out.reset()
        int diff = inProject(project, 'project', 'regenerate', '--diff')
        int written = inProject(project, 'project', 'regenerate', '--output', again.absolutePath)

        then: 'the plugin is as it was generated, and is written again with the same files'
        diff == 0
        written == 0
        err.toString().isEmpty()
        filesOf(again) == filesOf(project)
        new File(again, 'build.gradle').getText('UTF-8') == new File(project, 'build.gradle').getText('UTF-8')

        when:
        out.reset()
        int added = inProject(project, 'feature', 'add', 'asciidoctor')

        then:
        added == 0
        err.toString().isEmpty()
        out.toString().contains('Feature asciidoctor added')
        ProjectState.read(project.toPath()).manifest.applicationType == ApplicationType.REST_API_PLUGIN
        ProjectState.read(project.toPath()).manifest.requestedFeatures*.name == ['asciidoctor']

        when: 'a feature that is not one of a REST API plugin'
        int refused = inProject(project, 'feature', 'add', 'openapi')

        then:
        refused != 0
        !ProjectState.read(project.toPath()).lock.featureNames.contains('openapi')

        when: 'scaffolding, whose commands generate a REST controller and JSON views in a REST API plugin'
        int scaffolding = inProject(project, 'feature', 'add', 'scaffolding')
        List<String> features = ProjectState.read(project.toPath()).lock.featureNames

        then: 'it is added, without the GSP feature it brings to a web application'
        scaffolding == 0
        features.contains('scaffolding')
        !features.contains('gsp')
        new File(project, 'build.gradle').getText('UTF-8').contains('implementation "org.apache.grails:grails-scaffolding"')
        !new File(project, 'build.gradle').getText('UTF-8').contains('grails-gsp')
    }

    void "create-rest-api-plugin lists the features of a REST API plugin"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'create-rest-api-plugin', '--list-features')
        String listed = out.toString()

        then:
        exit == 0
        listed.contains('views-json')
        listed.contains('views-markup')
        listed.contains('asciidoctor')
        listed.contains('scaffolding')

        and: 'and none of the features that are for applications, or for pages'
        !listed.contains('asset-pipeline-grails')
        !listed.contains('openapi')
        !listed.contains('grails-spring-security')
    }

    private int inProject(File project, String... arguments) {
        String current = System.getProperty('user.dir')
        System.setProperty('user.dir', project.absolutePath)
        try {
            return ForgeCli.execute(beanContext, arguments)
        } finally {
            System.setProperty('user.dir', current)
        }
    }

    private static List<String> filesOf(File project) {
        List<String> files = []
        Files.walk(project.toPath()).withCloseable { stream ->
            stream.filter { Files.isRegularFile(it) }.each { files << project.toPath().relativize(it).join('/') }
        }
        return files.toSorted()
    }
}
