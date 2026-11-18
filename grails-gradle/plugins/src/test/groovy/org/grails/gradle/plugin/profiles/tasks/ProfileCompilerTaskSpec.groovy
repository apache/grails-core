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
package org.grails.gradle.plugin.profiles.tasks

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Unit-level tests for {@link ProfileCompilerTask} using {@link ProjectBuilder}.
 *
 * <p>Verifies that the generated {@code profile.yml} is reproducible - the {@code commands}
 * entries are emitted in a deterministic (sorted) order regardless of the filesystem's
 * directory iteration order.</p>
 *
 * @since 8.0
 */
class ProfileCompilerTaskSpec extends Specification {

    @TempDir
    File tmpDir

    def "commands are written to profile.yml in a deterministic sorted order"() {
        given: 'a commands directory populated in a non-alphabetical order'
        Project project = ProjectBuilder.builder().withProjectDir(tmpDir).build()
        File commandsDir = new File(tmpDir, 'commands')
        commandsDir.mkdirs()
        // Intentionally create the files out of alphabetical order. Use .yml command
        // definitions (which need no Groovy compilation) so the test exercises only the
        // ordering of the generated commands map.
        ['run-app.yml', 'assemble.yml', 'clean.yml', 'test-app.yml', 'compile.yml', 'add-property.yml'].each {
            new File(commandsDir, it) << 'description: test\n'
        }

        and: 'a ProfileCompilerTask pointed at that directory'
        ProfileCompilerTask task = project.tasks.create('compileProfile', ProfileCompilerTask)
        task.commandsDirectory.set(commandsDir)
        task.templatesDirectory.set((File) null)
        task.skeletonDirectory.set((File) null)
        task.profileExtendsDefault.set([])
        task.classpath = project.files()

        when: 'the profile is generated'
        task.execute()

        then: 'the commands map is present and sorted alphabetically by command name'
        Map profileData = new Yaml().load(task.profileFile.get().asFile.newReader())
        Map commands = (Map) profileData.commands
        new ArrayList(commands.keySet()) == ['add-property', 'assemble', 'clean', 'compile', 'run-app', 'test-app']

        and: 'each command maps to its source file name'
        commands['add-property'] == 'add-property.yml'
        commands['clean'] == 'clean.yml'
        commands['compile'] == 'compile.yml'
        commands['run-app'] == 'run-app.yml'
    }
}
