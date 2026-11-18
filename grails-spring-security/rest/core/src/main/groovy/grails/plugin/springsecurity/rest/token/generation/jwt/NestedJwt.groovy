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
package grails.plugin.springsecurity.rest.token.generation.jwt

import groovy.transform.CompileStatic

import com.nimbusds.jose.JWEObject
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.SignedJWT

/**
 * An encrypted JWT whose payload is a signed JWT, a nested JWT as RFC 7519 calls it. {@link EncryptedJwtTokenGenerator}
 * generates these, and {@link grails.plugin.springsecurity.rest.JwtService#parse(String)} returns one for an encrypted
 * token once it has decrypted the token and verified the signature of the JWT inside.
 *
 * <p>The header, the parsed parts and the serialized form are those of the encrypted JWT, and the claims are those of
 * the signed JWT.</p>
 */
@CompileStatic
class NestedJwt implements JWT {

    private static final long serialVersionUID = 1L

    @Delegate(includes = ['getHeader', 'getParsedParts', 'getParsedString', 'serialize'])
    private final JWEObject encryptedJwt

    @Delegate(includes = ['getJWTClaimsSet'])
    private final SignedJWT signedJwt

    /**
     * @param encryptedJwt the encrypted JWT, whose payload is {@code signedJwt}
     * @param signedJwt the signed JWT inside {@code encryptedJwt}, with a verified signature
     */
    NestedJwt(JWEObject encryptedJwt, SignedJWT signedJwt) {
        this.encryptedJwt = encryptedJwt
        this.signedJwt = signedJwt
    }
}
