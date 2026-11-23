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
package org.apache.grails.gradle.model.cli

import java.nio.file.Files
import java.nio.file.attribute.FileTime

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * When a catalog of commands may be out of date: by the times the files it is made from were changed,
 * as the command line of Grails reads it and as the task that writes it holds itself to.
 */
class CommandCatalogFreshnessSpec extends Specification {

    private static final long WRITTEN = 1_700_000_000_000L

    @TempDir
    File project

    File classes

    void setup() {
        classes = new File(project, 'build/classes/groovy/main')
        file('build.gradle', WRITTEN - 1000)
        file('settings.gradle', WRITTEN - 1000)
        file('gradle.properties', WRITTEN - 1000)
        file('grails-app/commands/example/GreetCommand.groovy', WRITTEN - 1000)
        file('build/classes/groovy/main/META-INF/grails-commands/example.GreetCommand.json', WRITTEN - 1000)
        // the directories were changed when their files were written, which was before the catalog
        ['grails-app/commands/example', 'grails-app/commands', 'build/classes/groovy/main/META-INF/grails-commands'].each { String path ->
            new File(project, path).setLastModified(WRITTEN - 1000)
        }
    }

    def "a catalog that is as new as everything it is made from is not out of date"() {
        expect:
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == null

        when: 'something is exactly as old as the catalog'
        file('build.gradle', WRITTEN)

        then: 'it is not newer'
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == null
    }

    @Unroll
    def "a catalog that is older than #path is out of date, and that is named"() {
        given:
        file(path, WRITTEN + 1000)

        expect:
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == named

        where:
        path                                                                    | named
        'build.gradle'                                                          | 'build.gradle'
        'build.gradle.kts'                                                      | 'build.gradle.kts'
        'settings.gradle'                                                       | 'settings.gradle'
        'settings.gradle.kts'                                                   | 'settings.gradle.kts'
        'gradle.properties'                                                     | 'gradle.properties'
        'grails-app/commands/example/GreetCommand.groovy'                       | 'grails-app/commands'
        'grails-app/commands/example/deeper/OtherCommand.groovy'                | 'grails-app/commands'
        'build/classes/groovy/main/META-INF/grails-commands/example.Other.json' | 'build/classes/groovy/main/META-INF/grails-commands'
    }

    def "a file of another ending is not looked at, and its directory is"() {
        given: 'a file of another ending, written again in a directory that is as old as the catalog'
        file('grails-app/commands/example/notes.txt', WRITTEN + 1000)
        new File(project, 'grails-app/commands/example').setLastModified(WRITTEN - 1000)

        expect:
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == null

        when: 'a file is deleted, which changes its directory'
        new File(project, 'grails-app/commands/example/notes.txt').delete()
        new File(project, 'grails-app/commands/example').setLastModified(WRITTEN + 1000)

        then:
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == 'grails-app/commands'
    }

    def "the descriptors of every directory of classes are looked at, also outside the project, which is then named by its path"() {
        given:
        File elsewhere = File.createTempDir()
        File descriptor = new File(elsewhere, 'META-INF/grails-commands/example.Elsewhere.json')
        descriptor.parentFile.mkdirs()
        descriptor.text = '{}'
        descriptor.setLastModified(WRITTEN + 1000)
        descriptor.parentFile.setLastModified(WRITTEN - 1000)

        expect:
        CommandCatalogFreshness.changedSince(project, [classes, elsewhere], at(WRITTEN)) == new File(elsewhere, 'META-INF/grails-commands').toPath().toString()

        cleanup:
        elsewhere.deleteDir()
    }

    def "places that are not there are not changed"() {
        given:
        File empty = File.createTempDir()

        expect:
        CommandCatalogFreshness.changedSince(empty, [new File(empty, 'build/classes/groovy/main')], at(WRITTEN)) == null

        cleanup:
        empty.deleteDir()
    }

    def "a directory with more entries than are looked at is taken for changed"() {
        given:
        File many = new File(project, 'grails-app/commands/many')
        many.mkdirs()
        (0..CommandCatalogFreshness.MAX_WALKED).each { int i ->
            File each = new File(many, "Command${i}.txt")
            each.text = ''
            each.setLastModified(WRITTEN - 1000)
        }
        many.setLastModified(WRITTEN - 1000)
        new File(project, 'grails-app/commands').setLastModified(WRITTEN - 1000)

        expect:
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == 'grails-app/commands'
    }

    @IgnoreIf({ os.windows })
    def "a link below the sources is not followed"() {
        given: 'a link to a directory with a newer source'
        File target = File.createTempDir()
        File newer = new File(target, 'NewerCommand.groovy')
        newer.text = ''
        newer.setLastModified(WRITTEN + 1000)
        target.setLastModified(WRITTEN + 1000)
        Files.createSymbolicLink(new File(project, 'grails-app/commands/example/linked').toPath(), target.toPath())
        new File(project, 'grails-app/commands/example').setLastModified(WRITTEN - 1000)

        expect: 'the link is an entry of its directory and not a file of the ending, and what it points to is not looked at'
        CommandCatalogFreshness.changedSince(project, [classes], at(WRITTEN)) == null

        cleanup:
        target.deleteDir()
    }

    def "the rule names the places the command line reads the catalog by"() {
        expect:
        CommandCatalogFreshness.BUILD_FILES == ['build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts', 'gradle.properties']
        CommandCatalogFreshness.COMMAND_SOURCES == 'grails-app/commands'
        CommandCatalogFreshness.SOURCE_ENDING == '.groovy'
        CommandCatalogFreshness.DESCRIPTORS == 'META-INF/grails-commands'
        CommandCatalogFreshness.DESCRIPTOR_ENDING == '.json'
        CommandCatalogFreshness.MAX_WALKED == 4096
        CommandCatalogFreshness.MAX_WALK_DEPTH == 24
    }

    private File file(String path, long modified) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        if (!file.exists()) {
            file.text = ''
        }
        file.setLastModified(modified)
        file
    }

    private static FileTime at(long millis) {
        FileTime.fromMillis(millis)
    }
}
