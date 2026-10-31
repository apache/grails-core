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
package test.app

import grails.testing.mixin.integration.Integration
import org.springframework.test.context.TestPropertySource
import org.springframework.web.filter.CharacterEncodingFilter
import spock.lang.Issue
import spock.lang.Specification

/**
 * {@code grails.config.locations} files are loaded after Spring Boot's environment post-processors run, and the
 * deprecated {@code grails.filter.*} encoding properties in them still configure the encoding filter.
 */
@Integration
@TestPropertySource(properties = 'grails.config.locations=classpath:legacyEncodingConfig.yml')
@Issue('https://github.com/apache/grails-core/issues/15921')
class LegacyEncodingConfigSpec extends Specification {

    CharacterEncodingFilter characterEncodingFilter

    void 'the encoding filter takes the deprecated Grails properties from an external config file'() {
        expect:
        characterEncodingFilter.encoding == 'ISO-8859-1'
        !characterEncodingFilter.forceRequestEncoding
        !characterEncodingFilter.forceResponseEncoding
    }
}
