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
