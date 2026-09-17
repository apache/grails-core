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

package org.grails.forge.options

import spock.lang.Specification
import spock.lang.Unroll

class GormImplSpec extends Specification {

    @Unroll
    void "parse resolves #value to #expected"() {
        expect:
        GormImpl.parse(value) == expected

        where:
        value        | expected
        'hibernate'  | GormImpl.HIBERNATE7
        'HIBERNATE'  | GormImpl.HIBERNATE7
        'hibernate7' | GormImpl.HIBERNATE7
        'HIBERNATE7' | GormImpl.HIBERNATE7
        'mongodb'    | GormImpl.MONGODB
        'neo4j'      | GormImpl.NEO4J
        null         | null
    }

    void "hibernate5 is no longer a resolvable value"() {
        expect: 'Hibernate 5 support has been removed, so this value is now unresolvable'
        GormImpl.parse('hibernate5') == null
    }

    void "the default option is Hibernate 7"() {
        expect:
        GormImpl.DEFAULT_OPTION == GormImpl.HIBERNATE7
    }
}
