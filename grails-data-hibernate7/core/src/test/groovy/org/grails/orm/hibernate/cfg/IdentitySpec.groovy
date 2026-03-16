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

package org.grails.orm.hibernate.cfg

import spock.lang.Specification
import spock.lang.Unroll
import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum

class IdentitySpec extends Specification {

    @Unroll
    def "test determineGeneratorName with generator=#generatorName and useSequence=#useSequence"() {
        given:
        Identity identity = new Identity(generator: generatorName)

        expect:
        identity.determineGeneratorName(useSequence) == expected

        where:
        generatorName | useSequence | expected
        'native'      | false       | 'native'
        'native'      | true        | 'sequence-identity'
        'identity'    | false       | 'identity'
        'identity'    | true        | 'identity'
        'sequence'    | true        | 'sequence'
        'increment'   | false       | 'increment'
        null          | false       | 'native'
        null          | true        | 'sequence-identity'
    }

    @Unroll
    def "test static determineGeneratorName with mappedId=#mappedIdPresent and useSequence=#useSequence"() {
        given:
        Identity identity = mappedIdPresent ? new Identity(generator: generatorName) : null

        expect:
        Identity.determineGeneratorName(identity, useSequence) == expected

        where:
        mappedIdPresent | generatorName | useSequence | expected
        true            | 'native'      | false       | 'native'
        true            | 'native'      | true        | 'sequence-identity'
        true            | 'uuid'        | false       | 'uuid'
        false           | null          | false       | 'native'
        false           | null          | true        | 'sequence-identity'
    }
}
