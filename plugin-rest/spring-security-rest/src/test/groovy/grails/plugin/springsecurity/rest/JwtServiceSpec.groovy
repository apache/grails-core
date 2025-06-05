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
package grails.plugin.springsecurity.rest

import com.nimbusds.jose.JOSEException
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.testing.services.ServiceUnitTest
import groovyx.gbench.BenchmarkBuilder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import spock.lang.Ignore
import spock.lang.Specification

class JwtServiceSpec extends Specification implements TokenGeneratorSupport, ServiceUnitTest<JwtService>  {

    void "it can serialize and deserialize compressed objects"() {
        given:
        UserDetails userDetails = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])

        when:
        String serialized = JwtService.serialize(userDetails)
        UserDetails deserialized = JwtService.deserialize(serialized)

        then:
        userDetails == deserialized
    }

    @Ignore
    void "performance test of JWT parsing"() {
        given:
        UserDetails userDetails = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])

        SignedJwtTokenGenerator signedJwtTokenGenerator = getTokenGenerator(false) as SignedJwtTokenGenerator
        AccessToken signedAccessToken = signedJwtTokenGenerator.generateAccessToken(userDetails)

        EncryptedJwtTokenGenerator encryptedJwtTokenGenerator = getTokenGenerator(true) as EncryptedJwtTokenGenerator
        AccessToken encryptedAccessToken = encryptedJwtTokenGenerator.generateAccessToken(userDetails)

        when:
        JWT jwt
        def bm = new BenchmarkBuilder().run(verbose: true) {
            'SignedJwt' {
                jwt = signedJwtTokenGenerator.jwtTokenStorageService.jwtService.parse(signedAccessToken.accessToken)
            }
            'EncryptedJwt' {
                jwt = encryptedJwtTokenGenerator.jwtTokenStorageService.jwtService.parse(encryptedAccessToken.accessToken)
            }
        }
        bm.prettyPrint()
        println "Done"

        then:
        jwt

    }

    void "denying unsigned JWT when not expected" (){
        given:
        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder().build())
        service.jwtSecret = "mysecret"

        when:
        service.parse(jwt.serialize())

        then:
        JOSEException exception = thrown()
        exception.message == 'Unsigned/unencrypted JWT not expected'
    }

    void "denying unencrypted JWT when not expected" (){
        given:
        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder().build())
        service.keyProvider = Mock(RSAKeyProvider)

        when:
        service.parse(jwt.serialize())

        then:
        JOSEException exception = thrown()
        exception.message == 'Unsigned/unencrypted JWT not expected'
    }

}
