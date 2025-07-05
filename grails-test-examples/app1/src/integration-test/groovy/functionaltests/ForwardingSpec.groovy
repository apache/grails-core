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

package functionaltests

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.PendingFeature

/**
 */
@Integration(applicationClass = Application)
@Rollback
class ForwardingSpec extends ContainerGebSpec {
    void 'params extracted from the URL path by a custom UrlMapping are forwarded'() {
        when: 'a url mapping such as /forward/$param1 is matched'
        go('/forward/test')
        then: 'param1 is passed to the forwarded action'
        $().text() == 'Forward Destination. Params: test'
    }

    void 'verifies params from the original url are passed to the forwarded action but not duplicated'() {
        when:
        go('/forwarding/two?param1=test')
        then:
        $().text() == 'Forward Destination. Params: test'
    }

    void 'Test forward to same controller'() {
        when: 'A forward is issued to an action in the same controller'
        go('/forwarding/one')

        then: 'The forward works correctly'
        $().text() == 'Forward Destination. Params:'
    }

    void 'Test forward to named controller'() {
        when: 'A forward is issued to an action in the same controller'
        go('/forwarding/two')

        then: 'The forward works correctly'
        $().text() == 'Forward Destination. Params:'
    }

    void 'Test forward with parameters'() {
        when: 'A forward is issued to an action with parameters'
        go('/forwarding/three')

        then: 'The forward works correctly'
        $().text() == 'Forward Destination. Params: test'
    }

    void 'Test forwarding to an action which returns a Map'() {
        when:
        go('/forwarding/forwardToList')

        then:
        $('li', text: 'Jeff')
        $('li', text: 'Zack')
        $('li', text: 'Jake')
        $('li', text: 'Betsy')
    }

    void 'Test forward after populating flash'() {
        when: 'an acton populates flash and then forwards'
        go('/forwarding/putMessageInFlash')

        then: 'the flash data is available in the action that was forwarded to'
        $('div', id: 'message').text() == 'flash.message is [some message]'

        when: 'a subsequent request is initiated'
        go('/forwarding/displayFlash')

        then: 'the flash data is still available'
        $('div', id: 'message').text() == 'flash.message is [some message]'

        when: 'any further request is initiated'
        go('/forwarding/displayFlash')

        then: 'the flash message has been cleared'
        $('div', id: 'message').text() == 'flash.message is []'
    }

    @PendingFeature
    void 'forwarding to a view'() {
        when: 'A forward is issued to a view'
        go('/forwarding/forwardWithRender')

        then: 'The view is rendered correctly'
        $('p', id: 'message').text() == 'Hello from a forwarded view'
    }
}
