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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import groovy.json.JsonSlurper
import org.grails.forge.cli.ForgeCli
import org.grails.forge.util.VersionInfo
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

/**
 * {@code update-wrapper}: the Grails wrapper that is committed in a project is replaced by the one
 * this command line has with it, which is the one an application is generated with.
 */
class UpdateWrapperCommandSpec extends Specification {

    static final List<String> WRAPPER = ['grailsw', 'grailsw.bat', 'grails-wrapper.jar']

    // what the command says after it replaced a wrapper of an earlier major version, or one that names no version, as a step of an upgrade
    static final String HEALTH = 'grails project health says what else the upgrade notes ask of the project, as far as its files show it.'

    @TempDir
    File dir

    File project
    String previousUserDir
    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        project = new File(dir, 'shop').canonicalFile
        new File(project, 'grails-app').mkdirs()
        new File(project, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        new File(project, 'gradle.properties').text = 'grailsVersion=9.0.0\n'
        previousUserDir = System.getProperty('user.dir')
        System.setProperty('user.dir', project.path)
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty('user.dir', previousUserDir)
        ConsoleProject.setOwnedByUser(null)
    }

    @IgnoreIf({ os.windows })
    void "the three files are replaced by those an application is generated with, and the script keeps its mode"() {
        given:
        earlierWrapper(project, '8.1.0')
        Files.setPosixFilePermissions(new File(project, 'grailsw').toPath(), PosixFilePermissions.fromString('rwxr-x---'))

        when:
        int exit = grails('update-wrapper')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ["The Grails wrapper of the project in ${project.path} was that of Grails 8.1.0 and is now that of Grails ${VersionInfo.grailsVersion}: " +
                                               'grailsw, grailsw.bat, grails-wrapper.jar were replaced. Commit them.', HEALTH]
        WRAPPER.every { String name -> new File(project, name).bytes == generated(name) }
        PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(project, 'grailsw').toPath())) == 'rwxr-x---'
        PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(project, 'grailsw.bat').toPath())) == 'rw-r--r--'

        and: 'nothing is left of how they were written'
        project.list().sort() == ['gradle.properties', 'grails-app', 'grails-wrapper.jar', 'grailsw', 'grailsw.bat', 'settings.gradle']

        when: 'once more: there is nothing to replace, and no file is written'
        out.reset()
        Map<String, Long> written = WRAPPER.collectEntries { String name -> [(name): new File(project, name).lastModified()] }
        exit = grails('update-wrapper')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines() == ["The Grails wrapper of the project in ${project.path} is that of Grails ${VersionInfo.grailsVersion}: nothing was changed".toString()]
        WRAPPER.every { String name -> new File(project, name).lastModified() == written[name] }
    }

    void "only what differs is replaced, a file of the wrapper that is not there is written, and a jar that names no version is said to be replaced"() {
        given: 'the script of this version, no batch file, and something that is no jar'
        new File(project, 'grailsw').bytes = generated('grailsw')
        new File(project, 'grails-wrapper.jar').text = 'not a jar'

        when:
        int exit = grails('update-wrapper')

        then:
        exit == 0
        out.toString().readLines() == ["The Grails wrapper of the project in ${project.path} is now that of Grails ${VersionInfo.grailsVersion}: " +
                                               'grailsw.bat, grails-wrapper.jar were replaced. Commit them.', HEALTH]
        WRAPPER.every { String name -> new File(project, name).bytes == generated(name) }
    }

    void "it is run in a directory of the project as it is at its root, and for a project of a larger build the wrapper of the root is replaced"() {
        given: 'a build with the wrapper at its root and the application below it'
        new File(project, 'grails-app').deleteDir()
        new File(project, '.git').mkdirs()
        File application = new File(project, 'apps/web')
        new File(application, 'grails-app/controllers').mkdirs()
        earlierWrapper(project, '7.0.14')
        System.setProperty('user.dir', new File(application, 'grails-app/controllers').path)
        ConsoleProject.setOwnedByUser { Path path -> true }

        when:
        int exit = grails('update-wrapper')

        then:
        exit == 0
        out.toString().startsWith("The Grails wrapper of the project in ${project.path} was that of Grails 7.0.14 and is now that of Grails ")
        WRAPPER.every { String name -> new File(project, name).bytes == generated(name) }
        !new File(application, 'grailsw').exists()
    }

    void "outside a project it replaces nothing and says that it is a command of a project"() {
        given: 'a directory that has the files of a wrapper and is no Grails project'
        File tools = new File(dir, 'tools').canonicalFile
        tools.mkdirs()
        earlierWrapper(tools, '8.1.0')
        Map<String, byte[]> before = WRAPPER.collectEntries { String name -> [(name): new File(tools, name).bytes] }
        System.setProperty('user.dir', tools.path)

        when:
        int exit = grails('update-wrapper')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().contains('update-wrapper')
        err.toString().contains('Grails project')
        WRAPPER.every { String name -> new File(tools, name).bytes == before[name] }
    }

    void "a project without a wrapper is told what a wrapper is, and none is written"() {
        when:
        int exit = grails('update-wrapper')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().contains("The project in ${project.path} has no Grails wrapper to replace: there is no grails-wrapper.jar in it. " +
                'A wrapper is the files grailsw, grailsw.bat, grails-wrapper.jar, which an application that create-app creates has at its root')
        WRAPPER.every { String name -> !new File(project, name).exists() }
    }

    void "the wrapper of a project of an earlier version of Grails is not replaced: the project is upgraded first"() {
        given:
        new File(project, 'gradle.properties').text = 'grailsVersion=8.1.0\n'
        earlierWrapper(project, '8.1.0')
        Map<String, byte[]> before = WRAPPER.collectEntries { String name -> [(name): new File(project, name).bytes] }

        when:
        int exit = grails('update-wrapper')

        then:
        exit == 1
        err.toString().contains('This project is on Grails 8.1.0 (gradle.properties), and the command line of Grails 9 works with projects of Grails 9 and later.')
        WRAPPER.every { String name -> new File(project, name).bytes == before[name] }
        !WrapperFiles.applies(project.toPath())

        when: 'the version is changed, which is the first step of the upgrade'
        new File(project, 'gradle.properties').text = 'grailsVersion=9.0.0\n'

        then:
        WrapperFiles.applies(project.toPath())
    }

    @IgnoreIf({ os.windows })
    void "a link is not followed and not replaced, and what it names outside the project is not written to"() {
        given:
        earlierWrapper(project, '8.1.0')
        File elsewhere = new File(dir, 'elsewhere/grailsw')
        elsewhere.parentFile.mkdirs()
        elsewhere.text = '#!/bin/sh\n# the script of another directory\n'
        new File(project, 'grailsw').delete()
        Files.createSymbolicLink(new File(project, 'grailsw').toPath(), elsewhere.toPath())

        when:
        int exit = grails('update-wrapper')

        then: 'the others are replaced, and the exit code says that the wrapper is not complete'
        exit == 1
        out.toString().contains('grailsw.bat, grails-wrapper.jar were replaced. Commit them.')
        new File(project, 'grailsw.bat').bytes == generated('grailsw.bat')
        new File(project, 'grails-wrapper.jar').bytes == generated('grails-wrapper.jar')

        and:
        Files.isSymbolicLink(new File(project, 'grailsw').toPath())
        elsewhere.text == '#!/bin/sh\n# the script of another directory\n'
        err.toString().contains("grailsw was not replaced: it is a symbolic link, and a link is not followed. Replace it by hand in ${project.path} with the grailsw of Grails ${VersionInfo.grailsVersion}, " +
                "which is at the root of an application that create-app creates and in apache-grails-wrapper-${VersionInfo.grailsVersion}-bin.zip of the release")
    }

    @IgnoreIf({ os.windows || System.getProperty('user.name') == 'root' })
    void "a file that is read-only is left as it is and named, and so is every file of a directory that cannot be written to"() {
        given:
        earlierWrapper(project, '8.1.0')
        File jar = new File(project, 'grails-wrapper.jar')
        byte[] jarBefore = jar.bytes
        jar.setWritable(false)

        when:
        int exit = grails('update-wrapper')

        then:
        exit == 1
        jar.bytes == jarBefore
        out.toString().contains('grailsw, grailsw.bat were replaced')
        err.toString().contains('grails-wrapper.jar was not replaced: it is read-only. Replace it by hand in ')

        when: 'the directory is read-only, as a checkout that is mounted read-only is'
        jar.setWritable(true)
        new File(project, 'grailsw').text = 'another script\n'
        project.setWritable(false)
        out.reset()
        err.reset()
        exit = grails('update-wrapper')

        then:
        exit == 1
        out.toString().isEmpty()
        jar.bytes == jarBefore
        new File(project, 'grailsw').text == 'another script\n'
        err.toString().readLines().size() == 2
        err.toString().contains('grailsw was not replaced: AccessDeniedException: ')
        err.toString().contains('grails-wrapper.jar was not replaced: AccessDeniedException: ')

        cleanup:
        project.setWritable(true)
    }

    void "a batch file the command interpreter is reading is not replaced under it: the new one is written beside it"() {
        given:
        earlierWrapper(project, '8.1.0')
        WrapperFiles files = new WrapperFiles(project.toPath())

        expect: 'this JVM was not started by the wrapper of the project'
        !files.batchFileRuns
        !WrapperFiles.startedBy(project.toPath())

        when:
        files.batchFileRuns = true
        WrapperFiles.Result result = files.update()

        then:
        !result.complete
        result.replaced == ['grailsw', 'grails-wrapper.jar']
        new File(project, 'grailsw.bat').text == '@rem the script of an earlier version\r\n'
        new File(project, 'grailsw.bat.new').bytes == generated('grailsw.bat')
        result.left().size() == 1
        result.left()[0].startsWith('grailsw.bat was not replaced: the command interpreter that started this command is reading it. ')
    }

    void "it is one command and takes no other: #args"() {
        given:
        earlierWrapper(project, '8.1.0')
        Map<String, byte[]> before = WRAPPER.collectEntries { String name -> [(name): new File(project, name).bytes] }

        when:
        int exit = grails(args as String[])

        then:
        exit == 2
        err.toString().contains(said)
        WRAPPER.every { String name -> new File(project, name).bytes == before[name] }

        where:
        args                             | said
        ['update-wrapper', 'run-app']    | 'update-wrapper takes no argument run-app'
        ['update-wrapper', '--force']    | '--force'
    }

    void "the help lists it among the commands of a project, and a tool is told that it is one"() {
        when:
        int exit = grails('help')

        then:
        exit == 0
        out.toString().readLines()*.replaceAll(/\s+/, ' ').any { String line -> line.startsWith(' update-wrapper Replaces the Grails wrapper of the project by that of this version') }

        when:
        out.reset()
        exit = grails('--plain-output', 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())
        Map command = ((List<Map>) described.commands).find { it.name == 'update-wrapper' }

        then:
        exit == 0
        command.project == true
        command.origin == 'built-in'
    }

    // as the grails command starts the command line
    private static int grails(String... args) {
        return ForgeCli.launch('grails', true, args)
    }

    private static byte[] generated(String name) {
        return UpdateWrapperCommandSpec.classLoader.getResourceAsStream("grails-wrapper/${name}").bytes
    }

    /** The files of a wrapper as an application of an earlier version has them: other scripts, and a jar that says which version it is of. */
    static void earlierWrapper(File directory, String version) {
        new File(directory, 'grailsw').text = '#!/bin/sh\n# the script of an earlier version\n'
        new File(directory, 'grailsw').setExecutable(true)
        new File(directory, 'grailsw.bat').text = '@rem the script of an earlier version\r\n'
        Manifest manifest = new Manifest()
        manifest.mainAttributes.put(Attributes.Name.MANIFEST_VERSION, '1.0')
        manifest.mainAttributes.put(Attributes.Name.IMPLEMENTATION_VERSION, version)
        new File(directory, 'grails-wrapper.jar').withOutputStream { OutputStream stream ->
            new JarOutputStream(stream, manifest).close()
        }
    }
}
