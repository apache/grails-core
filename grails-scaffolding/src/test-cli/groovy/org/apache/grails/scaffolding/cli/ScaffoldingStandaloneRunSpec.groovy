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
package org.apache.grails.scaffolding.cli

import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.core.cli.StandaloneToolRunner
import org.apache.grails.gradle.model.cli.JvmOptions

/**
 * The scaffolding tools run as the {@code grails} command line starts a tool that runs on its own: by
 * {@link StandaloneToolRunner}, in a JVM of its own whose working directory and {@code base.dir} are the
 * directory of the project, with nothing but the tools and their class path.
 */
class ScaffoldingStandaloneRunSpec extends Specification {

    @TempDir
    File project

    /** A directory of the class path the tool is started with, outside the project */
    @TempDir
    File classpath

    /** What the JVM that is started is given and writes, outside the project */
    @TempDir
    File jvm

    def setup() {
        write('grails-app/domain/com/example/Book.groovy', 'package com.example\n\nclass Book {\n    String title\n}\n')
    }

    def "generate-views reads the record of the project it is started in, and writes the JSON views of a REST API application"() {
        given: 'the record the command line writes for a REST API application'
        write('.grails/forge.lock.yml', recordSample('rest-api'))

        when:
        Started started = start('generate-views', 'com.example.Book')

        then:
        started.exit == 0
        generatedViews() == ['_book.gson', 'index.gson', 'show.gson']
    }

    def "generate-views renders each GSP view once where its class path has the templates of the plugin more than once"() {
        given: 'a project without templates of its own, and without a record, which is given GSP views'
        assert !new File(project, 'src/main/templates').exists()

        and: 'a copy of the templates of the plugin beside the one the plugin has, as the build has one in the jar of the plugin and one beside the tools'
        File copy = new File(classpath, 'META-INF/templates/scaffolding')
        copy.mkdirs()
        new File('src/main/templates/scaffolding').eachFile { File template ->
            new File(copy, template.name).bytes = template.bytes
        }
        URLClassLoader started = new URLClassLoader([classpath.toURI().toURL()] as URL[], getClass().classLoader)
        assert Collections.list(started.getResources('META-INF/templates/scaffolding/index.gsp')).size() > 1
        started.close()

        when:
        Started run = start('generate-views', 'com.example.Book')

        then: 'each view is rendered once, and none is passed over as being there already'
        run.exit == 0
        generatedViews() == ['create.gsp', 'edit.gsp', 'index.gsp', 'show.gsp']
        ['create', 'edit', 'index', 'show'].every { String view -> run.out.count("Rendered template ${view}.gsp ") == 1 }
        !run.out.contains('already exists')
    }

    /**
     * Starts a tool as the command line starts one that runs on its own, and waits for it to end.
     */
    private Started start(String... arguments) {
        File options = new File(jvm, 'java.options')
        String path = System.getProperty('java.class.path') + File.pathSeparator + classpath.absolutePath
        options.setText("-cp\n\"${path.replace('\\', '\\\\')}\"\n", 'UTF-8')
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        List<String> counted = coverage ? [coverage.replace('@fork@', UUID.randomUUID().toString())] : []
        File home = new File(jvm, 'home')
        home.mkdirs()
        File out = new File(jvm, 'stdout.txt')
        File err = new File(jvm, 'stderr.txt')
        // the options the grails command line starts a standalone tool with
        List<String> jvmOptions = JvmOptions.of(Runtime.version().feature())
        Process process = new ProcessBuilder([new File(System.getProperty('java.home'), 'bin/java').path] + jvmOptions + ["@${options.path}".toString()] + counted +
                ["-Dbase.dir=${project.path}".toString(), "-Duser.home=${home.path}".toString(), StandaloneToolRunner.name] + arguments.toList())
                .directory(project)
                .redirectOutput(out)
                .redirectError(err)
                .start()
        assert process.waitFor(5, TimeUnit.MINUTES): 'the tool ended'
        new Started(exit: process.exitValue(), out: out.getText('UTF-8'), err: err.getText('UTF-8'))
    }

    private static String recordSample(String type) {
        String samples = System.getProperty('scaffolding.projectRecordSamples')
        assert samples: 'the build hands the directory of the sample records of the command line to the tests'
        new File(samples, "${type}/.grails/forge.lock.yml").getText('UTF-8')
    }

    private void write(String path, String text) {
        File file = new File(project, path)
        file.parentFile.mkdirs()
        file.text = text
    }

    /**
     * @return the names of the views generated for the domain class, in order
     */
    private List<String> generatedViews() {
        File views = new File(project, 'grails-app/views/book')
        views.isDirectory() ? views.list().toList().sort() : []
    }

    /** How a tool that was started ended, and what it wrote */
    private static class Started {

        int exit
        String out
        String err

        @Override
        String toString() {
            "exit ${exit}\n${out}\n${err}"
        }
    }
}
