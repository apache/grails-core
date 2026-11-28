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
    className: h2
    url: dbURL
jpa.default.properties.hibernate.hbm2ddl: auto
"""
    }

    void "the keys are written in an order that does not depend on the order they were given in"() {
        given:
        Map<String, Object> config = [:]
        config.put("quartz.autoStartup", true)
        config.put("grails.views.gsp.encoding", "UTF-8")
        config.put("environments", [production: [dataSource: [url: "p"]], development: [dataSource: [url: "d", dbCreate: "create-drop"]]])
        config.put("grails.cache.enabled", true)
        config.put("dataSource", [username: "sa", driverClassName: "org.h2.Driver"])
        config.put("info.app.name", "foo")

        when:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        new YamlTemplate("abc", config).write(baos)

        then:
        baos.toString() == """info.app.name: foo
dataSource:
  driverClassName: org.h2.Driver
  username: sa
environments:
  development:
    dataSource:
      dbCreate: create-drop
      url: d
  production:
    dataSource:
      url: p
grails:
  cache:
    enabled: true
  views:
    gsp:
      encoding: UTF-8
quartz.autoStartup: true
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

    void "what is written is UTF-8, whatever the default charset of the JVM that generates is"() {
        given:
        ByteArrayOutputStream baos = new ByteArrayOutputStream()

        when: 'a value that is not ASCII, as the name of an application can have one'
        new YamlTemplate("abc", ['info.app.name': 'caf\u00e9 \u2603'] as Map<String, Object>).write(baos)

        then: 'the bytes are those of UTF-8, also on a JVM of Java 17 on Windows, whose default charset is another'
        new String(baos.toByteArray(), 'UTF-8').contains('caf\u00e9 \u2603') || new String(baos.toByteArray(), 'UTF-8').contains('caf\\xE9 \\u2603')
        !new String(baos.toByteArray(), 'ISO-8859-1').contains('caf\u00e9')
    }
}
