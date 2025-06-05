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

package grails.plugin.springsecurity.acl

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.ReadOnly
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.springframework.security.acls.model.ObjectIdentity

@CompileStatic
class AclObjectIdentityGormService {

    @ReadOnly
    List<AclObjectIdentity> findAll() {
        findQueryAll().list()
    }

    protected DetachedCriteria<AclObjectIdentity> findQueryAll() {
        AclObjectIdentity.where { }
    }

    @ReadOnly
    AclObjectIdentity findById(Serializable id) {
        AclObjectIdentity.get(id)
    }

    @CompileDynamic
    @ReadOnly
    List<AclObjectIdentity> findAllByParentObjectIdAndParentAclClassName(Long objectId, String aclClassName) {
        //findQueryByParentObjectIdAndParentAclClassName(objectId, aclClassName).list()
        List<AclObjectIdentity> aclObjectIdentityList = findAll()
        aclObjectIdentityList.findAll { AclObjectIdentity oid ->
            (oid?.parent?.aclClass?.className == aclClassName) &&  ( oid?.parent?.objectId == objectId)
        }
    }

    @ReadOnly
    List<AclObjectIdentity> findAllByObjectIdAndAclClassName(Serializable objectId, String aclClassName) {
        findQueryByObjectIdAndAclClassName(objectId, aclClassName).list()
    }

    @ReadOnly
    AclObjectIdentity findByObjectIdentity(ObjectIdentity oid) {
        findQueryByObjectIdAndAclClassName(oid.identifier, oid.type).get()
    }

    @ReadOnly
    AclObjectIdentity findByObjectIdAndAclClassName(Serializable objectId, String aclClassName) {
        findQueryByObjectIdAndAclClassName(objectId, aclClassName).get()
    }

    protected DetachedCriteria<AclObjectIdentity> findQueryByObjectIdAndAclClassName(Serializable objectId, String aclClassName) {
        AclObjectIdentity.where {
            objectId == objectId && aclClass.className == aclClassName
        }
    }

    protected DetachedCriteria<AclObjectIdentity> findQueryByParentObjectIdAndParentAclClassName(Long objectId, String aclClassName) {
        AclObjectIdentity.where {
            parent.objectId == objectId && parent.aclClass.className == aclClassName
        }
    }
}