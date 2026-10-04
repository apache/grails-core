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
package org.grails.web.pages

import spock.lang.Specification
import spock.lang.TempDir

import grails.util.BuildSettings

class GroovyPageForkedCompilerSpec extends Specification {

    @TempDir
    File dir

    private File pageList(String name, String... pages) {
        File list = new File(dir, name)
        list.setText(pages.join('\n'), 'UTF-8')
        list
    }

    void 'the optional pages reach the page compiler from the build, from every list it is given'() {
        given:
        String lists = [pageList('scaffolded.txt', 'grails-scaffolded/a.gsp', '', 'grails-scaffolded/b.gsp'),
                        pageList('other.txt', 'other/c.gsp'),
                        new File(dir, 'missing.txt')]*.path.join(File.pathSeparator)
        String previous = System.getProperty(BuildSettings.OPTIONAL_GSP_PAGES)
        System.setProperty(BuildSettings.OPTIONAL_GSP_PAGES, lists)

        when:
        def compiler = new GroovyPageForkedCompiler(dir, dir, dir).createPageCompiler()

        then:
        compiler.optionalPages == ['grails-scaffolded/a.gsp', 'grails-scaffolded/b.gsp', 'other/c.gsp'] as Set

        cleanup:
        previous == null ? System.clearProperty(BuildSettings.OPTIONAL_GSP_PAGES) :
                System.setProperty(BuildSettings.OPTIONAL_GSP_PAGES, previous)
    }

    void 'an optional page left out is printed, which is what the build shows of this process'() {
        given:
        File views = new File(dir, 'views')
        new File(views, 'generated').mkdirs()
        File broken = new File(views, 'generated/broken.gsp')
        broken.text = '<% def x = ; %>'
        File classes = new File(dir, 'classes')
        File work = new File(dir, 'work')
        [classes, work]*.mkdirs()
        def compiler = new GroovyPageForkedCompiler(views, classes, work)
        String previous = System.getProperty(BuildSettings.OPTIONAL_GSP_PAGES)
        System.setProperty(BuildSettings.OPTIONAL_GSP_PAGES, pageList('optional.txt', 'generated/broken.gsp').path)
        PrintStream err = System.err
        ByteArrayOutputStream printed = new ByteArrayOutputStream()
        System.err = new PrintStream(printed, true, 'UTF-8')

        when:
        compiler.compile([broken])

        then:
        printed.toString('UTF-8').contains('Left out the optional page generated/broken.gsp')

        cleanup:
        System.err = err
        previous == null ? System.clearProperty(BuildSettings.OPTIONAL_GSP_PAGES) :
                System.setProperty(BuildSettings.OPTIONAL_GSP_PAGES, previous)
    }

    void 'the directories of generated pages reach the page compiler from the build, and their pages are compiled'() {
        given:
        File views = new File(dir, 'views')
        File generated = new File(dir, 'generated')
        File classes = new File(dir, 'classes')
        File work = new File(dir, 'work')
        [views, classes, work]*.mkdirs()
        new File(views, 'index.gsp').text = '<p>index</p>'
        new File(generated, 'grails-scaffolded').mkdirs()
        new File(generated, 'grails-scaffolded/show.gsp').text = '<p>show</p>'
        String previous = System.getProperty(BuildSettings.GENERATED_GSP_VIEW_DIRECTORIES)
        System.setProperty(BuildSettings.GENERATED_GSP_VIEW_DIRECTORIES,
                [generated, new File(dir, 'missing')]*.path.join(File.pathSeparator))

        when:
        def compiler = new GroovyPageForkedCompiler(views, classes, work).createPageCompiler()
        GroovyPageForkedCompiler.run([views.path, classes.path, work.path, '21', 'probe', '/', '', 'UTF-8'] as String[])
        Properties registry = new Properties()
        new File(classes, 'gsp/views.properties').withInputStream { registry.load(it) }

        then:
        compiler.generatedViewsDirs == [generated]
        registry.keySet() == ['/index.gsp', '/grails-scaffolded/show.gsp'] as Set

        cleanup:
        previous == null ? System.clearProperty(BuildSettings.GENERATED_GSP_VIEW_DIRECTORIES) :
                System.setProperty(BuildSettings.GENERATED_GSP_VIEW_DIRECTORIES, previous)
    }

    void 'without the setting no page is optional'() {
        given:
        String previous = System.getProperty(BuildSettings.OPTIONAL_GSP_PAGES)
        System.clearProperty(BuildSettings.OPTIONAL_GSP_PAGES)

        expect:
        new GroovyPageForkedCompiler(dir, dir, dir).createPageCompiler().optionalPages.isEmpty()

        cleanup:
        previous == null ? System.clearProperty(BuildSettings.OPTIONAL_GSP_PAGES) :
                System.setProperty(BuildSettings.OPTIONAL_GSP_PAGES, previous)
    }
}
