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

class ScaffoldedPagesSpec extends Specification {

    /** Mentions every name the runtime model binds. */
    static final String ALL_NAMES = '${className} ${fullName} ${propertyName} ${modelName} ${packageName} ' +
            '${packagePath} ${simpleName} ${lowerCaseName}'

    static byte[] bytes(String text) {
        text.getBytes(StandardCharsets.UTF_8)
    }

    static Map<String, Object> model(Map<String, Object> changes = [:]) {
        [className: 'Book', fullName: 'com.example.Book', propertyName: 'book', modelName: 'book',
         packageName: 'com.example', packagePath: 'com/example', simpleName: 'Book', lowerCaseName: 'book'] + changes
    }

    void 'a page is named for its template, under the directory of its domain class'() {
        expect:
        ScaffoldedPages.uri(templatePath, model(), bytes(ALL_NAMES)) ==~ expected

        where:
        templatePath | expected
        'show'       | /\/grails-scaffolded\/com\.example\.Book\/show-[0-9a-f]{16}\.gsp/
        'admin/show' | /\/grails-scaffolded\/com\.example\.Book\/admin\/show-[0-9a-f]{16}\.gsp/
    }

    void 'the same template and model always name the same page, whatever order the model is in'() {
        given:
        Map<String, Object> reversed = new LinkedHashMap<>()
        model().keySet().toList().reverse().each { reversed[it] = model()[it] }

        expect:
        ScaffoldedPages.uri('show', reversed, bytes(ALL_NAMES)) == ScaffoldedPages.uri('show', model(), bytes(ALL_NAMES))
    }

    void 'a different template names a different page'() {
        expect:
        ScaffoldedPages.uri('show', model(), bytes(ALL_NAMES + ' ')) != ScaffoldedPages.uri('show', model(), bytes(ALL_NAMES))
    }

    void 'a model differing in #name, which the template mentions, names a different page'() {
        expect:
        ScaffoldedPages.uri('show', model((name): 'x'), bytes(ALL_NAMES)) != ScaffoldedPages.uri('show', model(), bytes(ALL_NAMES))

        where:
        name << model().keySet().findAll { it != 'fullName' }
    }

    void 'a name the template does not mention cannot change the page, so it does not change its name'() {
        given: 'the separator the machine running the application uses, rather than the one that built it'
        byte[] template = bytes('show ${className}')

        expect:
        ScaffoldedPages.uri('show', model(packagePath: 'com\\example'), template) == ScaffoldedPages.uri('show', model(), template)
    }

    void 'where one entry ends and the next begins is part of the name'() {
        given:
        byte[] template = bytes('${a}${ab}')

        expect:
        ScaffoldedPages.uri('show', model(a: 'bc', ab: ''), template) != ScaffoldedPages.uri('show', model(a: 'b', ab: 'c'), template)
    }

    void 'a template is expanded with the model'() {
        expect:
        ScaffoldedPages.expand(bytes('list of ${propertyName} for ${className} in ${packageName}'), model()) ==
                'list of book for Book in com.example'
    }

    void 'a template is read as UTF-8 wherever it is expanded'() {
        expect:
        ScaffoldedPages.expand(bytes('Título ${className}'), model()) == 'Título Book'
    }
}
