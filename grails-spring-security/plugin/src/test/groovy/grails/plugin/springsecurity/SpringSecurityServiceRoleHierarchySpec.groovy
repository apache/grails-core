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
import spock.lang.Unroll

import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

@Issue('https://github.com/apache/grails-core/issues/14498')
class SpringSecurityServiceRoleHierarchySpec extends AbstractUnitSpec {

    void setupSpec() {
        defineBeans {
            roleHierarchy(RoleHierarchyImpl)
        }
    }

    void setup() {
        ConfigObject config = new ConfigObject()
        config.roleHierarchyEntryClassName = RoleHierarchyEntryStub.name
        SpringSecurityUtils.securityConfig = config
    }

    void cleanup() {
        RoleHierarchyEntryStub.stored = []
    }

    private Set<String> reachable(RoleHierarchyImpl hierarchy, String role) {
        hierarchy.getReachableGrantedAuthorities([new SimpleGrantedAuthority(role)])*.authority as Set<String>
    }

    @Unroll
    void 'the hierarchy reloaded from #stored is read as it is at startup'() {
        given:
        SpringSecurityService service = new SpringSecurityService()
        service.grailsApplication = grailsApplication
        RoleHierarchyEntryStub.stored = stored.collect { String line -> new RoleHierarchyEntryStub(entry: line) }
        RoleHierarchyImpl atStartup = RoleHierarchyImpl.fromHierarchy(RoleHierarchyNormalizer.normalize(stored.join('\n')))

        when:
        service.reloadDBRoleHierarchy()
        RoleHierarchyImpl reloaded = grailsApplication.mainContext.getBean('roleHierarchy', RoleHierarchyImpl)

        then:
        reachable(reloaded, 'ROLE_ADMIN') == reachable(atStartup, 'ROLE_ADMIN')
        reachable(reloaded, 'ROLE_ADMIN') == expected as Set

        where:
        stored                                          || expected
        ['ROLE_ADMIN>ROLE_USER']                        || ['ROLE_ADMIN', 'ROLE_USER']
        ['ROLE_ADMIN > ROLE_USER']                      || ['ROLE_ADMIN', 'ROLE_USER']
        ['ROLE_ADMIN>ROLE_USER>ROLE_GUEST']             || ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_GUEST']
        ['ROLE_ADMIN>ROLE_USER', 'ROLE_USER>ROLE_GUEST'] || ['ROLE_ADMIN', 'ROLE_USER', 'ROLE_GUEST']
        ['ROLE_ADMIN>>>ROLE_USER']                      || ['ROLE_ADMIN']
        ['>ROLE_USER']                                  || ['ROLE_ADMIN']
        []                                              || ['ROLE_ADMIN']
    }
}

class RoleHierarchyEntryStub {

    static List<RoleHierarchyEntryStub> stored = []

    String entry

    static List<RoleHierarchyEntryStub> list() {
        stored
    }

    static withTransaction(Closure work) {
        work.call()
    }
}
