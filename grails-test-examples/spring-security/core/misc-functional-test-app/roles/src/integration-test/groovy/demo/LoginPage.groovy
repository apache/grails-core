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

package demo

import geb.Page

class LoginPage extends Page {
    static url = "login/auth"

    static at = { title == 'Login' }

    static content = {
        loginButton { $("#submit", 0) }
        usernameInputField { $("#username", 0) }
        passwordInputField { $("#password", 0) }
    }

    void login(String username, String password) {
        usernameInputField << username
        passwordInputField << password
        loginButton.click()
        // Submitting the form is a navigation, and the browser goes on showing this page until the
        // response lands. Returning before that leaves whatever the specification asserts next
        // reading the login form rather than the page it logged in to. The url says the browser has
        // left, where the title would only say the page reads differently - and a page reads
        // differently in another language.
        waitFor { !browser.currentUrl.contains('/login/') }
    }
}