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

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.Specification

import org.springframework.security.core.userdetails.User

import grails.plugins.redis.RedisService

class RedisTokenStorageServiceSpec extends Specification {

    private Map<String, byte[]> stored = [:]
    private RedisTokenStorageService service

    def setup() {
        Jedis jedis = Mock(Jedis) {
            set(_ as byte[], _ as byte[]) >> { byte[] key, byte[] value ->
                stored[new String(key, 'UTF-8')] = value
                'OK'
            }
            get(_ as byte[]) >> { byte[] key -> stored[new String(key, 'UTF-8')] }
            del(_ as String) >> { String key -> stored.remove(key) == null ? 0L : 1L }
        }
        JedisPool pool = Stub(JedisPool) {
            getResource() >> jedis
        }
        service = new RedisTokenStorageService(redisService: new RedisService(redisPool: pool))
    }

    void "load the principal of a stored token"() {
        given:
        def principal = new User('foo', 'bar', [])
        service.storeToken('12345', principal)

        expect:
        service.loadUserByToken('12345') == principal
    }

    void "a removed token is no longer found"() {
        given:
        service.storeToken('12345', new User('foo', 'bar', []))

        when:
        service.removeToken('12345')
        service.loadUserByToken('12345')

        then:
        thrown(TokenNotFoundException)
    }

    void "throw token not found exception if there is no principal to remove for a given token"() {
        when:
        service.removeToken('token-does-not-exist')

        then:
        thrown(TokenNotFoundException)
    }

    void "throw token not found exception if the principal of a given token was removed before"() {
        given:
        service.storeToken('12345', new User('foo', 'bar', []))
        service.removeToken('12345')

        when:
        service.removeToken('12345')

        then:
        thrown(TokenNotFoundException)
    }
}
