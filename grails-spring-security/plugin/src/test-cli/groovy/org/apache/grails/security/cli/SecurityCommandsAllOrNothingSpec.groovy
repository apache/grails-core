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

package org.apache.grails.security.cli

import groovy.io.FileType

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.ToolSupport
import org.apache.grails.core.cli.io.FileSystemInteractionImpl
import org.apache.grails.core.cli.script.ScriptConsole
import org.apache.grails.core.cli.template.TemplateRendererImpl
import org.grails.build.parsing.CommandLineParser

/**
 * The commands of the Spring Security Core plugin write all of their files or none: what a command cannot write
 * is found before it writes anything, so a run that failed leaves nothing in the way of the next one.
 */
@Issue('https://github.com/apache/grails-core/issues/16348')
class SecurityCommandsAllOrNothingSpec extends Specification {

    private static final String YAML = 'grails-app/conf/application.yml'
    private static final String PRESENT_YAML = 'grails:\n    profile: web\n'
    private static final String RESOURCES = 'grails-app/conf/spring/resources.groovy'
    private static final String USER = 'grails-app/domain/com/example/User.groovy'
    private static final String LISTENER = 'src/main/groovy/com/example/UserPasswordEncoderListener.groovy'
    private static final String QUICKSTART = 's2-quickstart com.example User Role'
    private static final List<String> QUICKSTART_FILES = [
            YAML,
            RESOURCES,
            'grails-app/domain/com/example/Role.groovy',
            USER,
            'grails-app/domain/com/example/UserRole.groovy',
            LISTENER
    ]

    @TempDir
    File project

    ByteArrayOutputStream errors = new ByteArrayOutputStream()
    GrailsConsole console

    def setup() {
        console = GrailsConsole.getInstance()
        GrailsConsole.setInstance(new ScriptConsole(new PrintStream(new ByteArrayOutputStream(), true), new PrintStream(errors, true)))
        write(YAML, PRESENT_YAML)
    }

    def cleanup() {
        GrailsConsole.setInstance(console)
        new File(project, YAML).setWritable(true)
        new File(project, RESOURCES).setWritable(true)
    }

    @Unroll
    def "s2-quickstart writes nothing when #path is there already, and with --force keeps it and writes the rest"() {
        given:
        write(path, 'edited')
        Map<String, String> before = snapshot()

        when:
        boolean result = run(new S2QuickstartCommand(), QUICKSTART)

        then:
        !result
        snapshot() == before
        errors.toString().contains('These files are there already, so the command wrote nothing:')
        errors.toString().contains("    ${path}")
        errors.toString().contains('run the command again with --force')

        when:
        errors.reset()
        result = run(new S2QuickstartCommand(), QUICKSTART + ' --force')

        then:
        result
        errors.toString() == ''
        written(path) == 'edited'
        snapshot().keySet() == QUICKSTART_FILES as Set

        where:
        path << [USER, LISTENER, 'grails-app/domain/com/example/UserRole.groovy']
    }

    @Unroll
    def "s2-quickstart writes nothing when it cannot create #blocked, and runs once it can"() {
        given: 'a file where a folder of the classes is to be'
        write(inTheWay, 'not a folder')
        Map<String, String> before = snapshot()

        when:
        boolean result = run(new S2QuickstartCommand(), QUICKSTART)

        then:
        !result
        snapshot() == before
        errors.toString().contains("${blocked} cannot be created, so the command wrote nothing.")

        when:
        errors.reset()
        assert new File(project, inTheWay).delete()
        result = run(new S2QuickstartCommand(), QUICKSTART)

        then:
        result
        errors.toString() == ''
        snapshot().keySet() == QUICKSTART_FILES as Set

        where:
        inTheWay                 || blocked
        'grails-app/domain/com'  || USER
        'src/main/groovy/com'    || LISTENER
    }

