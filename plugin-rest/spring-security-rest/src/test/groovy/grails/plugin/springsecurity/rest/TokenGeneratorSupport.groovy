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

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.MACSigner
import grails.plugin.springsecurity.rest.token.generation.jwt.AbstractJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.IssuerClaimProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import grails.spring.BeanBuilder

trait TokenGeneratorSupport {

    SignedJwtTokenGenerator setupSignedJwtTokenGenerator() {
        String secret = 'foobar'*10
        def jwtTokenStorageService = new JwtTokenStorageService(jwtService: new JwtService(jwtSecret: secret))
        return new SignedJwtTokenGenerator(defaultExpiration: 3600, jwtSecret: secret, signer: new MACSigner(secret), jwtTokenStorageService: jwtTokenStorageService, customClaimProviders: [], jwsAlgorithm: JWSAlgorithm.HS256)
    }

    EncryptedJwtTokenGenerator setupEncryptedJwtTokenGenerator() {
        RSAKeyProvider keyProvider = new DefaultRSAKeyProvider()
        def jwtTokenStorageService = new JwtTokenStorageService(jwtService: new JwtService(keyProvider: keyProvider))
        return new EncryptedJwtTokenGenerator(defaultExpiration: 3600, jwtTokenStorageService: jwtTokenStorageService, keyProvider: keyProvider, customClaimProviders: [], jweAlgorithm: JWEAlgorithm.RSA_OAEP, encryptionMethod: EncryptionMethod.A128GCM)
    }

}