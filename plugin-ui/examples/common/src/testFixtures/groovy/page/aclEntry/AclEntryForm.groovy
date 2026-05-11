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
package page.aclEntry

import groovy.transform.Immutable

import geb.Page
import geb.module.Checkbox
import geb.module.Select
import geb.module.TextInput

@Immutable
class AclEntryForm {

    String aclObjectIdentityId
    String aceOrder
    String mask
    String sid
    Boolean auditFailure
    Boolean auditSuccess
    Boolean granting

    void applyTo(Page page) {
        if (aclObjectIdentityId != null) {
            page.$(name: 'aclObjectIdentity.id').module(TextInput).text = aclObjectIdentityId
        }
        if (aceOrder != null) {
            page.$(name: 'aceOrder').module(TextInput).text = aceOrder
        }
        if (mask != null) {
            page.$(name: 'mask').module(TextInput).text = mask
        }
        if (sid != null) {
            page.$(name: 'sid.id').module(Select).selected = sid
        }
        if (auditFailure != null) {
            applyToCheckbox(page.$(name: 'auditFailure').module(Checkbox), auditFailure)
        }
        if (auditSuccess != null) {
            applyToCheckbox(page.$(name: 'auditSuccess').module(Checkbox), auditSuccess)
        }
        if (granting != null) {
            applyToCheckbox(page.$(name: 'granting').module(Checkbox), granting)
        }
    }

    private static void applyToCheckbox(Checkbox checkbox, boolean value) {
        if (value) checkbox.check() else checkbox.uncheck()
    }
}