    def "s2-quickstart writes nothing when resources.groovy cannot be written to"() {
        given:
        write(RESOURCES, 'beans = {\n}\n')
        assert new File(project, RESOURCES).setWritable(false)
        Map<String, String> before = snapshot()

        when:
        boolean result = run(new S2QuickstartCommand(), QUICKSTART)

        then:
        !result
        snapshot() == before
        errors.toString().contains("The password encoder listener is not registered: ${RESOURCES} cannot be written to.")
        errors.toString().contains('so the command wrote nothing')

        when:
        errors.reset()
        assert new File(project, RESOURCES).setWritable(true)
        result = run(new S2QuickstartCommand(), QUICKSTART)

        then:
        result
        errors.toString() == ''
        written(RESOURCES).contains('    userPasswordEncoderListener(UserPasswordEncoderListener)')
    }

    @Unroll
    def "#line writes nothing when the configuration file cannot be written to, and runs once it can"() {
        given:
        assert new File(project, YAML).setWritable(false)
        Map<String, String> before = snapshot()

        when:
        boolean result = run(command.getDeclaredConstructor().newInstance(), line)

        then:
        !result
        snapshot() == before
        errors.toString().contains("${YAML} cannot be written to, so the command wrote nothing.")

        when:
        errors.reset()
        assert new File(project, YAML).setWritable(true)
        result = run(command.getDeclaredConstructor().newInstance(), line)

        then:
        result
        errors.toString() == ''
        written(YAML).contains(setting)
        new File(project, created).isFile()

        where:
        line                                                            | command                           || setting                         | created
        QUICKSTART                                                      | S2QuickstartCommand               || 'userDomainClassName'           | USER
        's2-quickstart --uiOnly'                                        | S2QuickstartCommand               || 'staticRules'                   | YAML
        's2-create-persistent-token com.example.PersistentLogin'        | S2CreatePersistentTokenCommand    || 'persistentToken'               | 'grails-app/domain/com/example/PersistentLogin.groovy'
        's2-create-role-hierarchy-entry com.example.RoleHierarchyEntry' | S2CreateRoleHierarchyEntryCommand || 'roleHierarchyEntryClassName'   | 'grails-app/domain/com/example/RoleHierarchyEntry.groovy'
    }

    def "s2-quickstart that fails while it writes says which files it wrote"() {
        given: 'a template of the project that cannot be rendered, which is found only when it is'
        write('src/main/templates/Authority.groovy.template', 'package ${packageName}\n\nclass ${noSuchAttribute.name} {\n}\n')

        when:
        boolean result = run(new S2QuickstartCommand(), QUICKSTART)

        then: 'the files before the one that failed are named, and nothing after it was written'
        !result
        errors.toString().contains('s2-quickstart stopped before it had written everything: ')
        errors.toString().contains("It wrote ${USER}, ${LISTENER} before that, and nothing else.")
        new File(project, USER).isFile()
        new File(project, LISTENER).isFile()
        !new File(project, 'grails-app/domain/com/example/Role.groovy').exists()
        !new File(project, 'grails-app/domain/com/example/UserRole.groovy').exists()
        !new File(project, RESOURCES).exists()
        written(YAML) == PRESENT_YAML
    }

    private boolean run(ToolSupport command, String line) {
        command.executionContext = new ExecutionContext(new CommandLineParser().parseString(line))
        command.templateRenderer = new TemplateRendererImpl(project)
        command.fileSystemInteraction = new FileSystemInteractionImpl(project)
        command.handle()
    }

    /**
     * @return every file of the project, by its path in the project
     */
    private Map<String, String> snapshot() {
        Map<String, String> files = [:]
        project.eachFileRecurse(FileType.FILES) { File file ->
            files.put(project.toPath().relativize(file.toPath()).toString().replace(File.separator, '/'), file.text)
        }
        files
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    private String written(String path) {
        File file = new File(project, path)
        assert file.isFile()
        file.text
    }
}
