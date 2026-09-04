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
package com.example.pages

import geb.waiting.WaitTimeoutException

class LoginPage extends NavigationPage {

    static String pageTitle = 'Please sign in'

    static url = 'login'
    static at = { title == pageTitle }
    static content = {
        username { $('input', name: 'username') }
        password { $('input', name: 'password') }
        loginButton { $('button.primary') }
    }

    void login(String username = 'test@grails.org', String password = 'letmein') {
        // Reported at each step for GRAILS-16217: this page is sometimes submitted against a
        // session the browser no longer holds a cookie for, and the page that comes back is
        // another login page with nothing on it to say so. Which cookie was held, and when it
        // stopped being held, is what tells the three candidate explanations apart.
        report('login page loaded')
        this.username = username
        this.password = password
        report('about to submit')
        loginButton.click()
        // Wait for a definitive authenticated signal: the login page must be fully replaced
        // (title changed AND the login form is gone), not merely a transient title change.
        try {
            waitFor { title != pageTitle && $('input', name: 'username').empty }
        }
        catch (WaitTimeoutException neverLeftTheLoginPage) {
            report('login page never left')
            throw neverLeftTheLoginPage
        }
        report('signed in')
    }

    /**
     * Writes what the browser holds now. Guarded, because a report that throws would decide the
     * outcome of a test it is only supposed to describe.
     */
    private void report(String moment) {
        try {
            def cookies = browser.driver.manage().cookies.collect {
                // Hashed rather than printed: enough to see a session replaced or lost, without
                // putting a session id in a public log.
                "${it.name}#${Integer.toHexString(it.value?.hashCode() ?: 0)}@${it.domain}${it.path}"
            }
            // The cookies say the session survived; they cannot say whether the submit was ever
            // sent. A form still holding what was typed was never replaced, so the POST did not
            // leave the browser. A form present but empty is a *new* login document, so the POST
            // did reach the server and something answered with the login page again.
            def usernameField = $('input', name: 'username')
            String form = usernameField.empty ? 'gone' : (usernameField.value() ? 'retained' : 'empty')
            println "[16217] ${moment}: url=${browser.driver.currentUrl} form=${form} cookies=${cookies}"
        }
        catch (Throwable reportFailed) {
            println "[16217] ${moment}: could not be reported (${reportFailed.class.simpleName})"
        }
    }
}
