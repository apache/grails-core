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

package org.grails.forge.template

import spock.lang.Specification

class YamlTemplateSpec extends Specification {

    void "test yaml output"() {
        Map<String, Object> config = [:]
        config.put("info.app.name", "foo")
        config.put("grails.codegen.defaultPackage", "example")
        config.put("grails.events.spring", true)
        config.put("datasources.default.url", "dbURL")
        config.put("datasources.default.className", "h2")
        config.put("jpa.default.properties.hibernate.hbm2ddl", "auto")

        YamlTemplate template = new YamlTemplate("abc", config)
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        template.write(baos)

        expect:
        //single value nested keys get collapsed
        //micronaut. ignores that rule
        baos.toString() == """info.app.name: foo
grails:
  codegen:
    defaultPackage: example
  events:
    spring: true
datasources:
  default:
    url: dbURL
    className: h2
jpa.default.properties.hibernate.hbm2ddl: auto
"""
    }

    void "test empty yaml output"() {
        Map<String, Object> config = [:]

        YamlTemplate template = new YamlTemplate("abc", config)
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        template.write(baos)

        expect:
        baos.toString() == "# Place application configuration here"
    }
}
