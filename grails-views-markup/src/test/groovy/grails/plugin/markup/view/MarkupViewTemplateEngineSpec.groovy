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
package grails.plugin.markup.view

import grails.web.mapping.LinkGenerator
import spock.lang.Specification

/**
 * Created by graemerocher on 28/08/15.
 */
class MarkupViewTemplateEngineSpec extends Specification {

    void "test links in markup engine"() {
        given:"A template engine"
        def templateEngine = new MarkupViewTemplateEngine()
        def linkGenerator = Mock(LinkGenerator)
        linkGenerator.link(_) >> "http://localhost:8080/book/show/1"
        templateEngine.setLinkGenerator(linkGenerator)

        when:"A template that creates a link is rendered"
        def template = templateEngine.createTemplate('''
model {
    Iterable<Map> cars
}
xmlDeclaration()
cars {
   cars.each {
       car(make: it.make, model: it.model, href:this.g.link(controller:'car'))
   }
}
''')

        def writable = template.make(cars: [[make:"Audi", model:"A5"]])

        def sw = new StringWriter()
        writable.writeTo(sw)

        then:"The result is correct"
        sw.toString().replace('\r','') == '''<?xml version='1.0'?>
<cars>
    <car make='Audi' model='A5' href='http://localhost:8080/book/show/1'/>
</cars>'''
    }

    void "Test parse markup template"() {
        given:"A template engine"
        def templateEngine = new MarkupViewTemplateEngine()

        when:"A template is parsed"
        def template = templateEngine.createTemplate('''
model {
    Iterable<Map> cars
}
xmlDeclaration()
cars {
   cars.each {
       car(make: it.make, model: it.model)
   }
}
''')

        def writable = template.make(cars: [[make:"Audi", model:"A5"]])

        then:"The writable is of the correct type"
        writable instanceof MarkupViewTemplate

        when:"The writable writes"

        def writer = new StringWriter()
        writable.writeTo(writer)

        then:"The output is correct"
        writer.toString().replace('\r','') == '''<?xml version='1.0'?>
<cars>
    <car make='Audi' model='A5'/>
</cars>'''
    }
}
