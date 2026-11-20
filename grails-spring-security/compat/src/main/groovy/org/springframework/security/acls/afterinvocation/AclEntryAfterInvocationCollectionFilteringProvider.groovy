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

import java.lang.reflect.Array

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.springframework.security.access.AuthorizationServiceException
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.acls.model.AclService
import org.springframework.security.acls.model.Permission
import org.springframework.security.core.Authentication

@Slf4j
@CompileStatic
class AclEntryAfterInvocationCollectionFilteringProvider extends AclEntryAfterInvocationProvider {

    AclEntryAfterInvocationCollectionFilteringProvider(AclService aclService, List<Permission> requirePermission) {
        super(aclService, 'AFTER_ACL_COLLECTION_READ', requirePermission)
    }

    @Override
    Object decide(Authentication authentication, Object object, Collection<ConfigAttribute> config, Object returnedObject) {
        if (returnedObject == null) {
            log.debug('Return object is null, skipping')
            return null
        }
        for (def attr : config) {
            if (!supports(attr)) {
                continue
            }
            if (returnedObject instanceof Collection) {
                def filtered = filterCollection(authentication, (Collection<?>) returnedObject)
                return filtered
            }
            if (returnedObject.getClass().isArray()) {
                return filterArray(authentication, (Object[]) returnedObject)
            }
            throw new AuthorizationServiceException(
                    'A Collection or an array (or null) was required as the returnedObject, ' +
                            'but the returnedObject was: ' + returnedObject
            )
        }
        returnedObject
    }

    private Collection<?> filterCollection(Authentication authentication, Collection<?> objects) {
        def removeList = [] as Set<Object>
        for (def domainObject : objects) {
            if (domainObject == null || !processDomainObjectClass.isAssignableFrom(domainObject.getClass())) {
                continue
            }
            if (!hasPermission(authentication, domainObject)) {
                removeList << domainObject
                log.debug('Principal is NOT authorised for element: {}', domainObject)
            }
        }
        objects.removeAll(removeList)
        objects
    }

    private Object[] filterArray(Authentication authentication, Object[] objects) {
        List<Object> filtered = []
        for (Object domainObject in objects) {
            if (domainObject == null || !processDomainObjectClass.isAssignableFrom(domainObject.getClass()) || hasPermission(authentication, domainObject)) {
                filtered << domainObject
            }
            else {
                log.debug('Principal is NOT authorised for element: {}', domainObject)
            }
        }
        def result = Array.newInstance(objects.getClass().componentType, filtered.size()) as Object[]
        filtered.toArray(result)
    }
}
