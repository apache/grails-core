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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.ToolCatalogBuilder
import org.apache.grails.gradle.model.cli.ToolCatalogFreshness
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Requires

/**
 * Where the command line finds the tools of the application and its plugins: in the file the
 * Gradle task {@code grailsToolCatalog} writes into the directory of the project, whatever the build
 * of the project does with its build directory, which the command line does not run the build to
 * learn. A file an earlier build left in a build directory is not read. When the catalog may be out
 * of date is the rule the task holds itself to, {@code ToolCatalogFreshness} of grails-gradle-model.
 */
class ToolCatalogLocationSpec extends CommandSpec implements CommandFixture {

    private static final Map STALE = [name: 'stale-command', aliases: [], description: 'Listed by an earlier build', origin: 'application', target: 'application',
                                      registration: 'descriptor']

    // What makes the catalog out of date, and how the command line names it: the build files, the sources of the tools in
    // the two folders of tools and the deprecated one, Groovy and Java, and the descriptors the compiler writes for the
    // three source sets that have tools
    static final List<List<String>> RULE = [
            ['build.gradle', 'build.gradle'],
            ['build.gradle.kts', 'build.gradle.kts'],
            ['settings.gradle', 'settings.gradle'],
            ['settings.gradle.kts', 'settings.gradle.kts'],
            ['gradle.properties', 'gradle.properties'],
            ['src/cli/groovy/example/GreetTool.groovy', 'src/cli'],
            ['src/cli/java/example/GreetTool.java', 'src/cli'],
            ['src/standalone/groovy/example/ReportTool.groovy', 'src/standalone'],
            ['src/standalone/java/example/ReportTool.java', 'src/standalone'],
            ['grails-app/commands/example/GreetCommand.groovy', 'grails-app/commands'],
            ['grails-app/commands/example/GreetCommand.java', 'grails-app/commands'],
            ['build/classes/groovy/main/META-INF/grails-tools/example.GreetCommand.json', 'build/classes/groovy/main/META-INF/grails-tools'],
            ['build/classes/groovy/cli/META-INF/grails-tools/example.GreetTool.json', 'build/classes/groovy/cli/META-INF/grails-tools'],
            ['build/classes/groovy/standalone/META-INF/grails-tools/example.ReportTool.json', 'build/classes/groovy/standalone/META-INF/grails-tools'],
    ].asImmutable()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    void "the command line reads the catalog where the task of the build writes it"() {
        expect:
        ToolCatalogFile.LOCATION == ToolCatalogBuilder.LOCATION
        ToolCatalogFile.LOCATION == '.grails/cache/tool-catalog.json'
    }

    void "in a project whose #dsl build moves its build directory the tools are those of the catalog in the directory of the project"() {
        given: 'a build that moves its build directory, and catalogs that earlier builds left in build directories'
        File script = file(buildFile, moving)
        file('build/grails/tool-catalog.json', ProjectToolsSpec.doc([STALE]))
        file('out/grails/tool-catalog.json', ProjectToolsSpec.doc([STALE]))
        File catalog = file(ToolCatalogFile.LOCATION, ProjectToolsSpec.doc([ProjectToolsSpec.HELLO]))
        catalog.setLastModified(script.lastModified() + 2000)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == true
        described.diagnostics == []
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == ['hello-world']

        when: 'the tool is run by its name'
        out.reset()
        err.reset()
        ForgeCli.execute(beanContext, 'stale-command')

        then: 'what an earlier build listed is no command'
        err.toString().contains('stale-command')
        !all().contains('Running the tool stale-command')

        where:
        dsl      | buildFile          | moving
        'Groovy' | 'build.gradle'     | "layout.buildDirectory = layout.projectDirectory.dir('out')\n"
        'Kotlin' | 'build.gradle.kts' | 'layout.buildDirectory.set(layout.projectDirectory.dir("out"))\n'
    }

    void "a catalog that an earlier build left in the build directory alone is not read, and the task that lists the tools is named"() {
        given:
        file('build.gradle', "layout.buildDirectory = layout.projectDirectory.dir('out')\n")
        File stale = file('build/grails/tool-catalog.json', ProjectToolsSpec.doc([STALE]))
        stale.setLastModified(System.currentTimeMillis() + 2000)

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then:
        exit == 0
        described.complete == false
        described.diagnostics == [ProjectToolsSpec.MISSING]
        ((List<Map>) described.commands).every { it.origin == 'built-in' }
    }

