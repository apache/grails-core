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

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

/**
 * Generates RSA-encrypted JWT's. The claims are signed with the private key of the {@link #keyProvider}, using RS256,
 * and the signed JWT is encrypted with its public key, which gives a {@link NestedJwt}.
 */
@CompileStatic
class EncryptedJwtTokenGenerator extends AbstractJwtTokenGenerator {

    RSAKeyProvider keyProvider

    JWEAlgorithm jweAlgorithm

    EncryptionMethod encryptionMethod

    @Override
    protected JWT generateAccessToken(JWTClaimsSet claimsSet) {
        // Sign the claims with the private RSA key
        final signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet).tap {
            sign(new RSASSASigner(keyProvider.privateKey))
        }

        // Encrypt the signed JWT with the public RSA key. The content type tells that the payload is a JWT.
        final header = new JWEHeader.Builder(jweAlgorithm, encryptionMethod).contentType('JWT').build()
        final encryptedJwt = new JWEObject(header, new Payload(signedJwt)).tap {
            encrypt(new RSAEncrypter(keyProvider.publicKey))
        }

        new NestedJwt(encryptedJwt, signedJwt)
    }

}
