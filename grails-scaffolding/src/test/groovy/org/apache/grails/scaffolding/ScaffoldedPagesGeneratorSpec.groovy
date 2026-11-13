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
package org.apache.grails.scaffolding

import java.nio.charset.StandardCharsets

import spock.lang.Specification
import spock.lang.TempDir

class ScaffoldedPagesGeneratorSpec extends Specification {

    @TempDir
    File dir

    File templates
    File output

    void setup() {
        templates = new File(dir, 'templates')
        output = new File(dir, 'out')
        template('show', 'show ${className}')
        template('admin/show', 'admin show ${className}')
    }

    void template(String path, String text) {
        File file = new File(templates, "${path}.gsp")
        file.parentFile.mkdirs()
        file.setText(text, 'UTF-8')
    }

    /** What a page renders: all of it but the comment it ends with. */
    static String rendered(File page) {
        page.getText('UTF-8').replaceFirst(/%\{--[^%]*--}%$/, '')
    }

    /** The page's path under the output directory, as the generator reports it: the same on every platform. */
    String pagePath(String templatePath, String domain, String text) {
        Map<String, Object> model = new ScaffoldedPagesGenerator().model(domain).asMap()
        ScaffoldedPages.uri(templatePath, model, text.getBytes(StandardCharsets.UTF_8)).substring(1)
    }

    /** Where the resolver looks for the page, which is where the generator must have written it. */
    File page(String templatePath, String domain, String text) {
        Map<String, Object> model = new ScaffoldedPagesGenerator().model(domain).asMap()
        new File(output, ScaffoldedPages.uri(templatePath, model, text.getBytes(StandardCharsets.UTF_8)).substring(1))
    }

    void 'every template is expanded for every domain class, where the resolver looks for it'() {
        when:
        int written = new ScaffoldedPagesGenerator().generate(['com.example.Book': [templates], 'com.example.Author': [templates]], output).written

        then:
        written == 4
        rendered(page('show', 'com.example.Book', 'show ${className}')) == 'show Book'
        rendered(page('admin/show', 'com.example.Book', 'admin show ${className}')) == 'admin show Book'
        rendered(page('show', 'com.example.Author', 'show ${className}')) == 'show Author'
        rendered(page('admin/show', 'com.example.Author', 'admin show ${className}')) == 'admin show Author'
    }

    void 'a domain class is expanded with only the templates planned for it'() {
        given:
        File plain = new File(dir, 'plain')
        new File(plain, 'show.gsp').with { parentFile.mkdirs(); setText('show ${className}', 'UTF-8') }

        when:
        int written = new ScaffoldedPagesGenerator().generate(['com.example.Book': [templates], 'com.example.Author': [plain]], output).written

        then:
        written == 3
        page('admin/show', 'com.example.Book', 'admin show ${className}').exists()
        page('show', 'com.example.Author', 'show ${className}').exists()
        !page('admin/show', 'com.example.Author', 'admin show ${className}').exists()
    }

    void 'a template that cannot be expanded is skipped and the others are still written'() {
        given:
        template('broken', 'broken ${noSuchName}')

        when:
        int written = new ScaffoldedPagesGenerator().generate(['com.example.Book': [templates]], output).written

        then:
        written == 2
        page('show', 'com.example.Book', 'show ${className}').exists()
        !new File(output, 'grails-scaffolded/com.example.Book').list().any { it.startsWith('broken') }
    }

    void 'every copy of a template is expanded, each to the page the resolver looks for when it chooses that copy'() {
        given: 'a second copy of the show template, as a plugin overriding it would carry'
        File other = new File(dir, 'other')
        new File(other, 'show.gsp').with { parentFile.mkdirs(); setText('other show ${className}', 'UTF-8') }

        when:
        int written = new ScaffoldedPagesGenerator().generate(['com.example.Book': [templates, other]], output).written

        then:
        written == 3
        rendered(page('show', 'com.example.Book', 'show ${className}')) == 'show Book'
        rendered(page('show', 'com.example.Book', 'other show ${className}')) == 'other show Book'
    }

