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
        'hibernate5' | GormImpl.HIBERNATE5
        'HIBERNATE5' | GormImpl.HIBERNATE5
        'hibernate'  | GormImpl.HIBERNATE5
        'HIBERNATE'  | GormImpl.HIBERNATE5
        'hibernate7' | GormImpl.HIBERNATE7
        'mongodb'    | GormImpl.MONGODB
        'neo4j'      | null
        null         | null
    }

    void "the default option is Hibernate 5"() {
        expect:
        GormImpl.DEFAULT_OPTION == GormImpl.HIBERNATE5
    }
}
