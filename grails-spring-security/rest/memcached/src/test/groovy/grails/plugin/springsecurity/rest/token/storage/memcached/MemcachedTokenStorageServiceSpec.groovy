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
package grails.plugin.springsecurity.rest.token.storage.memcached

import net.spy.memcached.CASValue
import net.spy.memcached.MemcachedClient
import net.spy.memcached.internal.OperationFuture
import spock.lang.Specification
import spock.lang.Unroll

import org.springframework.security.core.userdetails.User

import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException

class MemcachedTokenStorageServiceSpec extends Specification {

    private MemcachedClient memcachedClient = Mock(MemcachedClient)
    private MemcachedTokenStorageService service = new MemcachedTokenStorageService(memcachedClient: memcachedClient)
    private OperationFuture<Boolean> deleted = Stub(OperationFuture) { get() >> true }
    private OperationFuture<Boolean> notDeleted = Stub(OperationFuture) { get() >> false }

    @Unroll
    void "a token that is #description is not found, and memcached is not asked for it"() {
        when:
        service.loadUserByToken(token)

        then:
        thrown(TokenNotFoundException)
        0 * memcachedClient._

        when:
        service.removeToken(token)

        then:
        thrown(TokenNotFoundException)
        0 * memcachedClient._

        where:
        description                                 | token
        'empty'                                     | ''
        'longer than 250 bytes'                     | 'a' * 251
        'longer than 250 bytes in fewer characters' | '\u00e9' * 126
    }

    @Unroll
    void "a stored token that is #description is found and removed"() {
        given:
        def principal = new User('foo', 'bar', [])

        when:
        def loaded = service.loadUserByToken(token)

        then:
        1 * memcachedClient.getAndTouch(token, 3600) >> new CASValue<Object>(1L, principal)
        loaded == principal

        when:
        service.removeToken(token)

        then:
        1 * memcachedClient.delete(token) >> deleted
        0 * memcachedClient.getAndTouch(*_)

        where:
        // the plugin configures the client for the binary protocol, whose keys can hold a space or a line break
        description                  | token
        'made of letters and digits' | 'abc123'
        '250 bytes long'             | 'a' * 250
        'holding a space'            | 'a b'
        'holding a line break'       | 'a\nb'
    }

    void "a token that memcached does not have is not found"() {
        when:
        service.loadUserByToken('12345')

        then:
        1 * memcachedClient.getAndTouch('12345', 3600) >> null
        thrown(TokenNotFoundException)

        when:
        service.removeToken('12345')

        then:
        1 * memcachedClient.delete('12345') >> notDeleted
        thrown(TokenNotFoundException)
    }

    void "throw token not found exception if another removal deleted the token first"() {
        given: 'memcached holds the token when it is looked up, but no longer when it is deleted'
        memcachedClient.getAndTouch('12345', 3600) >> new CASValue<Object>(1L, new User('foo', 'bar', []))

        when:
        service.removeToken('12345')

        then:
        1 * memcachedClient.delete('12345') >> notDeleted
        thrown(TokenNotFoundException)
    }
}
