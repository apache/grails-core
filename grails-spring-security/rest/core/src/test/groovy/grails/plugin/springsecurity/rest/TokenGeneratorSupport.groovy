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

import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

import org.springframework.security.core.userdetails.UserDetails

import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService

trait TokenGeneratorSupport {

    SignedJwtTokenGenerator setupSignedJwtTokenGenerator() {
        String secret = 'foobar' * 10
        def jwtTokenStorageService = new JwtTokenStorageService(jwtService: new JwtService(jwtSecret: secret))
        return new SignedJwtTokenGenerator(defaultExpiration: 3600, jwtSecret: secret, signer: new MACSigner(secret), jwtTokenStorageService: jwtTokenStorageService, customClaimProviders: [], jwsAlgorithm: JWSAlgorithm.HS256)
    }

    EncryptedJwtTokenGenerator setupEncryptedJwtTokenGenerator(RSAKeyProvider keyProvider = new DefaultRSAKeyProvider()) {
        def jwtTokenStorageService = new JwtTokenStorageService(jwtService: new JwtService(keyProvider: keyProvider))
        return new EncryptedJwtTokenGenerator(defaultExpiration: 3600, jwtTokenStorageService: jwtTokenStorageService, keyProvider: keyProvider, customClaimProviders: [], jweAlgorithm: JWEAlgorithm.RSA_OAEP, encryptionMethod: EncryptionMethod.A128GCM)
    }

    /**
     * The claims of an access token for the user, as the plugin generates them.
     */
    JWTClaimsSet accessTokenClaims(UserDetails user) {
        new JWTClaimsSet.Builder()
                .subject(user.username)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim('roles', user.authorities*.authority)
                .claim('principal', JwtService.serialize(user))
                .build()
    }

    /**
     * Encrypts the claims with the public key, without signing them.
     */
    String encryptClaims(JWTClaimsSet claimsSet, RSAPublicKey publicKey) {
        new EncryptedJWT(new JWEHeader(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A128GCM), claimsSet).tap {
            encrypt(new RSAEncrypter(publicKey))
        }.serialize()
    }

    /**
     * Encrypts the JWT with the public key, as the payload of an encrypted JWT, using the given algorithm for the
     * content encryption key.
     */
    String encryptJwt(JWT jwt, RSAPublicKey publicKey, JWEAlgorithm algorithm = JWEAlgorithm.RSA_OAEP) {
        final header = new JWEHeader.Builder(algorithm, EncryptionMethod.A128GCM).contentType('JWT').build()
        new JWEObject(header, new Payload(jwt.serialize())).tap {
            encrypt(new RSAEncrypter(publicKey))
        }.serialize()
    }

    SignedJWT signWithRsa(JWTClaimsSet claimsSet, PrivateKey privateKey) {
        new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet).tap {
            sign(new RSASSASigner(privateKey))
        }
    }

    SignedJWT signWithHmac(JWTClaimsSet claimsSet, byte[] secret) {
        new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet).tap {
            sign(new MACSigner(secret))
        }
    }

}
