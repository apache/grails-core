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
package org.apache.grails.datamapping.core.querytail

import spock.lang.Specification

/**
 * The dialects of each database are told apart in the Hibernate 5 and Hibernate 7 specs of the statement
 * inspector, with the dialects of each; this one covers what is no Hibernate dialect.
 */
class QueryTailDialectsSpec extends Specification {

    void "the statements of a session factory whose dialect is #described are redacted as for a database that is not known"() {
        expect:
        QueryTailDialects.syntaxOf(dialect) == QueryTailSyntax.UNKNOWN

        where:
        dialect | described
        null    | 'not known'
        String  | 'no Hibernate dialect'
        Object  | 'the root of every class'
    }
}
