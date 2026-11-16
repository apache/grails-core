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

import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Issue
import spock.lang.Specification

/**
 * Spring Security reads a role hierarchy only when the {@code >} operator has a space on each side, and ignores a line
 * written {@code ROLE_ADMIN>ROLE_USER} without a word, so the roles of the user stayed what they were. Grails 3 accepted
 * the form without spaces, and the setting is read the same way here.
 */
@Issue('#15892')
class RoleHierarchyNormalizerSpec extends Specification {

    void 'a hierarchy written without spaces around the operator grants the roles it implies'() {
        given:
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy(RoleHierarchyNormalizer.normalize(configured))

        expect:
        hierarchy.getReachableGrantedAuthorities([new SimpleGrantedAuthority('ROLE_ADMIN')])*.authority.sort() == reachable

        where:
        configured                                                    || reachable
        'ROLE_ADMIN>ROLE_USER'                                        || ['ROLE_ADMIN', 'ROLE_USER']
        'ROLE_ADMIN > ROLE_USER'                                      || ['ROLE_ADMIN', 'ROLE_USER']
        'ROLE_ADMIN >ROLE_USER'                                       || ['ROLE_ADMIN', 'ROLE_USER']
        'ROLE_ADMIN> ROLE_USER'                                       || ['ROLE_ADMIN', 'ROLE_USER']
        '''
                ROLE_ADMIN>ROLE_USER
        '''                                                           || ['ROLE_ADMIN', 'ROLE_USER']
        'ROLE_ADMIN>ROLE_USER\nROLE_USER>ROLE_GUEST'                  || ['ROLE_ADMIN', 'ROLE_GUEST', 'ROLE_USER']
        '  ROLE_ADMIN  >  ROLE_USER  \r\n\r\n  ROLE_USER>ROLE_GUEST ' || ['ROLE_ADMIN', 'ROLE_GUEST', 'ROLE_USER']
    }

    void 'a chain grants every role after the first, with or without spaces, and a malformed line grants nothing'() {
        given:
        RoleHierarchyImpl hierarchy = RoleHierarchyImpl.fromHierarchy(RoleHierarchyNormalizer.normalize(configured))

        expect:
        hierarchy.getReachableGrantedAuthorities([new SimpleGrantedAuthority('ROLE_ADMIN')])*.authority.sort() == reachable

        where:
        form                                       | configured                                         || reachable
        'a chain without spaces'                   | 'ROLE_ADMIN>ROLE_USER>ROLE_GUEST'                  || ['ROLE_ADMIN', 'ROLE_GUEST', 'ROLE_USER']
        'a chain with spaces'                      | 'ROLE_ADMIN > ROLE_USER > ROLE_GUEST'              || ['ROLE_ADMIN', 'ROLE_GUEST', 'ROLE_USER']
        'a chain with tabs'                        | 'ROLE_ADMIN\t>\tROLE_USER\t>\tROLE_GUEST'          || ['ROLE_ADMIN', 'ROLE_GUEST', 'ROLE_USER']
        'three operators in a row'                 | 'ROLE_ADMIN>>>ROLE_USER'                           || ['ROLE_ADMIN']
        'two operators in a row'                   | 'ROLE_ADMIN>>ROLE_USER'                            || ['ROLE_ADMIN']
        'a trailing operator'                      | 'ROLE_ADMIN>ROLE_USER>'                            || ['ROLE_ADMIN']
        'a leading operator'                       | '>ROLE_ADMIN>ROLE_USER'                            || ['ROLE_ADMIN']
        'a chain broken by an empty piece'         | 'ROLE_ADMIN>ROLE_USER>>ROLE_GUEST'                 || ['ROLE_ADMIN']
        'a good line, then a malformed one'        | 'ROLE_ADMIN>ROLE_USER\nROLE_USER>>ROLE_GUEST'      || ['ROLE_ADMIN', 'ROLE_USER']
        'a malformed line, then a good one'        | 'ROLE_ADMIN>>ROLE_USER\nROLE_ADMIN>ROLE_GUEST'     || ['ROLE_ADMIN', 'ROLE_GUEST']
    }

    void 'a line with an empty piece is left as written, not rewritten into an operator that is read as a role'() {
        expect:
        RoleHierarchyNormalizer.normalize(configured) == normalized

        where:
        configured                              || normalized
        'ROLE_ADMIN>>>ROLE_USER'                || 'ROLE_ADMIN>>>ROLE_USER'
        ' ROLE_ADMIN>ROLE_USER> '               || 'ROLE_ADMIN>ROLE_USER>'
        '>ROLE_ADMIN>ROLE_USER'                 || '>ROLE_ADMIN>ROLE_USER'
        'ROLE_A>ROLE_B\nROLE_B>>ROLE_C'         || 'ROLE_A > ROLE_B\nROLE_B>>ROLE_C'
    }

    void 'a hierarchy is rewritten with one space on each side of the operator, one line for each assignment'() {
        expect:
        RoleHierarchyNormalizer.normalize(configured) == normalized

        where:
        configured                                  || normalized
        'ROLE_ADMIN>ROLE_USER'                      || 'ROLE_ADMIN > ROLE_USER'
        ' ROLE_ADMIN  >   ROLE_USER '               || 'ROLE_ADMIN > ROLE_USER'
        'ROLE_A>ROLE_B\n\n  \nROLE_B>ROLE_C'        || 'ROLE_A > ROLE_B\nROLE_B > ROLE_C'
        'ROLE_A>ROLE_B\r\nROLE_B>ROLE_C'            || 'ROLE_A > ROLE_B\nROLE_B > ROLE_C'
        'ROLE_A > ROLE_B'                           || 'ROLE_A > ROLE_B'
    }

    void 'a missing or blank hierarchy stays as it is'() {
        expect:
        RoleHierarchyNormalizer.normalize(configured) == configured

        where:
        configured << [null, '']
    }
}
