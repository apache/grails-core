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

package org.grails.orm.hibernate.cfg.domainbinding

import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsEnumType

class GrailsEnumTypeSpec extends Specification {

    @Unroll
    def "should return correct type for #enumConstant"() {
        expect:
        enumConstant.getType() == expectedType

        where:
        enumConstant           | expectedType
        GrailsEnumType.DEFAULT | "default"
        GrailsEnumType.STRING   | "string"
        GrailsEnumType.ORDINAL  | "ordinal"
        GrailsEnumType.IDENTITY | "identity"
    }

    def "should have all expected enum constants"() {
        expect:
        GrailsEnumType.values().length == 4
        GrailsEnumType.valueOf("DEFAULT") == GrailsEnumType.DEFAULT
        GrailsEnumType.valueOf("STRING") == GrailsEnumType.STRING
        GrailsEnumType.valueOf("ORDINAL") == GrailsEnumType.ORDINAL
        GrailsEnumType.valueOf("IDENTITY") == GrailsEnumType.IDENTITY
    }

    @Unroll
    def "fromString should return #expectedType for #value"() {
        expect:
        GrailsEnumType.fromString(value) == expectedType

        where:
        value      | expectedType
        null       | GrailsEnumType.DEFAULT
        "default"  | GrailsEnumType.DEFAULT
        "DEFAULT"  | GrailsEnumType.DEFAULT
        "string"   | GrailsEnumType.STRING
        "ordinal"  | GrailsEnumType.ORDINAL
        "identity" | GrailsEnumType.IDENTITY
    }

    def "fromString should throw MappingException for invalid value"() {
        when:
        GrailsEnumType.fromString("invalid")

        then:
        def e = thrown(org.hibernate.MappingException)
        e.message.contains("Invalid enum type [invalid]")
    }
}
