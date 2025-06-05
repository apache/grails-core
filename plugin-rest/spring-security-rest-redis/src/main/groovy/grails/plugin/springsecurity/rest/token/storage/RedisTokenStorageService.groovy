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
package grails.plugin.springsecurity.rest.token.storage

import grails.plugins.redis.RedisService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.core.convert.converter.Converter
import org.springframework.core.serializer.support.SerializingConverter
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import redis.clients.jedis.Jedis

@Slf4j
@CompileStatic
class RedisTokenStorageService implements TokenStorageService {

    RedisService redisService
    UserDetailsService userDetailsService

    /** Expiration in seconds */
    Integer expiration = 3600

    private static final String PREFIX = "spring:security:token:"

    Converter<Object, byte[]> serializer = new SerializingConverter()

    @Override
    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Searching in Redis for UserDetails of token ${tokenValue}"

        byte[] userDetails
        redisService.withRedis { Jedis jedis ->
            String key = buildKey(tokenValue)
            userDetails = jedis.get(key.getBytes('UTF-8'))
            jedis.expire(key, expiration)
        }

        if (userDetails) {
            return deserialize(userDetails) as UserDetails
        } else {
            throw new TokenNotFoundException("Token ${tokenValue} not found")
        }

    }

    @Override
    void storeToken(String tokenValue, UserDetails principal) {
        log.debug "Storing principal for token: ${tokenValue} with expiration of ${expiration} seconds"
        log.debug "Principal: ${principal}"

        redisService.withRedis { Jedis jedis ->
            String key = buildKey(tokenValue)
            jedis.set(key.getBytes('UTF-8'), serialize(principal))
            jedis.expire(key, expiration)
        }
    }

    @Override
    void removeToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Removing token: ${tokenValue}"
        redisService.withRedis { Jedis jedis ->
            jedis.del(buildKey(tokenValue))
        }
    }

    private static String buildKey(String token){
        "$PREFIX$token"
    }

    private Object deserialize(byte[] bytes) {
        new ByteArrayInputStream(bytes).withObjectInputStream(getClass().classLoader) { is ->
            return is.readObject()
        }
    }

    private byte[] serialize(Object object) {
        if(object == null) {
            return new byte[0]
        } else {
            try {
                return serializer.convert(object)
            } catch (Exception var3) {
                throw new Exception("Cannot serialize", var3)
            }
        }
    }

}
