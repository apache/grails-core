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
package org.apache.grails.cli

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The Grails wrapper of this version on the jar it downloads, {@code grails-cli-VERSION-all.jar}, as this build
 * makes both: the wrapper is the {@code grails-wrapper.jar} the jar has with it, which {@code update-wrapper} and
 * {@code create-app} write, and it starts the class whose name it has compiled into it, with the property that
 * says it is no wrapper of Grails 7 or 8. That class starts the command line and nothing else: the wrapper of the
 * project is replaced only when {@code update-wrapper} is asked for.
 */
class WrapperOfThisVersionSpec extends Specification {

    private static final List<String> WRAPPER = ['grailsw', 'grailsw.bat', 'grails-wrapper.jar']
    private static final String VERSION = '9.0.0'

    @Shared
    File allJar = new File(System.getProperty('grails.cli.all.jar'))

    @Shared
    @TempDir
    File shared

    @Shared
    File repository

    @TempDir
    File dir

    File project

    void setupSpec() {
        // the repository the wrapper downloads the command line from
        repository = new File(shared, 'repository')
        File versionDirectory = new File(repository, "org/apache/grails/grails-cli/${VERSION}")
        versionDirectory.mkdirs()
        Files.copy(allJar.toPath(), new File(versionDirectory, "grails-cli-${VERSION}-all.jar").toPath())
    }

    void setup() {
        project = new File(dir, 'shop').canonicalFile
        new File(project, 'grails-app/controllers/shop').mkdirs()
        new File(project, 'grails-app/conf').mkdirs()
        new File(project, 'grails-app/controllers/shop/BookController.groovy').text = 'package shop\n\nclass BookController {\n}\n'
        new File(project, 'settings.gradle').text = "rootProject.name = 'shop'\n"
        new File(project, 'gradle.properties').text = "grailsVersion=${VERSION}\n"
    }

    void "the jar the wrapper downloads has the class the wrapper starts, by the name the wrapper has, and the two agree on the property"() {
        given: 'the constants of the wrapper, read from the jar of the wrapper in a class loader of that jar alone'
        File wrapperJar = new File(dir, 'grails-wrapper.jar')
        wrapperJar.bytes = inJar('grails-wrapper.jar')
        URLClassLoader loader = new URLClassLoader([wrapperJar.toURI().toURL()] as URL[], ClassLoader.platformClassLoader)
        Class<?> startClass = Class.forName('grails.init.StartClass', true, loader)
        String name = startClass.getField('NAME').get(null)
        String property = startClass.getField('WRAPPER_VERSION_PROPERTY').get(null)

        expect:
        name == DelegatingShellApplication.name
        new JarFile(allJar).withCloseable { JarFile jar -> jar.getEntry(name.replace('.', '/') + '.class') != null }
        property == DelegatingShellApplication.WRAPPER_VERSION

        and: 'the class of the jar is this one, which the jar of this artifact has, and not the command line'
        new JarFile(allJar).withCloseable { JarFile jar -> jar.getInputStream(jar.getEntry(name.replace('.', '/') + '.class')).bytes } ==
                DelegatingShellApplication.getResourceAsStream('DelegatingShellApplication.class').withCloseable { InputStream stream -> stream.readAllBytes() }

        cleanup:
        loader?.close()
    }

    void "the wrapper of this version starts the command line and replaces no file of the wrapper: #args"() {
        given: 'the wrapper of this version, with scripts that are not those of the version'
        wrapperOfThisVersionIn(project)
        Map<String, byte[]> before = WRAPPER.collectEntries { String name -> [(name): new File(project, name).bytes] }

        when:
        Map result = wrapper(project, [:], args as String[])

        then:
        result.status == status
        (result.out + result.err).contains(said)
        !result.out.contains('The Grails wrapper of')
        WRAPPER.every { String name -> new File(project, name).bytes == before[name] }

        where:
        args                                   | status | said
        ['--version']                          | 0      | 'Grails Version: '
        ['stats']                              | 0      | '| Controllers          |     1 |'
        ['--plain-output', 'stats']            | 0      | '| Controllers          |     1 |'
        ['no-such-command']                    | 2      | 'There is no command no-such-command'
        ['create-app', '--no-such-option']     | 2      | '--no-such-option'
    }

