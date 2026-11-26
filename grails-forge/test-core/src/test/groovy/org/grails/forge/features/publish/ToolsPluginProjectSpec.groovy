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
package org.grails.forge.features.publish

import java.nio.file.Files
import java.util.zip.ZipFile
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.gradle.testkit.runner.BuildResult
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.FileSystemOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import org.grails.forge.utils.CommandSpec

/**
 * A generated plugin with a tool in each folder of tools publishes two companions beside itself, as it is
 * generated: {@code <artifact>-cli} with the tool of {@code src/cli}, which depends on the plugin, and
 * {@code <artifact>-cli-standalone} with the tool of {@code src/standalone}, which does not. A generated
 * application that depends on the plugin has both companions without a line for either, and runs a tool of
 * each kind. The plugin is published to a directory of the test, so nothing is written to the local Maven
 * repository of whoever runs the spec.
 */
class ToolsPluginProjectSpec extends CommandSpec {

    void 'a plugin with a tool in each folder publishes two companions, and an application that depends on the plugin runs both tools'() {
        given: 'a generated plugin with grails-publish, a tool the build runs and a tool that runs as a process of its own'
        generateProject(OperatingSystem.LINUX, ['grails-publish'], ApplicationType.PLUGIN)
        File plugin = dir
        String artifact = plugin.name
        write(plugin, 'src/cli/groovy/example/grails/HelloTool.groovy', '''\
            package example.grails

            import org.apache.grails.core.cli.ApplicationTool
            import org.apache.grails.core.cli.ToolSupport

            class HelloTool implements ApplicationTool, ToolSupport {

                boolean startsApplication = false

                boolean handle() {
                    println "KIND cli args=${args}"
                    true
                }
            }
            ''')
        write(plugin, 'src/standalone/groovy/example/grails/PlainTool.groovy', '''\
            package example.grails

            import org.apache.grails.core.cli.StandaloneTool
            import org.apache.grails.core.cli.ToolSupport

            class PlainTool implements StandaloneTool, ToolSupport {

                boolean handle() {
                    println "KIND standalone args=${args}"
                    true
                }
            }
            ''')
        File repository = new File(plugin, 'build/published-by-the-spec')

        when: 'the plugin is published to the repository of the test'
        BuildResult published = executeGradle('publish', '-Pversion=0.1', "-PmavenPublishUrl=${repository.toURI()}".toString())
        File group = new File(repository, 'example/grails')

        then: 'the plugin and its two companions'
        published.output.contains('BUILD SUCCESSFUL')
        [artifact, "${artifact}-cli", "${artifact}-cli-standalone"].every { String name -> new File(group, "${name}/0.1/${name}-0.1.jar").isFile() }

        and: 'the companion of the tools the build runs depends on the plugin and on the other companion; the standalone one on neither'
        String cliPom = new File(group, "${artifact}-cli/0.1/${artifact}-cli-0.1.pom").text
        String standalonePom = new File(group, "${artifact}-cli-standalone/0.1/${artifact}-cli-standalone-0.1.pom").text
        cliPom.contains("<artifactId>${artifact}</artifactId>")
        cliPom.contains("<artifactId>${artifact}-cli-standalone</artifactId>")
        !standalonePom.contains("<artifactId>${artifact}</artifactId>")
        standalonePom.contains('<artifactId>grails-core-cli</artifactId>')

        and: 'each tool is in its companion, and none is in the plugin'
        entries(new File(group, "${artifact}-cli/0.1/${artifact}-cli-0.1.jar")).contains('example/grails/HelloTool.class')
        entries(new File(group, "${artifact}-cli-standalone/0.1/${artifact}-cli-standalone-0.1.jar")).contains('example/grails/PlainTool.class')
        !entries(new File(group, "${artifact}/0.1/${artifact}-0.1.jar")).any { it.endsWith('/HelloTool.class') || it.endsWith('/PlainTool.class') }

        when: 'a generated application depends on the plugin, from the repository of the test, and nothing else of it'
        File application = Files.createTempDirectory('testtoolsapplication').toFile()
        applicationContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('example.grails.shop'), new Options(), OperatingSystem.LINUX, [],
                new FileSystemOutputHandler(application, ConsoleOutput.NOOP), ConsoleOutput.NOOP)
        new File(application, 'build.gradle') << """
            repositories {
                maven { url = uri('${repository.toURI()}') }
            }
            dependencies {
                implementation 'example.grails:${artifact}:0.1'
            }
            """.stripIndent()
        dir = application
        String cli = executeGradle('runTool', '-Pargs=hello one').output
        String standalone = executeGradle('runTool', '-Pargs=plain two').output
        executeGradle('grailsStandaloneTools')
        ToolCatalogFile catalog = ToolCatalogFile.read(application.toPath())
        String record = new File(application, '.grails/cache/standalone-tools.json').text

        then: 'both tools run, without a line for either companion'
        cli.contains('KIND cli args=[one]')
        standalone.contains('KIND standalone args=[two]')
        !new File(application, 'build.gradle').text.contains('-cli')

        and: 'the catalog has each as its kind, from its companion'
        catalog.state == ToolCatalogFile.State.CURRENT
        ToolCatalogFile.Entry hello = catalog.tools.find { it.name == 'hello' }
        ToolCatalogFile.Entry plain = catalog.tools.find { it.name == 'plain' }
        hello.target == 'application' && !hello.startsApplication
        hello.origin.startsWith("example.grails:${artifact}-cli:")
        plain.standalone
        plain.origin.startsWith("example.grails:${artifact}-cli-standalone:")

        and: 'the standalone tool is started with its companion, and with nothing of the plugin'
        record.contains('"plain"')
        record.contains("${artifact}-cli-standalone-0.1.jar")
        !record.contains("${artifact}-0.1.jar")

        cleanup:
        plugin?.deleteDir()
    }

    private static void write(File project, String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text.stripIndent()
    }

    private static List<String> entries(File jar) {
        List<String> names = []
        new ZipFile(jar).withCloseable { ZipFile zip -> names.addAll(zip.entries().toList()*.name) }
        return names
    }

    @Override
    String getTempDirectoryPrefix() {
        'testtoolsplugin'
    }
}
