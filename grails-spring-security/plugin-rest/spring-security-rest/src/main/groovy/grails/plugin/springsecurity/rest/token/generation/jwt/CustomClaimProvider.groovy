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

import com.nimbusds.jwt.JWTClaimsSet
import org.springframework.security.core.userdetails.UserDetails

/**
 * Interface providing a hook to have a chance to add additional claims inside the JWT
 */
interface CustomClaimProvider {

    /**
     * The method will be called when the JWT is built. Use the builder to include additional claims, if needed.
     *
     * @param builder the claim builder used to add additional claims
     * @param details the {@link UserDetails} representing the authenticated user
     * @param principal the principal, usually the username
     * @param expiration the expiration time in seconds for which the JWT is configured to
     */
    void provideCustomClaims(JWTClaimsSet.Builder builder, UserDetails details, String principal, Integer expiration)

}