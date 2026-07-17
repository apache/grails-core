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
package org.springframework.security.acls

import java.lang.reflect.InvocationTargetException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.aopalliance.intercept.MethodInvocation

import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.AuthorizationServiceException
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
import org.springframework.util.ObjectUtils
import org.springframework.util.StringUtils

@Slf4j
@CompileStatic
class AclEntryVoter implements AccessDecisionVoter<MethodInvocation> {

    private final AclService aclService
    private final String processConfigAttribute
    private final List<Permission> requirePermission

    ObjectIdentityRetrievalStrategy objectIdentityRetrievalStrategy = new ObjectIdentityRetrievalStrategyImpl()
    SidRetrievalStrategy sidRetrievalStrategy = new SidRetrievalStrategyImpl()
    Class<?> processDomainObjectClass = Object
    String internalMethod

    AclEntryVoter(AclService aclService, String processConfigAttribute, Permission[] requirePermission) {
        Assert.notNull(processConfigAttribute, 'A processConfigAttribute is mandatory')
        Assert.notNull(aclService, 'An AclService is mandatory')
        Assert.isTrue(!ObjectUtils.isEmpty(requirePermission), 'One or more requirePermission entries is mandatory')
        this.aclService = aclService
        this.processConfigAttribute = processConfigAttribute
        this.requirePermission = Arrays.asList(requirePermission)
    }

    AclEntryVoter(AclService aclService, String processConfigAttribute, List<Permission> requirePermission) {
        this(aclService, processConfigAttribute, requirePermission as Permission[])
    }

    @Override
    boolean supports(ConfigAttribute attribute) {
        attribute?.attribute != null && attribute.attribute == processConfigAttribute
    }

    @Override
    boolean supports(Class<?> clazz) {
        MethodInvocation.isAssignableFrom(clazz)
    }

    @Override
    int vote(Authentication authentication, MethodInvocation object, Collection<ConfigAttribute> attributes) {
        for (def attr : attributes) {
            if (!supports(attr)) {
                continue
            }
            def domainObject = getDomainObjectInstance(object)
            if (domainObject == null) {
                log.debug('Voting to abstain - domainObject is null')
                return ACCESS_ABSTAIN
            }
            if (StringUtils.hasText(internalMethod)) {
                domainObject = invokeInternalMethod(domainObject)
            }
            def objectIdentity = objectIdentityRetrievalStrategy.getObjectIdentity(domainObject)
            def sids = sidRetrievalStrategy.getSids(authentication)
            Acl acl
            try {
                acl = aclService.readAclById(objectIdentity, sids)
            }
            catch (NotFoundException ignored) {
                log.debug('Voting to deny access - no ACLs apply for this principal')
                return ACCESS_DENIED
            }
            try {
                if (acl.isGranted(requirePermission, sids, false)) {
                    log.debug('Voting to grant access')
                    return ACCESS_GRANTED
                }
                log.debug('Voting to deny access - ACLs returned, but insufficient permissions for this principal')
                return ACCESS_DENIED
            }
            catch (NotFoundException ignored) {
                log.debug('Voting to deny access - no ACLs apply for this principal')
                return ACCESS_DENIED
            }
        }
        ACCESS_ABSTAIN
    }

    protected Object getDomainObjectInstance(MethodInvocation invocation) {
        for (def arg : invocation.arguments) {
            if (arg != null && processDomainObjectClass.isAssignableFrom(arg.getClass())) {
                return arg
            }
        }
        throw new AuthorizationServiceException(
                "MethodInvocation: $invocation did not provide any argument of type: ${processDomainObjectClass.name}"
        )
    }

    private Object invokeInternalMethod(Object domainObject) {
        try {
            def method = domainObject.getClass().getMethod(internalMethod)
            return method.invoke(domainObject)
        }
        catch (NoSuchMethodException ignored) {
            throw new AuthorizationServiceException(
                    "Object of class '${domainObject.getClass()}' does not provide " +
                            "the requested internalMethod: $internalMethod"
            )
        }
        catch (IllegalAccessException | InvocationTargetException ex) {
            log.debug('Problem invoking internalMethod', ex)
            throw new AuthorizationServiceException(
                    "Problem invoking internalMethod: $internalMethod for object: $domainObject"
            )
        }
    }
}
