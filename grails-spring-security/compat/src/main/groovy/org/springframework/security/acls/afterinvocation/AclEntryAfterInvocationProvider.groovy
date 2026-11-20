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
package org.springframework.security.acls.afterinvocation

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.AfterInvocationProvider
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.acls.domain.ObjectIdentityRetrievalStrategyImpl
import org.springframework.security.acls.domain.SidRetrievalStrategyImpl
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.AclService
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.acls.model.ObjectIdentityRetrievalStrategy
import org.springframework.security.acls.model.Permission
import org.springframework.security.acls.model.SidRetrievalStrategy
import org.springframework.security.core.Authentication
import org.springframework.util.Assert

@Slf4j
@CompileStatic
class AclEntryAfterInvocationProvider implements AfterInvocationProvider {

    final AclService aclService
    final String processConfigAttribute
    final List<Permission> requirePermission

    ObjectIdentityRetrievalStrategy objectIdentityRetrievalStrategy = new ObjectIdentityRetrievalStrategyImpl()
    SidRetrievalStrategy sidRetrievalStrategy = new SidRetrievalStrategyImpl()
    Class<?> processDomainObjectClass = Object

    AclEntryAfterInvocationProvider(AclService aclService, List<Permission> requirePermission) {
        this(aclService, 'AFTER_ACL_READ', requirePermission)
    }

    AclEntryAfterInvocationProvider(AclService aclService, String processConfigAttribute, List<Permission> requirePermission) {
        Assert.notNull(aclService, 'An AclService is mandatory')
        Assert.hasText(processConfigAttribute, 'A processConfigAttribute is mandatory')
        Assert.notEmpty(requirePermission, 'One or more requirePermission entries is mandatory')
        this.aclService = aclService
        this.processConfigAttribute = processConfigAttribute
        this.requirePermission = requirePermission
    }

    @Override
    Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> config, Object returnedObject) {
        if (returnedObject == null) {
            log.debug('Return object is null, skipping')
            return null
        }
        if (!processDomainObjectClass.isAssignableFrom(returnedObject.getClass())) {
            log.debug('Return object is not applicable for this provider, skipping')
            return returnedObject
        }
        for (def attr : config) {
            if (!supports(attr)) {
                continue
            }
            if (hasPermission(authentication, returnedObject)) {
                return returnedObject
            }
            log.debug('Denying access')
            throw new AccessDeniedException(
                    "Authentication ${authentication?.name} has NO permissions to the domain object $returnedObject"
            )
        }
        returnedObject
    }

    @Override
    boolean supports(ConfigAttribute attribute) {
        attribute?.attribute != null && attribute.attribute == processConfigAttribute
    }

    @Override
    boolean supports(Class<?> clazz) {
        true
    }

    protected boolean hasPermission(Authentication authentication, Object domainObject) {
        def objectIdentity = objectIdentityRetrievalStrategy.getObjectIdentity(domainObject)
        def sids = sidRetrievalStrategy.getSids(authentication)
        Acl acl
        try {
            acl = aclService.readAclById(objectIdentity, sids)
        }
        catch (NotFoundException ignored) {
            return false
        }
        try {
            return acl.isGranted(requirePermission, sids, false)
        }
        catch (NotFoundException ignored) {
            return false
        }
    }
}
