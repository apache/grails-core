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

package module

import geb.Module
import geb.navigator.Navigator

/**
 * Module representing the Roles tab which is displayed on the User edit page
 */
class RolesTab extends Module {

    static content = {
        tab { $('a', href: '#tab-roles') }
    }

    void select() {
        tab.click()
        waitFor { $('li', 'aria-controls': 'tab-roles', 'aria-selected': 'true') }
    }

    int totalRoles() {
        return $('input', type: 'checkbox', id: startsWith('ROLE_')).size().toInteger()
    }

    int totalEnabledRoles() {
        return findAllEnabledRoles().size().toInteger()
    }

    Navigator findAllEnabledRoles() {
        return $('input', type: 'checkbox', id: startsWith('ROLE_'), checked: 'checked')
    }

    void enableRole(String roleName) {
        $('input', type: 'checkbox', id: roleName).value(true)
    }

    void disableRole(String roleName) {
        $('input', type: 'checkbox', id: roleName).value(false)
    }

    boolean hasEnabledRole(String roleName) {
        return hasEnabledRoles([roleName])
    }

    boolean hasEnabledRoles(List<String> roleNames) {
        return findAllEnabledRoles().collect { it.attr('id') }.containsAll(roleNames)
    }
}