    // a tool of src/cli is compiled by the Gradle plugin that has the folders of tools
    @Requires({ ToolsOfTheBuild.hasToolFolders() })
    void "the tools of a generated application whose build moves its build directory are there once the build has listed them"() {
        given: 'an application with a tool, whose build directory is moved, and a catalog an earlier build left in the build directory it had'
        generateProject(ApplicationType.WEB)
        new File(dir, 'build.gradle') << "\nlayout.buildDirectory = layout.projectDirectory.dir('out')\n"
        ForgeCli.execute(beanContext, 'create-tool', 'greet')
        file('build/grails/tool-catalog.json', ProjectToolsSpec.doc([STALE]))

        when: 'the build lists the tools, as the command line says to'
        out.reset()
        int listed = ForgeCli.execute(beanContext, 'gradle', 'grailsToolCatalog')

        then:
        listed == 0
        new File(dir, ToolCatalogFile.LOCATION).isFile()
        new File(dir, 'out').isDirectory()
        !new File(dir, 'out/grails').exists()

        when:
        out.reset()
        err.reset()
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then: 'the tool of the application is a command of the command line, and what the earlier build listed is not'
        exit == 0
        ((List<Map>) described.commands).findAll { it.origin == 'application' }*.name == ['greet']
        !((List<Map>) described.commands).any { it.name == STALE.name }
        !((List<String>) described.diagnostics).any { it.contains(ToolCatalogFile.LOCATION) }
    }

    void "the command line takes the catalog for out of date when #changed is newer"() {
        given: 'a catalog, and then a file that is newer than it'
        File catalog = file(ToolCatalogFile.LOCATION, ProjectToolsSpec.doc([ProjectToolsSpec.HELLO]))
        catalog.setLastModified(System.currentTimeMillis() - 60_000)
        file(changed, '')

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then: 'the command line says what the task of the build would write the catalog again for'
        exit == 0
        described.complete == false
        ((List<String>) described.diagnostics)[0].contains("may have changed: ${named} is newer than ${ToolCatalogFile.LOCATION}")

        when: 'the catalog is written again, as the task does'
        catalog.setLastModified(System.currentTimeMillis() + 2_000)
        out.reset()
        ForgeCli.execute(beanContext, 'help', '--json')

        then: 'it is not taken for out of date'
        document().complete == true

        where:
        [changed, named] << RULE
    }

