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
package org.grails.gradle.plugin.core

import java.nio.charset.StandardCharsets

import javax.tools.JavaCompiler
import javax.tools.ToolProvider

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir

/**
 * What a tool of a feature pack reads of what the JVM that runs Forge writes: both of its outputs as
 * UTF-8, also where the platform writes its output in another encoding.
 */
class ForgePackToolOutputSpec extends Specification {

    static final String SAID = 'Paquet « crème brûlée » ✓'

    @TempDir
    File directory

    // a locale whose encoding is US-ASCII stands for a console of Windows in another code page
    @IgnoreIf({ os.windows })
    void "what Forge writes on either output is read with its letters, in a JVM whose platform encoding is US-ASCII"() {
        given: 'a class that writes a line with letters outside ASCII on each output'
        File classes = new File(directory, 'classes')
        classes.mkdirs()
        File source = new File(directory, 'Writes.java')
        // the line is in the class, as escapes: an argument would be read in the encoding of the platform
        source.setText('''
            public class Writes {
                public static void main(String[] args) {
                    String line = "Paquet \\u00ab cr\\u00e8me br\\u00fbl\\u00e9e \\u00bb \\u2713";
                    System.out.println(line);
                    System.err.println(line);
                }
            }
        '''.stripIndent(), StandardCharsets.UTF_8.name())
        JavaCompiler compiler = ToolProvider.systemJavaCompiler
        assert compiler.run(null, null, null, '-encoding', 'UTF-8', '-d', classes.absolutePath, source.absolutePath) == 0

        and: 'a task of a pack tool that runs it'
        Project project = ProjectBuilder.builder().withProjectDir(new File(directory, 'project')).build()
        CheckForgePackTask task = project.tasks.register('checkForgePack', CheckForgePackTask).get()
        task.toolingClasspath.from(classes)

        when:
        ForgePackToolTask.Launched launched = task.launch('Writes', [], [LC_ALL: 'C', LANG: 'C'])

        then:
        launched.exitValue == 0
        launched.written == [SAID]
        launched.said == [SAID]
    }
}
