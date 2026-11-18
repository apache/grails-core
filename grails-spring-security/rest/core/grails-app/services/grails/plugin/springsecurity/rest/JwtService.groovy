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

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import groovy.util.logging.Slf4j

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jose.crypto.RSADecrypter
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT

import org.springframework.security.core.userdetails.UserDetails

import grails.plugin.springsecurity.rest.token.generation.jwt.NestedJwt
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import grails.util.Holders

/**
 * Helper to perform actions with JWT tokens
 */
@Slf4j
class JwtService {

    String jwtSecret
    RSAKeyProvider keyProvider

    /**
     * The algorithm that encrypted tokens are configured to encrypt their content encryption key with, the value of
     * {@code grails.plugin.springsecurity.rest.token.generation.jwt.jweAlgorithm}. When it is set,
     * {@link #parse(String)} accepts a token that uses {@link JWEAlgorithm#RSA1_5} only when this is {@code RSA1_5}.
     */
    JWEAlgorithm jweAlgorithm

    /**
     * Parses and verifies the given token. A signed token is accepted when a {@link #jwtSecret} is set and its HMAC
     * signature is valid. An encrypted token is accepted when a {@link #keyProvider} is set, the token decrypts with
     * its private key, and the payload is a JWT whose signature verifies with its public key, as
     * {@link grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator} generates it. When
     * {@link #jweAlgorithm} is set, an encrypted token that uses {@code RSA1_5} is accepted only when it is
     * {@code RSA1_5}.
     *
     * @param tokenValue a JWT token
     * @return a {@link JWT} object; for an encrypted token, a {@link NestedJwt} with the claims of the signed JWT inside
     * @throws JOSEException when verification/decryption fails, or the type of the token is not configured
     */
    JWT parse(String tokenValue) {
        JWT jwt = JWTParser.parse(tokenValue)

        if (jwt instanceof SignedJWT) {
            log.debug 'Parsed an HMAC signed JWT'
            if (!jwtSecret) {
                throw new JOSEException('Signed JWT not expected')
            }

            SignedJWT signedJwt = jwt as SignedJWT
            if (!signedJwt.verify(new MACVerifier(jwtSecret))) {
                throw new JOSEException('Invalid signature')
            }
        } else if (jwt instanceof EncryptedJWT) {
            log.debug 'Parsed an RSA encrypted JWT'
            if (!keyProvider) {
                throw new JOSEException('Encrypted JWT not expected')
            }

            final encryptedJwt = jwt as EncryptedJWT
            // Nimbus deprecates RSA1_5, so when jweAlgorithm is set, a token that uses RSA1_5 is accepted only when
            // the plugin issues such tokens
            if (jweAlgorithm && jweAlgorithm != JWEAlgorithm.RSA1_5 &&
                    encryptedJwt.header.algorithm == JWEAlgorithm.RSA1_5) {
                throw new JOSEException('JWE algorithm RSA1_5 not expected')
            }
            encryptedJwt.decrypt(new RSADecrypter(keyProvider.privateKey))

            final signedJwt = encryptedJwt.payload.toSignedJWT()
            if (!signedJwt) {
                throw new JOSEException('Encrypted JWT does not contain a signed JWT')
            }
            if (!signedJwt.verify(new RSASSAVerifier(keyProvider.publicKey))) {
                throw new JOSEException('Invalid signature')
            }

            return new NestedJwt(encryptedJwt, signedJwt)
        } else if (jwt instanceof PlainJWT) {
            log.debug 'Parsed a plain JWT'
            if (jwtSecret || keyProvider) {
                throw new JOSEException('Unsigned/unencrypted JWT not expected')
            }
        }

        return jwt
    }

    static String serialize(UserDetails userDetails) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos)
        ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut)
        objectOut.writeObject(userDetails)
        objectOut.close()
        byte[] outputBytes = baos.toByteArray()
        return outputBytes.encodeBase64()
    }

    static UserDetails deserialize(String userDetails) {
        byte[] inputBytes = userDetails.decodeBase64()
        ByteArrayInputStream bais = new ByteArrayInputStream(inputBytes)
        GZIPInputStream gzipIn = new GZIPInputStream(bais)
        ContextClassLoaderAwareObjectInputStream objectIn = new ContextClassLoaderAwareObjectInputStream(gzipIn)
        UserDetails userDetailsObject = objectIn.readObject() as UserDetails
        objectIn.close()
        return userDetailsObject
    }

}

@Slf4j
class ContextClassLoaderAwareObjectInputStream extends ObjectInputStream {

    ContextClassLoaderAwareObjectInputStream(InputStream is) throws IOException {
        super(is)
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        ClassLoader currentTccl = null
        try {
            currentTccl = Holders.grailsApplication.classLoader
            return currentTccl.loadClass(desc.name)
        } catch (Exception e) {
            log.debug e.message
        }

        return super.resolveClass(desc)
    }
}
