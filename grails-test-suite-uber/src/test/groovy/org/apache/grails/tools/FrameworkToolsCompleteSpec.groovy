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
package org.apache.grails.tools

import java.util.jar.JarFile

import spock.lang.Shared
import spock.lang.Specification

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Every tool Grails ships is complete: in each {@code -cli} and {@code -cli-standalone} jar of the framework,
 * every tool that is registered has a descriptor with a description, its hints, and the declarations of the
 * arguments its code reads, so that the descriptor has no {@code incomplete}. That is what {@code help},
 * completion and agents read of a tool, and a framework tool lacks none of it.
 *
 * <p>The jars are the ones the build makes, which the task {@code frameworkToolsTest} of this project hands the
 * spec, of every project of the framework that publishes one. A tool that lacks something is named with what it
 * lacks, by the jar it is in.</p>
 */
class FrameworkToolsCompleteSpec extends Specification {

    @Shared
    List<File> jars = companionJars()

    def "the build hands the spec the jars of the tools of the framework"() {
        expect: 'among them the jars of the tool API and of a plugin whose tools run on their own'
        jars*.name.any { String name -> name.startsWith('grails-core-cli-') }
        jars*.name.any { String name -> name.startsWith('grails-scaffolding-cli-standalone-') }

        and: 'they register tools'
        jars.sum { File jar -> registeredClasses(jar).size() } > 0
    }

    def "every tool of the framework has a description, hints and the declarations of the arguments it reads"() {
        expect: 'no jar has a tool that lacks any of them'
        lacking() == [:]
    }

    /**
     * @return by the name of each jar that has one, what each of its tools that is not complete lacks
     */
    private Map<String, List<String>> lacking() {
        Map<String, List<String>> lacking = new TreeMap<String, List<String>>()
        for (File jar : jars) {
            List<String> tools = new JarFile(jar).withCloseable { JarFile archive ->
                registeredClasses(archive).collect { String className -> lacks(archive, className) }.findAll()
            }
            if (tools) {
                lacking.put(jar.name, tools)
            }
        }
        lacking
    }

    // what the tool lacks, as one line that names it, or null when it lacks nothing
    private static String lacks(JarFile archive, String className) {
        def entry = archive.getEntry(ToolDescriptor.location(className))
        if (entry == null) {
            return "${className}: no descriptor".toString()
        }
        ToolDescriptor.Described described
        try {
            described = ToolDescriptor.read(className, archive.getInputStream(entry).bytes)
        } catch (ToolDescriptor.Refused refused) {
            return "${className}: a descriptor that ${refused.message}".toString()
        }
        List<String> lacks = []
        if (!described.description) {
            lacks << 'no description'
        }
        if (described.declarations.hints == null) {
            lacks << 'no hints'
        }
        if (described.declarations.incomplete) {
            lacks << "incomplete ${described.declarations.incomplete}".toString()
        }
        lacks ? "${described.name} (${className}): ${lacks.join(', ')}".toString() : null
    }

    private static List<String> registeredClasses(File jar) {
        new JarFile(jar).withCloseable { JarFile archive -> registeredClasses(archive) }
    }

    private static List<String> registeredClasses(JarFile archive) {
        def factories = archive.getEntry('META-INF/grails-cli.factories')
        if (factories == null) {
            return []
        }
        Properties registered = new Properties()
        archive.getInputStream(factories).withCloseable { InputStream input -> registered.load(input) }
        ToolDescriptor.FACTORIES_KEYS.collectMany { String key ->
            (registered.getProperty(key) ?: '').tokenize(',')*.trim().findAll()
        }.unique()
    }

    private static List<File> companionJars() {
        String paths = System.getProperty('framework.tools.jars')
        assert paths: 'the task frameworkToolsTest hands the spec the jars of the tools of the framework'
        paths.split(File.pathSeparator).collect { String path -> new File(path) }.sort { File jar -> jar.name }
    }
}
