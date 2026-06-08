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
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.InitializingBean

import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Loads RSA public/private key's from files
 */
@Slf4j
@CompileStatic
class FileRSAKeyProvider implements RSAKeyProvider, InitializingBean {

    /** Full path to the public key so that {@code new File(publicKeyPath).exists() == true} */
    String publicKeyPath

    /** Full path to the private key so that {@code new File(publicKeyPath).exists() == true} */
    String privateKeyPath

    RSAPublicKey publicKey
    RSAPrivateKey privateKey

    @Override
    void afterPropertiesSet() throws Exception {
        log.debug "Loading public/private key from DER files"
        KeyFactory kf = KeyFactory.getInstance("RSA")

        def key = new File(publicKeyPath)
        log.debug "Public key path: ${key.absolutePath}"
        def keyBytes = key.bytes
        def spec = new X509EncodedKeySpec(keyBytes)
        publicKey = kf.generatePublic(spec) as RSAPublicKey

        key = new File(privateKeyPath)
        log.debug "Private key path: ${key.absolutePath}"
        keyBytes = key.bytes
        spec = new PKCS8EncodedKeySpec(keyBytes)
        privateKey = kf.generatePrivate(spec) as RSAPrivateKey
    }
}
