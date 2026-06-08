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
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.context.MessageSource

@Slf4j
@CompileStatic
class AclSidGormService implements WarnErros {

    MessageSource messageSource

    @ReadOnly
    AclSid findBySidAndPrincipal(String sidName, boolean principal) {
        findQueryBySidAndPrincipal(sidName, principal).get()
    }

    @Transactional
    AclSid saveBySidNameAndPrincipal(String sidName, boolean principal) {
        AclSid aclSidInstance = new AclSid(sid: sidName, principal: principal)
        if ( !aclSidInstance.save() ) {
            log.error '{}', errorsBeanBeingSaved(messageSource, aclSidInstance)
        }
        aclSidInstance
    }

    protected DetachedCriteria<AclSid> findQueryBySidAndPrincipal(String sidName, boolean principalParam) {
        AclSid.where { sid == sidName && principal == principalParam }
    }
}