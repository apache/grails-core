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
package page.user

import groovy.transform.Immutable

import geb.module.Checkbox
import geb.module.PasswordInput
import geb.module.TextInput
import page.LifecyclePage

@Immutable
class UserForm {

    String username
    String password
    Boolean enabled
    Boolean accountExpired
    Boolean accountLocked
    Boolean passwordExpired

    <P extends LifecyclePage> void applyTo(P page) {
        if (username != null) page.$('#username').module(TextInput).text = username
        if (password != null) page.$('#password').module(PasswordInput).text = password
        if (enabled != null) updateCheckbox(page.$(name: 'enabled').module(Checkbox), enabled)
        if (accountExpired != null) updateCheckbox(page.$(name: 'accountExpired').module(Checkbox), accountExpired)
        if (accountLocked != null) updateCheckbox(page.$(name: 'accountLocked').module(Checkbox), accountLocked)
        if (passwordExpired != null) updateCheckbox(page.$(name: 'passwordExpired').module(Checkbox), passwordExpired)
    }

    private static void updateCheckbox(Checkbox checkbox, Boolean value) {
        if (value) checkbox.check() else checkbox.uncheck()
    }
}