    void "the wrapper of this version starts the command line outside a project"() {
        given:
        File outside = new File(dir, 'tools').canonicalFile
        outside.mkdirs()
        wrapperOfThisVersionIn(outside)

        when: 'outside a project the version is the one the environment names'
        Map result = wrapper(outside, [PREFERRED_GRAILS_VERSION: VERSION], '--version')

        then:
        result.status == 0
        result.out.contains('Grails Version: ')
        !result.out.contains('The Grails wrapper of')
    }

    void "the class replaces the wrapper of the project first when it is not told that a wrapper of Grails 9 or later started it"() {
        given: 'a project whose files of the wrapper are not those of this version'
        WRAPPER.each { String name -> new File(project, name).text = "the ${name} of Grails 8\n" }

        when: 'it is started with the property, as the wrapper of this version starts it'
        Map told = startClass(project, ["-D${DelegatingShellApplication.WRAPPER_VERSION}=${VERSION}".toString()], 'stats')

        then: 'the command runs and nothing is replaced'
        told.status == 0
        told.out.contains('| Controllers          |     1 |')
        !told.out.contains('The Grails wrapper of')
        WRAPPER.every { String name -> new File(project, name).text == "the ${name} of Grails 8\n" }

        when: 'it is started without the property, as the wrapper of Grails 7 and 8 starts it'
        Map earlier = startClass(project, [], 'stats')

        then: 'the files are replaced, and the command runs after it'
        earlier.status == 0
        earlier.out.readLines()[0].startsWith("The Grails wrapper of the project in ${project.path} ")
        earlier.out.contains('| Controllers          |     1 |')
        WRAPPER.every { String name -> new File(project, name).bytes == inJar(name) }
    }

    private byte[] inJar(String name) {
        return new JarFile(allJar).withCloseable { JarFile jar -> jar.getInputStream(jar.getEntry("grails-wrapper/${name}")).bytes }
    }

    private void wrapperOfThisVersionIn(File directory) {
        WRAPPER.each { String name -> new File(directory, name).bytes = inJar(name) }
        new File(directory, 'grailsw').text = '#!/bin/sh\n# the script of another release\n'
        new File(directory, 'grailsw.bat').text = '@rem the script of another release\r\n'
    }

    // runs the grails-wrapper.jar of the directory as its scripts do, which downloads the command line from the repository
    private Map wrapper(File directory, Map<String, String> environment, String... args) {
        File home = new File(dir, 'home')
        home.mkdirs()
        return start(directory, [GRAILS_REPO_URL: repository.path, GRAILS_WRAPPER_HOME: home.path] + environment,
                ['-cp', new File(directory, 'grails-wrapper.jar').path, 'grails.init.Start'] + args.toList())
    }

    // runs the class every wrapper starts, from the jar the wrapper downloads, as a wrapper runs it once it has the jar
    private Map startClass(File directory, List<String> options, String... args) {
        return start(directory, [:], options + ['-cp', allJar.path, DelegatingShellApplication.name] + args.toList())
    }

    private Map start(File directory, Map<String, String> environment, List<String> arguments) {
        File userHome = new File(dir, 'user-home')
        userHome.mkdirs()
        // nothing here asks the network for anything: what did would be refused at once
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, "-Duser.home=${userHome.path}".toString(),
                                '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=9', '-Dhttp.proxyHost=127.0.0.1', '-Dhttp.proxyPort=9'] + arguments
        File out = File.createTempFile('out', '.txt', dir)
        File err = File.createTempFile('err', '.txt', dir)
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory).redirectOutput(out).redirectError(err)
        ['GRAILS_REPO_URL', 'GRAILS_WRAPPER_HOME', 'PREFERRED_GRAILS_VERSION', 'GRAILS_WRAPPER_ALLOWED_TYPES', 'GRAILS_SAFE_DIRECTORY', 'JAVA_TOOL_OPTIONS'].each { String name ->
            builder.environment().remove(name)
        }
        builder.environment().putAll(environment)
        // the input stays open, as that of a terminal does
        Process process = builder.start()
        try {
            assert process.waitFor(180, TimeUnit.SECONDS): "the command line did not end:\n${out.text}\n${err.text}"
            return [status: process.exitValue(), out: out.text, err: err.text]
        } finally {
            process.destroyForcibly()
        }
    }
}
