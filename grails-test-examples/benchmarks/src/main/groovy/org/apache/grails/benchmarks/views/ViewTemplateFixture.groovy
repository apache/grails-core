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
package org.apache.grails.benchmarks.views

import grails.plugin.json.view.JsonViewTemplateEngine
import grails.plugin.markup.view.MarkupViewTemplateEngine
import groovy.text.Template

class ViewTemplateFixture {

    static Template createJsonTemplate() {
        new JsonViewTemplateEngine().createTemplate('''
model {
    String name
    Integer count
}
json {
    name name
    count count
}
''')
    }

    // A model property named `model` fails to compile: the generated template extends
    // groovy.text.markup.BaseTemplate, whose getModel() returns Map, so `String model`
    // becomes an incompatible override.
    static Template createMarkupTemplate() {
        new MarkupViewTemplateEngine().createTemplate('''
model {
    String make
    String trim
}
car(make: make, trim: trim)
''')
    }
}
