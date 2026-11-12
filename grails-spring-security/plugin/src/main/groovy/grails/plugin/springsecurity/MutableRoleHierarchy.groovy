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

import groovy.transform.CompileStatic
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.core.GrantedAuthority

/**
 * A mutable {@link RoleHierarchy} that allows the hierarchy definition to be replaced
 * at runtime, working around the immutability of Spring Security's
 * {@link RoleHierarchyImpl}.
 *
 * <p>Spring Security 6 made {@link RoleHierarchyImpl} effectively immutable by
 * removing public mutators in favour of {@code RoleHierarchyImpl.fromHierarchy(...)}.
 * This class wraps a delegate that is rebuilt every time the hierarchy string is
 * assigned, so callers (such as the plugin's {@code roleHierarchy} bean and any
 * application code that reads {@code grails.plugin.springsecurity.roleHierarchy}
 * at runtime) keep their original setter-based API.</p>
 *
 * <p>The {@code hierarchy} property uses the standard Spring Security syntax,
 * with one assignment per line, e.g.:</p>
 *
 * <pre>
 * ROLE_ADMIN &gt; ROLE_USER
 * ROLE_USER &gt; ROLE_GUEST
 * </pre>
 *
 * <p>Setting the property to {@code null} or an empty string clears the hierarchy.</p>
 */
@CompileStatic
class MutableRoleHierarchy implements RoleHierarchy {

    String hierarchy = ''

    private RoleHierarchy delegate = RoleHierarchyImpl.fromHierarchy('')

    void setHierarchy(String hierarchy) {
        this.hierarchy = hierarchy ?: ''
        delegate = RoleHierarchyImpl.fromHierarchy(this.hierarchy)
    }

    @Override
    Collection<? extends GrantedAuthority> getReachableGrantedAuthorities(Collection<? extends GrantedAuthority> authorities) {
        delegate.getReachableGrantedAuthorities(authorities)
    }
}
