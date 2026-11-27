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
import spock.lang.Unroll

import org.apache.grails.core.cli.StandaloneToolRunner

/**
 * The scaffolding tools declare their options and the domain classes they take, and are held to them as the
 * {@code grails} command line runs them: by {@link StandaloneToolRunner}, in a JVM of its own. What a tool does
 * not declare is refused with exit code 2 before the tool runs, and the tool writes nothing.
 */
class ScaffoldingDeclaredArgumentsSpec extends Specification {

    @TempDir
    File project

    /** What the JVM that is started is given and writes, outside the project */
    @TempDir
    File jvm

    def setup() {
        write('grails-app/domain/com/example/Book.groovy', 'package com.example\n\nclass Book {\n    String title\n}\n')
    }

    def "a declared option takes its value from the next argument, and a switch takes none, so the domain class after it is the parameter"() {
        when: 'the kind of views is the word after --views, and --force is followed by the domain class'
        Started started = start('generate-views', '--views', 'json', '--force', 'com.example.Book')

        then:
        started.exit == 0
        generatedViews() == ['_book.gson', 'index.gson', 'show.gson']
    }

    @Unroll
    def "#arguments is refused with exit code 2, says #what, and writes nothing"() {
        when:
        Started started = start(arguments as String[])

        then: 'the line is in what the tool printed: the tools make the console of Grails when they are made, which writes what is printed to System.err to the output'
        started.exit == 2
        (started.out + started.err).readLines().contains("| Error ${arguments[0]}: ${what}. grails help ${arguments[0]} shows its options".toString())
        generatedViews() == []
        !new File(project, 'grails-app/controllers').exists()

        where:
        arguments                                                           || what
        ['generate-views', 'com.example.Book', '--vews=json']               || '--vews is not one of its options'
        ['generate-all', 'com.example.Book', '--forse']                     || '--forse is not one of its options'
        ['generate-views', 'com.example.Book', '--force=true']              || '--force takes no value'
        ['generate-views', 'com.example.Book', '--views']                   || '--views needs a value'
        ['generate-controller']                                             || 'DOMAIN-CLASS is required'
        ['create-scaffold-controller', 'com.example.Book', 'com.example.Other'] || 'it takes at most 1 parameter, and was given 2'
    }

    /**
     * Starts a tool as the command line starts one that runs on its own, and waits for it to end.
     */
    private Started start(String... arguments) {
        File options = new File(jvm, 'java.options')
        options.setText("-cp\n\"${System.getProperty('java.class.path').replace('\\', '\\\\')}\"\n", 'UTF-8')
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        List<String> counted = coverage ? [coverage.replace('@fork@', UUID.randomUUID().toString())] : []
        File home = new File(jvm, 'home')
        home.mkdirs()
        File out = new File(jvm, 'stdout.txt')
        File err = new File(jvm, 'stderr.txt')
        Process process = new ProcessBuilder([new File(System.getProperty('java.home'), 'bin/java').path, "@${options.path}".toString()] + counted +
                ["-Dbase.dir=${project.path}".toString(), "-Duser.home=${home.path}".toString(), StandaloneToolRunner.name] + arguments.toList())
                .directory(project)
                .redirectOutput(out)
                .redirectError(err)
                .start()
        assert process.waitFor(5, TimeUnit.MINUTES): 'the tool ended'
        new Started(exit: process.exitValue(), out: out.getText('UTF-8'), err: err.getText('UTF-8'))
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