    void 'a page is written in the encoding it is compiled with'() {
        given:
        template('show', 'show ${className} \u00e9')

        when:
        new ScaffoldedPagesGenerator().generate(['com.example.Book': [templates]], output, 'ISO-8859-1')

        then:
        new String(page('show', 'com.example.Book', 'show ${className} \u00e9').bytes, StandardCharsets.ISO_8859_1).startsWith('show Book \u00e9')
    }

    void 'a page ends with a comment naming the template it was expanded from, which renders as nothing'() {
        given:
        String origin = 'theme.jar!/META-INF/templates/scaffolding/show.gsp'

        when:
        new ScaffoldedPagesGenerator().generate(['com.example.Book': [templates]], output, 'UTF-8', [(templates): origin])

        then:
        page('show', 'com.example.Book', 'show ${className}').getText('UTF-8') ==
                "show Book%{-- expanded from ${origin} for com.example.Book --}%".toString()
    }

    void 'a template that cannot be expanded is returned with where it came from and why'() {
        given:
        File broken = new File(dir, 'broken')
        new File(broken, 'show.gsp').with { parentFile.mkdirs(); setText('broken ${noSuchName}', 'UTF-8') }

        when:
        ScaffoldedPagesGenerator.Result result = new ScaffoldedPagesGenerator()
                .generate(['com.example.Book': [broken]], output, 'UTF-8', [(broken): 'theme.jar!/show.gsp'])

        then: 'whether it fails the build is for the build to say, which knows whose template it is'
        result.written == 0
        result.failures.size() == 1
        with(result.failures[0]) {
            templatesDir == broken
            templatePath == 'show'
            origin == 'theme.jar!/show.gsp'
            domain == 'com.example.Book'
            reason.contains('noSuchName')
        }
    }

    void 'a domain class named by the build is modelled as the resolver models the class itself'() {
        expect: 'the build names a class as ASM reads it, nested classes by their binary name'
        new ScaffoldedPagesGenerator().model(type.name).asMap() == new ScaffoldedPagesGenerator().model(type).asMap()

        where:
        type << [ScaffoldedPagesGenerator, Map.Entry]
    }

    void 'the command line reads a plan of domain classes and their templates directories'() {
        given:
        File plain = new File(dir, 'plain')
        new File(plain, 'index.gsp').with { parentFile.mkdirs(); setText('index ${className}', 'UTF-8') }
        File plan = new File(dir, 'plan.txt')
        plan.setText("com.example.Book\t${templates.path}\t${plain.path}\n\ncom.example.Author\t${plain.path}\n", 'UTF-8')

        when:
        File origins = new File(dir, 'origins.txt')
        origins.setText("${plain.path}\tthe application's index.gsp\n", 'UTF-8')
        File broken = new File(dir, 'broken')
        new File(broken, 'show.gsp').with { parentFile.mkdirs(); setText('broken ${noSuchName}', 'UTF-8') }
        plan.append("com.example.Broken\t${broken.path}\n", 'UTF-8')
        File report = new File(dir, 'report.txt')
        ScaffoldedPagesGenerator.main(plan.path, origins.path, output.path, 'UTF-8', report.path)

        then:
        page('show', 'com.example.Book', 'show ${className}').exists()
        page('index', 'com.example.Book', 'index ${className}').exists()
        page('index', 'com.example.Author', 'index ${className}').exists()
        !page('show', 'com.example.Author', 'show ${className}').exists()
        page('index', 'com.example.Author', 'index ${className}').getText('UTF-8').endsWith(
                "%{-- expanded from the application's index.gsp for com.example.Author --}%")

        and: 'each page written is reported with the templates it came from, and each template that could not be expanded'
        List<List<String>> lines = report.readLines('UTF-8')*.split('\t')*.toList()
        lines.findAll { it[0] == 'page' }.collect { it.take(3) } as Set == [
                ['page', templates.path, pagePath('show', 'com.example.Book', 'show ${className}')],
                ['page', templates.path, pagePath('admin/show', 'com.example.Book', 'admin show ${className}')],
                ['page', plain.path, pagePath('index', 'com.example.Book', 'index ${className}')],
                ['page', plain.path, pagePath('index', 'com.example.Author', 'index ${className}')]] as Set
        lines.findAll { it[0] == 'failed' }.collect { it.take(3) } == [['failed', broken.path, 'com.example.Broken']]
    }
}
