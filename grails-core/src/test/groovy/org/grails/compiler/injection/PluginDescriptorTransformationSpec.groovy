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
package org.grails.compiler.injection

import java.nio.file.Files

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Specification
import spock.lang.TempDir

import org.apache.grails.core.plugins.PluginUtils

class PluginDescriptorTransformationSpec extends Specification {

    @TempDir
    File project

    void 'plain Groovy compilation discovers both plugins and reconciles removed classes on recompilation'() {
        given:
        File output = new File(project, 'classes')
        File sources = new File(project, 'src/test/groovy')
        sources.mkdirs()
        File alpha = new File(sources, 'AlphaGrailsPlugin.groovy')
        alpha.text = "class AlphaGrailsPlugin { def version = '1.0' }"
        File beta = new File(sources, 'BetaGrailsPlugin.groovy')
        beta.text = "class BetaGrailsPlugin { def version = '1.0' }"
        writeService(output, 'SampleService')

        when:
        compile(output, alpha, beta)

        then:
        descriptors(output) == [alpha: ['SampleService'], beta: ['SampleService']]

        when: 'the incremental compiler driver removes outputs belonging to deleted sources'
        Files.delete(new File(output, 'AlphaGrailsPlugin.class').toPath())
        Files.delete(new File(output, 'SampleService.class').toPath())
        writeService(output, 'RenamedService')
        compile(output, beta)

        then:
        descriptors(output) == [beta: ['RenamedService']]
        !new File(output, 'META-INF/grails/plugins/alpha/plugin.properties').exists()

        when:
        Files.delete(new File(output, 'BetaGrailsPlugin.class').toPath())
        File unrelated = new File(sources, 'Unrelated.groovy')
        unrelated.text = 'class Unrelated {}'
        compile(output, unrelated)

        then:
        descriptors(output).isEmpty()
        !new File(output, 'META-INF/grails/plugins.imports').exists()
        !new File(output, 'META-INF/grails/plugins/beta/plugin.properties').exists()
    }

    void 'javac discovers an annotated Java plugin without Gradle'() {
        given:
        File source = new File(project, 'JavaPlugin.java')
        source.text = '@grails.plugins.metadata.GrailsPluginDescriptor(name="java-probe") public class JavaPlugin {}'
        File output = new File(project, 'java-classes')
        output.mkdirs()
        def compiler = javax.tools.ToolProvider.systemJavaCompiler
        def files = compiler.getStandardFileManager(null, null, null)
        String classpath = new File(grails.plugins.metadata.GrailsPluginDescriptor.protectionDomain.codeSource.location.toURI()).path
        def task = compiler.getTask(null, files, null, ['-classpath', classpath, '-d', output.path,
                '-Agrails.plugin.version=3.0'], null, files.getJavaFileObjects(source))
        task.processors = [new JavaPluginDescriptorProcessor()]

        expect:
        task.call()
        descriptors(output) == [javaProbe: []]
        new File(output, 'META-INF/grails/plugins/javaProbe/plugin.properties').text.readLines().contains('version=3.0')

        cleanup:
        files?.close()
    }

    private void writeService(File output, String name) {
        File source = new File(project, name + '.java')
        source.text = "@grails.artefact.Artefact(\"Service\") public class ${name} {}"
        output.mkdirs()
        String classpath = new File(grails.artefact.Artefact.protectionDomain.codeSource.location.toURI()).path
        assert javax.tools.ToolProvider.systemJavaCompiler.run(null, null, null,
                '-proc:none', '-classpath', classpath, '-d', output.path, source.path) == 0
    }

    private static void compile(File output, File... sources) {
        def unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: output))
        unit.addSources(sources)
        unit.compile()
    }

    private static Map descriptors(File output) {
        new URLClassLoader([output.toURI().toURL()] as URL[], (ClassLoader) null).withCloseable { loader ->
            PluginUtils.scanPluginDescriptorResources(loader).collectEntries {
                [(it.pluginName): it.providedClasses]
            }
        }
    }
}
