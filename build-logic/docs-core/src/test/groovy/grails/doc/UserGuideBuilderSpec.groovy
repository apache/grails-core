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
package grails.doc

import spock.lang.Specification
import spock.lang.TempDir

class UserGuideBuilderSpec extends Specification {

    @TempDir
    File workspace

    private File writeGuideSource() {
        File sourceDir = new File(workspace, 'src')
        File guideDir = new File(sourceDir, 'guide')
        guideDir.mkdirs()
        new File(guideDir, 'toc.yml').text = '''\
            introduction:
              title: Introduction
        '''.stripIndent()
        new File(guideDir, 'introduction.adoc').text = 'A sentence that has to reach the rendered guide.\n'
        sourceDir
    }

    private File writeResources(Map<String, String> docProperties = [title: 'Builder Guide']) {
        File resourcesDir = new File(workspace, 'resources')
        resourcesDir.mkdirs()
        new File(resourcesDir, 'doc.properties').text =
                docProperties.collect { key, value -> "${key}=${value}" }.join('\n') + '\n'
        resourcesDir
    }

    void 'builds the guide from plain values, with no Gradle model to read back'() {
        given: 'a minimal guide source tree'
        File sourceDir = writeGuideSource()
        File resourcesDir = writeResources()
        File targetDir = new File(workspace, 'guide-output')

        when:
        new UserGuideBuilder(
                sourceDir: sourceDir,
                resourcesDir: resourcesDir,
                targetDir: targetDir,
                properties: ['grails.version': '1.2.3'] as Map<String, Object>
        ).build()

        then: 'the guide is written where it was told to'
        new File(targetDir, 'index.html').exists()
        File section = new File(targetDir, 'guide/introduction.html')
        section.exists()
        section.text.contains('A sentence that has to reach the rendered guide.')

        and: 'doc.properties supplies the title'
        new File(targetDir, 'index.html').text.contains('Builder Guide')
    }

    void 'engine properties override the properties files, which override doc.properties'() {
        given: 'a title in doc.properties, in a properties file, and set directly'
        File sourceDir = writeGuideSource()
        File resourcesDir = writeResources([title: 'From doc.properties'])
        File overrides = new File(workspace, 'overrides.properties')
        overrides.text = 'title=From the properties file\n'
        File targetDir = new File(workspace, 'guide-output')

        when:
        new UserGuideBuilder(
                sourceDir: sourceDir,
                resourcesDir: resourcesDir,
                targetDir: targetDir,
                propertiesFiles: [overrides],
                properties: [title: 'From the engine properties'] as Map<String, Object>
        ).build()

        then: 'the directly set value wins'
        new File(targetDir, 'index.html').text.contains('From the engine properties')
    }

    void 'registers extra macros by class name'() {
        given: 'a macro named rather than handed over as an instance'
        File sourceDir = writeGuideSource()
        File resourcesDir = writeResources()
        File targetDir = new File(workspace, 'guide-output')
        GuideProbeMacro.instantiated = false

        when:
        new UserGuideBuilder(
                sourceDir: sourceDir,
                resourcesDir: resourcesDir,
                targetDir: targetDir,
                macroClassNames: [GuideProbeMacro.name]
        ).build()

        then: 'the builder instantiated it'
        GuideProbeMacro.instantiated
        new File(targetDir, 'guide/introduction.html').exists()
    }

    void 'keeps Ant quiet unless asked, so a worker process does not flood the console'() {
        given: 'a guide whose publisher will mkdir, copy and unjar its resources'
        File sourceDir = writeGuideSource()
        File resourcesDir = writeResources()
        File targetDir = new File(workspace, 'guide-output')
        PrintStream realOut = System.out
        ByteArrayOutputStream captured = new ByteArrayOutputStream()

        when:
        System.out = new PrintStream(captured, true)
        try {
            new UserGuideBuilder(
                    sourceDir: sourceDir,
                    resourcesDir: resourcesDir,
                    targetDir: targetDir
            ).build()
        }
        finally {
            System.out = realOut
        }

        then: "Ant's own INFO chatter stays out of the build output"
        !captured.toString().contains('[mkdir]')
        !captured.toString().contains('[copy]')

        and: 'the guide was still written'
        new File(targetDir, 'index.html').exists()
    }

    void 'prints Ant INFO messages when the build asked for them'() {
        given: 'the same guide, with verbose Ant logging'
        File sourceDir = writeGuideSource()
        File resourcesDir = writeResources()
        File targetDir = new File(workspace, 'guide-output')
        PrintStream realOut = System.out
        ByteArrayOutputStream captured = new ByteArrayOutputStream()

        when:
        System.out = new PrintStream(captured, true)
        try {
            new UserGuideBuilder(
                    sourceDir: sourceDir,
                    resourcesDir: resourcesDir,
                    targetDir: targetDir,
                    verboseAnt: true
            ).build()
        }
        finally {
            System.out = realOut
        }

        then: 'the diagnostics are available when they are wanted'
        captured.toString().contains('[mkdir]')
    }

    void 'empties the target directory before writing'() {
        given: 'a target directory holding output from an earlier build'
        File sourceDir = writeGuideSource()
        File resourcesDir = writeResources()
        File targetDir = new File(workspace, 'guide-output')
        targetDir.mkdirs()
        File stale = new File(targetDir, 'stale.html')
        stale.text = 'from a previous run'

        when:
        new UserGuideBuilder(
                sourceDir: sourceDir,
                resourcesDir: resourcesDir,
                targetDir: targetDir
        ).build()

        then:
        !stale.exists()
        new File(targetDir, 'index.html').exists()
    }
}
