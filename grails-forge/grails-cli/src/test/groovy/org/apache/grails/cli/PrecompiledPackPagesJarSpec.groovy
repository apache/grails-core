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

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The pages of the templates of the packs of Forge, which its build compiles, in the jar of the
 * {@code grails} command with everything in it: {@code grails-cli-VERSION-all.jar}, as this build
 * makes it and as the distribution has it.
 */
class PrecompiledPackPagesJarSpec extends Specification {

    private static final String INDEX = 'META-INF/grails-forge-pages/index'
    private static final String PAGES = 'org/apache/grails/forge/core/pack/pages/'

    @Shared
    File allJar = new File(System.getProperty('grails.cli.all.jar'))

    @TempDir
    File dir

    void "the jar has the index of the pages and, under the name the index gives it, the class of each"() {
        given:
        List<String> lines
        Set<String> entries
        new JarFile(allJar).withCloseable { JarFile jar ->
            lines = new String(jar.getInputStream(jar.getEntry(INDEX)).readAllBytes(), StandardCharsets.UTF_8).readLines()
            entries = jar.entries().toList()*.name as Set<String>
        }

        expect: 'a line for each template: its key, the digest of its source and the name of its class'
        lines.size() > 20
        lines.every { String line -> line ==~ /[0-9a-f]{64}\t[0-9a-f]{64}\torg\.apache\.grails\.forge\.core\.pack\.pages\.PackPage_[0-9a-f]{64}/ }

        and: 'the class is there under that name: putting the jars into one renamed nothing'
        lines.findAll { String line -> !entries.contains(line.split('\t')[2].replace('.', '/') + '.class') } == []

        and: 'and there is no class of a page the index does not name'
        entries.findAll { it.startsWith(PAGES) && it.endsWith('.class') }.collect { it.substring(0, PAGES.length() + 'PackPage_'.length() + 64).replace('/', '.') }.toSet() ==
                lines.collect { String line -> line.split('\t')[2] }.toSet()
    }

    void "the pages are of a package of their own, and the only classes without a package are the compiled views of the generator"() {
        given:
        Set<String> entries
        new JarFile(allJar).withCloseable { JarFile jar -> entries = jar.entries().toList()*.name as Set<String> }
        List<String> withoutPackage = entries.findAll { it.endsWith('.class') && !it.contains('/') }.toList()

        expect: 'a jar that is a module of Java can have no class without a package: the pages add none'
        withoutPackage.findAll { it.contains('PackPage_') } == []

        and: 'what is left without one are the views of the generator, which the GSP compiler of the build names so'
        withoutPackage.findAll { !it.startsWith('gsp_grails_forge_') } == []

        and: 'the package of the pages has the pages and nothing else'
        entries.findAll { it.startsWith(PAGES) && !it.endsWith('/') }.every { it.substring(PAGES.length()) ==~ /PackPage_[0-9a-f]{64}(\$[\w$]+)?\.class/ }
    }

    void "the command line in the jar creates an application with the pages of the jar, and compiles no template of a pack"() {
        given: 'the command line in a JVM of its own, which writes down every class it loads'
        File userHome = new File(dir, 'user-home')
        userHome.mkdirs()
        File classes = new File(dir, 'classes.log')
        File out = new File(dir, 'out.log')
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, "-Duser.home=${userHome.path}".toString(),
                                "-Xlog:class+load:file=${classes.path}".toString(), '-cp', allJar.path, 'org.apache.grails.cli.GrailsCli',
                                'create-app', 'com.example.demo', '--features=asciidoctor']
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(out)
        ['COLORTERM', 'JAVA_OPTS', 'GRAILS_OPTS', 'JAVA_TOOL_OPTIONS', 'GRAILS_ENV'].each { builder.environment().remove(it) }

        when:
        Process process = builder.start()

        then:
        assert process.waitFor(180, TimeUnit.SECONDS): "the command line did not end:\n${out.text}"
        assert process.exitValue() == 0: out.text
        new File(dir, 'demo/src/docs/asciidoc/index.adoc').isFile()

        when:
        List<String> loaded = classes.readLines()
        List<String> pages = loaded.findAll { it.contains('[class,load] org.apache.grails.forge.core.pack.pages.PackPage_') }

        then: 'the pages of the templates of the default features and of the one that was asked for, from the jar'
        pages.size() >= 5
        pages.every { it.contains('source: ') && it.endsWith(allJar.name) }

        and: 'and no class that is compiled from the template of a pack when it is rendered'
        loaded.findAll { it =~ /\[class,load\] template\d+_gsp/ } == []

        cleanup:
        process?.destroyForcibly()
    }
}
