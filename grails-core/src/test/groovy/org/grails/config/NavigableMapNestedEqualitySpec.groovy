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
package org.grails.config

import org.grails.config.yaml.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import spock.lang.Specification

/**
 * Created by graemerocher on 29/08/2016.
 */
class NavigableMapNestedEqualitySpec extends Specification {

    void "Test nested equality"() {
        given:
        Resource resource1 = new ByteArrayResource('''
---
grails:
    profile: web-plugin
    codegen:
        defaultPackage: grails.plugins.export
info:
    app:
        name: 'export\'
        version: '2.0.0\'
        grailsVersion: '3.0.3.BUILD-SNAPSHOT\'
spring:
    groovy:
        template:
            check-template-location: false

---
grails:
    mime:
        disable:
            accept:
                header:
                    userAgents:
                        - Gecko
                        - WebKit
                        - Presto
                        - Trident
        types:
            all: '*/*\'
            atom: application/atom+xml
            css: text/css
            csv: text/csv
            form: application/x-www-form-urlencoded
            html:
              - text/html
              - application/xhtml+xml
            js: text/javascript
            json:
              - application/json
              - text/json
            multipartForm: multipart/form-data
            rss: application/rss+xml
            text: text/plain
            hal:
              - application/hal+json
              - application/hal+xml
            xml:
              - text/xml
              - application/xml
    urlmapping:
        cache:
            maxsize: 1000
    controllers:
        defaultScope: singleton
    converters:
        encoding: UTF-8
    hibernate:
        cache:
            queries: false
    views:
        default:
            codec: html
        gsp:
            encoding: UTF-8
            htmlcodec: xml
            codecs:
                expression: html
                scriptlets: html
                taglib: none
                staticparts: none

---
exporters:
    excelExporter: grails.plugins.export.exporter.DefaultExcelExporter
    csvExporter: grails.plugins.export.exporter.DefaultCSVExporter
    xmlExporter: grails.plugins.export.exporter.DefaultXMLExporter
    pdfExporter: grails.plugins.export.exporter.DefaultPDFExporter
    odsExporter: grails.plugins.export.exporter.DefaultODSExporter
    rtfExporter: grails.plugins.export.exporter.DefaultRTFExporter

---
dataSource:
    pooled: true
    jmxExport: true
    driverClassName: org.h2.Driver
    username: sa
    password:

environments:
    development:
        dataSource:
            dbCreate: create-drop
            url: jdbc:h2:mem:devDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE
    test:
        dataSource:
            dbCreate: update
            url: jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE
    production:
        dataSource:
            dbCreate: update
            url: jdbc:h2:prodDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE
            properties:
                jmxEnabled: true
                initialSize: 5
                maxActive: 50
                minIdle: 5
                maxIdle: 25
                maxWait: 10000
                maxAge: 600000
                timeBetweenEvictionRunsMillis: 5000
                minEvictableIdleTimeMillis: 60000
                validationQuery: SELECT 1
                validationQueryTimeout: 3
                validationInterval: 15000
                testOnBorrow: true
                testWhileIdle: true
                testOnReturn: false
                jdbcInterceptors: ConnectionState
                defaultTransactionIsolation: 2 # TRANSACTION_READ_COMMITTED

'''.bytes, "test.yml")

        Resource resource2 = new ByteArrayResource('''
grails:
    mime:
        types:
            form: application/x-www-form-urlencoded
'''.bytes, "test.yml")

        def propertySourceLoader = new YamlPropertySourceLoader()
        def yamlPropertiesSource1 = propertySourceLoader.load('foo-plugin-environments.yml', resource1, Arrays.asList("dataSource", "hibernate"))
        def yamlPropertiesSource2 = propertySourceLoader.load('bar-plugin-environments.yml', resource2, Arrays.asList("dataSource", "hibernate"))
        def propertySources = new MutablePropertySources()
        propertySources.addFirst(yamlPropertiesSource1.first())
        propertySources.addFirst(yamlPropertiesSource2.first())
        def config = new PropertySourcesConfig(propertySources)

        expect:
        config.getProperty('grails.mime.types', Object) == ( config.grails.mime.types )
        config.getProperty('grails.mime.types', Object).is( config.grails.mime.types )

    }
}
