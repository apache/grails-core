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
package org.grails.datastore.gorm.mongodb.springdata

import spock.lang.Specification

/**
 * Guards that the auto-configuration is registered at the exact resource location Spring Boot scans.
 * Spring Boot's {@code ImportCandidates} reads {@code META-INF/spring/<name>.imports}; a file placed
 * anywhere else (e.g. {@code META-INF/services/spring/...}) is silently never loaded, so the module
 * would not auto-configure in a real application. This test fails loudly if the file moves.
 */
class AutoConfigurationImportsSpec extends Specification {

    private static final String CANONICAL = '/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports'

    void "test the auto-configuration is registered at Spring Boot's canonical imports location"() {
        when: "the imports resource Spring Boot scans is looked up"
        URL url = getClass().getResource(CANONICAL)

        then: "it exists and registers the interop auto-configuration"
        url != null
        url.text.contains('org.grails.datastore.gorm.mongodb.springdata.SpringDataMongoGormAutoConfiguration')

        and: "it is not left at the non-standard location Spring Boot never reads"
        getClass().getResource('/META-INF/services/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports') == null
    }
}
