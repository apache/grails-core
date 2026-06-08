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
package grails.plugin.springsecurity.rest.token

import grails.plugin.springsecurity.rest.token.generation.SecureRandomTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.UUIDTokenGenerator
import org.apache.commons.lang3.StringUtils
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import spock.lang.Specification
import spock.lang.Unroll

class TokenGeneratorSpec extends Specification {

    @Unroll
    void "#generator.class.name generates tokens with 32 characters"() {

        given:
        def tokens = []
        def userDetails = new User('foo', 'bar', [new SimpleGrantedAuthority('USER')])

        when:
        def startTime = System.nanoTime()
        10000.times {
            tokens << generator.generateAccessToken(userDetails)
        }
        def endTime = System.nanoTime()

        println "Time elapsed: ${(endTime - startTime)}"

        then:
        tokens.every { AccessToken token ->
            token.accessToken.size() == 32 && StringUtils.isAlphanumeric(token.accessToken)
        }

        where:
        generator << [new SecureRandomTokenGenerator(), new UUIDTokenGenerator()]
    }
}
