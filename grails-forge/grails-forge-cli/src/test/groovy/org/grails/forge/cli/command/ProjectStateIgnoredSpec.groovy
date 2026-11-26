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
import java.util.concurrent.TimeUnit
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Requires

/**
 * What of {@code .grails} Git commits in a project whose own {@code .gitignore} has no line for it, as that of an
 * application Grails 8 created or the Grails shell did: the record and nothing that is of one machine, which the
 * record keeps out by itself.
 */
@Requires({ ProjectStateLifecycleSpec.hasGit() })
class ProjectStateIgnoredSpec extends CommandSpec {

    // the .gitignore Grails 8 generated, which has no line for .grails
    static final String GRAILS_8_IGNORE = 'Thumbs.db\n.DS_Store\n.gradle\nbuild/\nout/\n.idea\n*.iml\n*.ipr\n*.iws\n.project\n.settings\n.classpath\n'

    // what the commands and the console keep under .grails while they run, and leave behind
    static final List<String> OF_ONE_MACHINE = ['.grails/transactions/lock', '.grails/transactions/t0123456789abcdef01234567/new/0',
                                                '.grails/history', '.grails/run/app.log', '.grails/cache/tool-catalog.json']

    static final List<String> RECORD = ['.grails/.gitattributes', '.grails/.gitignore', '.grails/baseline/index.yml', '.grails/forge.lock.yml', '.grails/project.yml']

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

    void "an application that Grails 8 created, given its record by project migrate, commits the record and not what is of one machine"() {
        given: 'an application with the .gitignore Grails 8 generated'
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = 'grails:\n    profile: web\n    codegen:\n        defaultPackage: com.example.legacy\n'
        new File(dir, 'build.gradle').text = ProjectMigrateJavaVersionSpec.GRAILS_8_BUILD
        new File(dir, 'settings.gradle').text = "rootProject.name = 'legacy'\n"
        new File(dir, '.gitignore').text = GRAILS_8_IGNORE

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')

        then:
        exit == 0
        out.toString().contains('Commit the directory .grails')

        when: 'the commands leave what they keep while they run'
        OF_ONE_MACHINE.each { String path -> write(dir, path) }
        git(dir, 'init', '-q')

        then: 'Git sees the record, with the copy of every file of its baseline, and nothing else of .grails'
        untracked(dir).findAll { String path -> path.startsWith('.grails/') }.containsAll(RECORD)
        untracked(dir).findAll { String path -> path.startsWith('.grails/') }.every { String path -> path in RECORD || path.startsWith('.grails/baseline/files/') }
        OF_ONE_MACHINE.every { String path -> ignored(dir, path) }
    }

    void "a generated application whose .gitignore was replaced keeps what is of one machine out all the same"() {
        given:
        ForgeCli.execute(beanContext, 'create-app', 'com.example.shop')
        File project = new File(dir, 'shop')
        new File(project, '.gitignore').text = GRAILS_8_IGNORE
        OF_ONE_MACHINE.each { String path -> write(project, path) }

        when:
        git(project, 'init', '-q')
        List<String> state = untracked(project).findAll { String path -> path.startsWith('.grails/') }

        then: 'the record, with the copy of every generated file, and nothing that is of one machine'
        state.containsAll(RECORD)
        state.every { String path -> path in RECORD || path.startsWith('.grails/baseline/files/') }
        OF_ONE_MACHINE.every { String path -> ignored(project, path) }
    }

    private static void write(File project, String path) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = 'x'
    }

    // the files Git would add, one by one
    private List<String> untracked(File project) {
        return git(project, 'status', '--porcelain', '--untracked-files=all').readLines().collect { String line -> line.substring(3) }.sort()
    }

    private boolean ignored(File project, String path) {
        return gitProcess(project, 'check-ignore', '-q', '--no-index', path).with { Process process ->
            process.inputStream.getText('UTF-8')
            assert process.waitFor(60, TimeUnit.SECONDS)
            process.exitValue() == 0
        }
    }

    private String git(File project, String... arguments) {
        Process process = gitProcess(project, arguments)
        String text = process.inputStream.getText('UTF-8')
        assert process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0: "git ${arguments.join(' ')}: ${text}"
        return text
    }

    // Git with none of the settings of the machine, and none of the .gitignore of the user
    private Process gitProcess(File project, String... arguments) {
        List<String> command = ['git', '-c', 'init.defaultBranch=main', '-c', 'core.excludesFile=', '-c', 'core.autocrlf=false'] + arguments.toList()
        ProcessBuilder builder = new ProcessBuilder(command).directory(project).redirectErrorStream(true)
        builder.environment().put('GIT_CONFIG_NOSYSTEM', '1')
        builder.environment().put('GIT_CONFIG_GLOBAL', new File(home, '.gitconfig').absolutePath)
        builder.environment().put('HOME', home.absolutePath)
        return builder.start()
    }
}
