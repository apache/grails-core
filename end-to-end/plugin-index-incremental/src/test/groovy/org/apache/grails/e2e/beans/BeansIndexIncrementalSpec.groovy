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
package org.apache.grails.e2e.beans

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.annotation.ImportCandidates
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification
import spock.lang.TempDir

class BeansIndexIncrementalSpec extends PluginIndexFixture {

    void 'removing the last beans contributor leaves only the hand-authored registration'() {
        given:
        File sources = new File(projectDir, 'src/main/groovy/demo')
        sources.mkdirs()
        new File(sources, 'ManualConfiguration.groovy').text = '''
            package demo
            import org.springframework.boot.autoconfigure.AutoConfiguration
            import org.springframework.context.annotation.Bean

            @AutoConfiguration
            class ManualConfiguration {
                @Bean String manualGreeting() { 'manual' }
            }
        '''
        File descriptor = new File(sources, 'GreetingGrailsPlugin.groovy')
        String base = '''
            package demo
            import grails.plugins.Plugin
            import grails.plugins.metadata.GrailsPluginDescriptor
            import grails.compiler.beans.GrailsBeans
            import org.springframework.boot.autoconfigure.AutoConfiguration

            @GrailsBeans
            @AutoConfiguration
            @GrailsPluginDescriptor
            class GreetingGrailsPlugin extends Plugin {
        '''
        descriptor.text = base + "\n def beans = { bean('generatedGreeting', String) { 'generated' } }\n}"
        // This independent resource JAR registers a class from the plugin, as applications may do.
        File manual = new File(projectDir, 'manual-imports.jar')
        new JarOutputStream(new FileOutputStream(manual)).withCloseable { jar ->
            jar.putNextEntry(new JarEntry('META-INF/spring/' + AutoConfiguration.name + '.imports'))
            jar.write('demo.ManualConfiguration\n'.getBytes('UTF-8'))
            jar.closeEntry()
        }

        when:
        build('jar')

        then:
        greetings(manual) == ['generatedGreeting', 'manualGreeting']

        when:
        if (removeSource) {
            assert descriptor.delete()
        } else {
            descriptor.text = base.replace('@GrailsBeans', '') + '}'
        }
        build('jar')
        def incremental = greetings(manual)

        then:
        incremental == ['manualGreeting']

        when:
        build('clean', 'jar')

        then:
        greetings(manual) == incremental

        where:
        removeSource << [true, false]
    }

    private List<String> greetings(File manual) {
        new URLClassLoader([snapshot().toURI().toURL(), manual.toURI().toURL()] as URL[], getClass().classLoader).withCloseable { loader ->
            new AnnotationConfigApplicationContext().withCloseable { context ->
                context.classLoader = loader
                ImportCandidates.load(AutoConfiguration, loader).findAll { it.startsWith('demo.') }.each {
                    context.register(loader.loadClass(it))
                }
                context.refresh()
                context.getBeanNamesForType(String).sort()
            }
        }
    }

}
