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

package grails.plugin.springsecurity

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import grails.testing.web.taglib.TagLibUnitTest

/**
 * With {@code grails.plugin.springsecurity.active} set to {@code false} the plugin defines none of its beans, so
 * every {@code sec:} tag has to render nothing instead of failing on a missing bean.
 */
@Issue('https://github.com/apache/grails-core/issues/15880')
class SecurityTagLibInactiveSpec extends Specification implements TagLibUnitTest<SecurityTagLib> {

    static final String TAG_BODY = 'body'

    def cleanup() {
        config['grails.plugin.springsecurity.active'] = true
    }

    @Unroll
    void 'tag #tag renders nothing when the plugin is inactive'() {
        given: 'the plugin is switched off, so no security bean exists'
        config['grails.plugin.springsecurity.active'] = false
        tagLib.springSecurityService = null
        tagLib.webExpressionHandler = null
        tagLib.webInvocationPrivilegeEvaluator = null

        expect:
        tagLib."$tag"(attrs).toString() == ''

        where:
        tag                            | attrs
        'ifAllGranted'                 | [roles: 'ROLE_USER']
        'ifNotGranted'                 | [roles: 'ROLE_USER']
        'ifAnyGranted'                 | [roles: 'ROLE_USER']
        'loggedInUserInfo'             | [field: 'username']
        'username'                     | [:]
        'ifLoggedIn'                   | [:]
        'ifNotLoggedIn'                | [:]
        'ifSwitched'                   | [:]
        'ifNotSwitched'                | [:]
        'switchedUserOriginalUsername' | [:]
        'access'                       | [expression: 'hasRole("ROLE_USER")']
        'link'                         | [controller: 'admin', action: 'index', fallback: true]
        'noAccess'                     | [expression: 'hasRole("ROLE_USER")']
    }

    void 'the tags are active when the property is not set'() {
        given: 'a service that reports nobody logged in'
        tagLib.springSecurityService = Mock(SpringSecurityService) {
            isLoggedIn() >> false
        }

        expect: 'the default is active, so the tag runs and renders its body'
        tagLib.ifNotLoggedIn([:], TAG_BODY).toString() == TAG_BODY
    }
}
