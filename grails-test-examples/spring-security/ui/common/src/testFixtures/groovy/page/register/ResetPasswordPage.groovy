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
package page.register

import groovy.transform.Immutable

import geb.module.PasswordInput

import page.LifecyclePage

class ResetPasswordPage extends LifecyclePage {

    static url = 'register/resetPassword'
    static at = { title == 'Reset Password' }
    static content = {
        password { $('#password').module(PasswordInput) }
        password2 { $('#password2').module(PasswordInput) }
        submitBtn { $('a', id: 'submit') }
    }

    @Override
    String convertToPath(Object[] args) {
        args ? "?t=${args[0]}" : ''
    }

    def <T extends LifecyclePage> T enterNewPassword(Form formData, Class<T> expectedPageType) {
        formData.applyTo(this)
        submitBtn.click()
        T page = browser.at(expectedPageType)
        waitFor { page.loaded }
        page
    }

    def <T extends LifecyclePage> T submitResetPassword(Class<T> expectedPageType) {
        submitBtn.click()
        T page = browser.at(expectedPageType)
        waitFor { page.loaded }
        page
    }

    @Immutable
    static class Form {

        String password
        String password2

        void applyTo(ResetPasswordPage page) {
            if (password != null) page.password = password
            if (password2 != null) page.password2 = password2
        }
    }
}