    void "a file that is no source of a tool, #changed, does not make the catalog out of date"() {
        given: 'a catalog, and then a file that is newer than it'
        File catalog = file(ToolCatalogFile.LOCATION, ProjectToolsSpec.doc([ProjectToolsSpec.HELLO]))
        catalog.setLastModified(System.currentTimeMillis() - 60_000)
        file(changed, '')
        // the directories that were made for the file are the project's, which the catalog is newer than
        for (File above = new File(dir, changed).parentFile; above != dir; above = above.parentFile) {
            above.setLastModified(System.currentTimeMillis() - 120_000)
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')

        then:
        exit == 0
        document().complete == true

        where:
        changed << ['src/main/groovy/example/Thing.groovy', 'src/cli/resources/report.txt', 'src/standalone/groovy/example/notes.md', 'grails-app/commands/README.txt',
                    'build/classes/groovy/cli/example/ReportTool.class', 'build/classes/groovy/test/META-INF/grails-tools/example.SpecTool.json']
    }

    // The task of the build holds itself to the rule the command line reads the catalog by, ToolCatalogFreshness of
    // grails-gradle-model, which is given the directories of the classes of the three source sets that have tools.
    // That model has the folders of tools only where the Gradle plugin of the checkout compiles them.
    @Requires({ ToolsOfTheBuild.hasToolFolders() })
    void "the task of the build takes the catalog for out of date by the same rule, when #changed is newer"() {
        given: 'a catalog, and then a file that is newer than it'
        File catalog = file(ToolCatalogFile.LOCATION, ProjectToolsSpec.doc([ProjectToolsSpec.HELLO]))
        catalog.setLastModified(System.currentTimeMillis() - 60_000)
        file(changed, '')
        List<File> classes = ['main', 'cli', 'standalone'].collect { String sourceSet -> new File(dir, "build/classes/groovy/${sourceSet}") }

        expect:
        ToolCatalogFreshness.changedSince(dir, classes, Files.getLastModifiedTime(catalog.toPath())) == named

        when: 'the catalog is written again, as the task does'
        catalog.setLastModified(System.currentTimeMillis() + 2_000)

        then:
        ToolCatalogFreshness.changedSince(dir, classes, Files.getLastModifiedTime(catalog.toPath())) == null

        where:
        [changed, named] << RULE
    }

    void "the places the command line reads the catalog by are those of the rule"() {
        expect:
        ToolCatalogFile.BUILD_FILES == ToolCatalogFreshness.BUILD_FILES
        ToolCatalogFile.SOURCE_DIRECTORIES == ToolCatalogFreshness.TOOL_SOURCES
        ToolCatalogFile.SOURCE_ENDINGS == ToolCatalogFreshness.SOURCE_ENDINGS
        ToolCatalogFile.DESCRIPTOR_DIRECTORIES == ['main', 'cli', 'standalone'].collect { String sourceSet -> "build/classes/groovy/${sourceSet}/${ToolCatalogFreshness.DESCRIPTORS}".toString() }
        ToolCatalogFile.DESCRIPTOR_ENDING == ToolCatalogFreshness.DESCRIPTOR_ENDING
        ToolCatalogFile.MAX_WALKED == ToolCatalogFreshness.MAX_WALKED
        ToolCatalogFile.MAX_WALK_DEPTH == ToolCatalogFreshness.MAX_WALK_DEPTH
    }

    @IgnoreIf({ os.windows })
    void "#path as a link counts for the command line and for the task of the build with the time of the file it leads to: #scenario"() {
        given: 'a catalog, and the place as a link to a file outside the project'
        long now = System.currentTimeMillis()
        File catalog = file(ToolCatalogFile.LOCATION, ProjectToolsSpec.doc([ProjectToolsSpec.HELLO]))
        catalog.setLastModified(now + (long) catalogWritten)
        File classes = new File(dir, 'build/classes/groovy/main')
        File elsewhere = Files.createTempDirectory('linked-build-files').toFile()
        File target = new File(elsewhere, 'target')
        if (targetModified != null) {
            target.text = ''
            target.setLastModified(now + (long) targetModified)
        }
        File link = new File(dir, path)
        link.parentFile.mkdirs()
        Files.createSymbolicLink(link.toPath(), target.toPath())
        // making the link changed its directory, and making that one the directory above, which is not what is asked here
        for (File above = link.parentFile; above != dir; above = above.parentFile) {
            above.setLastModified(now - 3_600_000L)
        }

        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = document()

        then: 'the command line and the task of the build say the same'
        exit == 0
        (Files.getLastModifiedTime(link.toPath(), LinkOption.NOFOLLOW_LINKS) > Files.getLastModifiedTime(catalog.toPath())) == linkIsNewer
        ToolCatalogFreshness.changedSince(dir, [classes], Files.getLastModifiedTime(catalog.toPath())) == named
        described.complete == (named == null)
        named == null || ((List<String>) described.diagnostics)[0].contains("may have changed: ${named} is newer than ${ToolCatalogFile.LOCATION}")
        named != null || described.diagnostics == []

        cleanup:
        elsewhere?.deleteDir()

        where:
        path                                              | scenario                                                    | catalogWritten | targetModified | linkIsNewer || named
        'build.gradle'                                    | 'a newer file behind a link that is older than the catalog' | 3_600_000L     | 7_200_000L     | false       || 'build.gradle'
        'build.gradle'                                    | 'an older file behind a link that is newer than the catalog' | -60_000L      | -120_000L      | true        || null
        'build.gradle'                                    | 'no file'                                                   | -60_000L       | null           | true        || null
        'settings.gradle'                                 | 'a newer file behind a link that is older than the catalog' | 3_600_000L     | 7_200_000L     | false       || 'settings.gradle'
        'settings.gradle'                                 | 'an older file behind a link that is newer than the catalog' | -60_000L      | -120_000L      | true        || null
        'gradle.properties'                               | 'a newer file behind a link that is older than the catalog' | 3_600_000L     | 7_200_000L     | false       || 'gradle.properties'
        'gradle.properties'                               | 'no file'                                                   | -60_000L       | null           | true        || null
        'grails-app/commands/example/GreetCommand.groovy' | 'a newer file behind a link that is older than the catalog' | 3_600_000L     | 7_200_000L     | false       || 'grails-app/commands'
        'grails-app/commands/example/GreetCommand.groovy' | 'an older file behind a link that is newer than the catalog' | -60_000L      | -120_000L      | true        || null
        'grails-app/commands/example/GreetCommand.groovy' | 'no file'                                                   | -60_000L       | null           | true        || null
    }

    private File file(String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.setText(text, StandardCharsets.UTF_8.name())
        return file
    }

    private Map document() {
        return (Map) new JsonSlurper().parseText(out.toString().readLines().last())
    }

    private String all() {
        return out.toString() + err.toString()
    }
}
