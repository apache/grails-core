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
package grails.plugin.scaffolding

import grails.codegen.model.ModelBuilder
import grails.core.gsp.GrailsTagLibClass
import groovy.text.GStringTemplateEngine
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.taglib.TagLibraryLookup
import spock.lang.Specification

class ScaffoldedIndexViewModelSpec extends Specification implements ModelBuilder {

    void "the scaffolded index view holds the count either scaffolding path supplies"() {
        given: 'the index view expanded from its template, the way dynamic scaffolding expands it'
        String expanded = new GStringTemplateEngine()
                .createTemplate(new File('src/main/templates/scaffolding/index.gsp'))
                .make(model(ScaffoldedIndexBook).asMap())
                .toString()
        String modelDirective = expanded.readLines().find { it.startsWith('@{ model=') }

        and: 'a page carrying only that declaration, so rendering it needs no tag library'
        GroovyPagesTemplateEngine engine = new GroovyPagesTemplateEngine()
        engine.afterPropertiesSet()
        engine.tagLibraryLookup = new TagLibraryLookup() {
            @Override
            protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
                tags.put(name, taglib.newInstance())
            }
        }
        def page = engine.createTemplate(modelDirective + '${scaffoldedIndexBookCount}', "scaffoldedIndex${supplied}")

        when:
        StringWriter out = new StringWriter()
        page.make([scaffoldedIndexBookList: [], scaffoldedIndexBookCount: supplied]).writeTo(new PrintWriter(out, true))

        then:
        modelDirective
        out.toString() == supplied.toString()

        where: 'RestfulController supplies an Integer, a generated service a Long, and a Long count can pass Integer.MAX_VALUE'
        supplied << [3, 3L, 3_000_000_000L]
    }
}

class ScaffoldedIndexBook {
}
