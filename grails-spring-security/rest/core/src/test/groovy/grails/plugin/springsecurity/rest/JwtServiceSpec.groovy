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

import java.security.GeneralSecurityException

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSADecrypter
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.NestedJwt
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.testing.services.ServiceUnitTest

class JwtServiceSpec extends Specification implements TokenGeneratorSupport, ServiceUnitTest<JwtService> {

    @Shared
    RSAKeyProvider keyProvider = new DefaultRSAKeyProvider()

    @Shared
    RSAKeyProvider otherKeyProvider = new DefaultRSAKeyProvider()

    @Shared
    UserDetails user = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])

    @Shared
    UserDetails otherUser = new User('other', 'N/A', [new SimpleGrantedAuthority('ROLE_OTHER')])

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
        def timings = [:]

        timings.SignedJwt = timeMillis {
            jwt = signedJwtTokenGenerator.jwtTokenStorageService.jwtService
                    .parse(signedAccessToken.accessToken)
        }

        timings.EncryptedJwt = timeMillis {
            jwt = encryptedJwtTokenGenerator.jwtTokenStorageService.jwtService
                    .parse(encryptedAccessToken.accessToken)
        }

        println "SignedJwt: ${timings.SignedJwt} ms"
        println "EncryptedJwt: ${timings.EncryptedJwt} ms"
        println "Done"

        then:
        jwt

    }

    private static long timeMillis(Closure<?> block) {
        long start = System.nanoTime()
        block.call()
        return (System.nanoTime() - start) / 1_000_000
    }

    void "denying unsigned JWT when not expected"() {
        given:
        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder().build())
        service.jwtSecret = "mysecret"

        when:
        service.parse(jwt.serialize())

        then:
        JOSEException exception = thrown()
        exception.message == 'Unsigned/unencrypted JWT not expected'
    }

    void "denying unencrypted JWT when not expected"() {
        given:
        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder().build())
        service.keyProvider = Mock(RSAKeyProvider)

        when:
        service.parse(jwt.serialize())

        then:
        JOSEException exception = thrown()
        exception.message == 'Unsigned/unencrypted JWT not expected'
    }

    void "an encrypted JWT holds a JWT signed with the private key"() {
        given:
        final tokenGenerator = setupEncryptedJwtTokenGenerator(keyProvider)

        when:
        final accessToken = tokenGenerator.generateAccessToken(user)
        final encryptedJwt = EncryptedJWT.parse(accessToken.accessToken)
        encryptedJwt.decrypt(new RSADecrypter(keyProvider.privateKey))
        final signedJwt = encryptedJwt.payload.toSignedJWT()

        then:
        encryptedJwt.header.algorithm == JWEAlgorithm.RSA_OAEP
        encryptedJwt.header.encryptionMethod == EncryptionMethod.A128GCM
        encryptedJwt.header.contentType == 'JWT'

        and:
        signedJwt.header.algorithm == JWSAlgorithm.RS256
        signedJwt.verify(new RSASSAVerifier(keyProvider.publicKey))
        signedJwt.JWTClaimsSet.subject == 'username'
        signedJwt.JWTClaimsSet.getStringListClaim('roles') == ['ROLE_USER']
    }

    void "an encrypted JWT is parsed into the claims of the signed JWT inside it"() {
        given:
        service.keyProvider = keyProvider
        final accessToken = setupEncryptedJwtTokenGenerator(keyProvider).generateAccessToken(user)

        when:
        final jwt = service.parse(accessToken.accessToken)

        then:
        jwt instanceof NestedJwt
        jwt.JWTClaimsSet.subject == 'username'
        jwt.JWTClaimsSet.getStringListClaim('roles') == ['ROLE_USER']
        JwtService.deserialize(jwt.JWTClaimsSet.getStringClaim('principal')) == user

        and:
        jwt.header instanceof JWEHeader
        jwt.header.algorithm == JWEAlgorithm.RSA_OAEP
        jwt.parsedString == accessToken.accessToken
        jwt.serialize() == accessToken.accessToken
    }

    void "#description is rejected"() {
        given:
        service.keyProvider = keyProvider

        when:
        service.parse(token)

        then:
        final exception = thrown(JOSEException)
        exception.message == message

        where:
        description                                                | token                                                                                                                                       | message
        'an encrypted JWT whose claims are not signed'             | encryptClaims(accessTokenClaims(otherUser), keyProvider.publicKey)                                                                          | 'Encrypted JWT does not contain a signed JWT'
        'an encrypted JWT without a signature'                     | encryptJwt(new PlainJWT(accessTokenClaims(otherUser)), keyProvider.publicKey)                                                               | 'Encrypted JWT does not contain a signed JWT'
        'an encrypted JWT signed with another private key'         | encryptJwt(signWithRsa(accessTokenClaims(otherUser), otherKeyProvider.privateKey), keyProvider.publicKey)                                   | 'Invalid signature'
        'an encrypted JWT whose claims were changed after signing' | encryptJwt(changeClaims(signWithRsa(accessTokenClaims(user), keyProvider.privateKey), accessTokenClaims(otherUser)), keyProvider.publicKey) | 'Invalid signature'
        'an encrypted JWT signed with HMAC and the public key'     | encryptJwt(signWithHmac(accessTokenClaims(otherUser), keyProvider.publicKey.encoded), keyProvider.publicKey)                                | 'Unsupported JWS algorithm HS256, must be RS256, RS384, RS512, PS256, PS384 or PS512'
    }

    void "a JWT encrypted with another public key is rejected"() {
        given:
        service.keyProvider = keyProvider
        final token = encryptJwt(signWithRsa(accessTokenClaims(user), keyProvider.privateKey), otherKeyProvider.publicKey)

        when:
        service.parse(token)

        then: 'the private key does not decrypt the content encryption key'
        final exception = thrown(JOSEException)
        exception.cause instanceof GeneralSecurityException
    }

    void "an encrypted JWT that uses RSA1_5 is rejected when jweAlgorithm is #configured"() {
        given:
        service.keyProvider = keyProvider
        service.jweAlgorithm = configured
        final token = encryptJwt(signWithRsa(accessTokenClaims(user), keyProvider.privateKey), keyProvider.publicKey, JWEAlgorithm.RSA1_5)

        when:
        service.parse(token)

        then:
        final exception = thrown(JOSEException)
        exception.message == 'JWE algorithm RSA1_5 not expected'

        where:
        configured << [JWEAlgorithm.RSA_OAEP, JWEAlgorithm.RSA_OAEP_256]
    }

    void "an encrypted JWT that uses RSA1_5 is accepted when jweAlgorithm is #configured"() {
        given:
        service.keyProvider = keyProvider
        service.jweAlgorithm = configured
        final tokenGenerator = setupEncryptedJwtTokenGenerator(keyProvider).tap {
            jweAlgorithm = JWEAlgorithm.RSA1_5
        }
        final accessToken = tokenGenerator.generateAccessToken(user)

        when:
        final jwt = service.parse(accessToken.accessToken)

        then:
        jwt.header.algorithm == JWEAlgorithm.RSA1_5
        jwt.JWTClaimsSet.subject == 'username'
        jwt.JWTClaimsSet.getStringListClaim('roles') == ['ROLE_USER']

        where:
        configured << [JWEAlgorithm.RSA1_5, null]
    }

    void "a signed JWT is rejected when only encrypted JWTs are configured: #description"() {
        given:
        service.keyProvider = keyProvider

        when:
        service.parse(token)

        then:
        final exception = thrown(JOSEException)
        exception.message == 'Signed JWT not expected'

        where:
        description                               | token
        'HMAC'                                    | setupSignedJwtTokenGenerator().generateAccessToken(user).accessToken
        'RSA with the private key, not encrypted' | signWithRsa(accessTokenClaims(user), keyProvider.privateKey).serialize()
    }

    void "an encrypted JWT is rejected when only signed JWTs are configured"() {
        given:
        service.jwtSecret = 'foobar' * 10
        final token = setupEncryptedJwtTokenGenerator(keyProvider).generateAccessToken(user).accessToken

        when:
        service.parse(token)

        then:
        final exception = thrown(JOSEException)
        exception.message == 'Encrypted JWT not expected'
    }

    private static SignedJWT changeClaims(SignedJWT signedJwt, JWTClaimsSet claimsSet) {
        new SignedJWT(signedJwt.header.toBase64URL(), claimsSet.toPayload().toBase64URL(), signedJwt.signature)
    }

}
