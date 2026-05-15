/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.grails.testing.cleanup.postgresql

import spock.lang.Specification

class PostgresDatabaseCleanupHelperSpec extends Specification {

    def 'extractCurrentSchemaFromUrl returns null when URL has no query string'() {
        given:
        String url = 'jdbc:postgresql://localhost/testdb'

        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(url) == null
    }

    def 'extractCurrentSchemaFromUrl extracts schema from currentSchema parameter'() {
        given:
        String url = 'jdbc:postgresql://localhost/testdb?currentSchema=myschema'

        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(url) == 'myschema'
    }

    def 'extractCurrentSchemaFromUrl extracts schema with other parameters'() {
        given:
        String url = 'jdbc:postgresql://localhost/testdb?user=postgres&currentSchema=testschema&password=secret'

        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(url) == 'testschema'
    }

    def 'extractCurrentSchemaFromUrl returns null for empty schema'() {
        given:
        String url = 'jdbc:postgresql://localhost/testdb?currentSchema='

        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(url) == null
    }

    def 'extractCurrentSchemaFromUrl returns null for null input'() {
        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(null) == null
    }

    def 'extractCurrentSchemaFromUrl returns null for empty input'() {
        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl('') == null
    }

    def 'extractCurrentSchemaFromUrl returns null when currentSchema is not present'() {
        given:
        String url = 'jdbc:postgresql://localhost/testdb?user=postgres&password=secret'

        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(url) == null
    }

    def 'extractCurrentSchemaFromUrl handles multiple query parameters correctly'() {
        given:
        String url = 'jdbc:postgresql://localhost:5432/testdb?sslmode=require&currentSchema=custom&application_name=app'

        expect:
        PostgresDatabaseCleanupHelper.extractCurrentSchemaFromUrl(url) == 'custom'
    }
}
