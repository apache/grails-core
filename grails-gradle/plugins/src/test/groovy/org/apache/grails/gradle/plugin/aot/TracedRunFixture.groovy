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
package org.apache.grails.gradle.plugin.aot

import java.util.concurrent.CountDownLatch

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

/**
 * An application for the trace to record: it serves forms, guards a page behind a login, and writes
 * down what it was asked for.
 *
 * <p>The form is the point. A page can be fetched by anything; only submitting one shows whether the
 * fields a form declares are the fields that arrive.</p>
 *
 * <p>Two of the shapes here are the ones a trace gets wrong rather than the ones it gets right: a
 * page carrying a layout form ahead of its own, and a page that answers with a redirect to the login
 * form until the login form has been submitted.</p>
 */
class TracedRunFixture {

    /** What a page carrying a layout form ahead of its own looks like. */
    private static final String TWO_FORMS = '''<html><body>
        <form id="searchForm" action="/search" method="post">
            <input type="text" name="q"/>
        </form>
        <form id="bookForm" action="/save" method="post">
            <input type="hidden" name="_csrf" value="a-token"/>
            <input type="text" name="title"/>
            <input type="password" name="secret"/>
            <input type="checkbox" name="published"/>
            <input type="submit" name="go" value="Save"/>
        </form></body></html>'''

    private static final String LOGIN_FORM = '''<html><body>
        <form id="loginForm" action="/authenticate" method="post">
            <input type="text" name="username"/>
            <input type="password" name="password"/>
        </form></body></html>'''

    /** A form only a signed-in visitor is shown, which is what makes reaching it worth checking. */
    private static final String PROTECTED_FORM = '''<html><body>
        <form id="protectedForm" action="/protected-save" method="post">
            <input type="text" name="secretTitle"/>
        </form></body></html>'''

    static void main(String[] args) {
        int port = Integer.parseInt(args[0])
        File requested = new File(args[1])
        String behaviour = args.length > 2 ? args[2] : 'ok'

        HttpServer server = HttpServer.create(new InetSocketAddress('localhost', port), 0)
        server.createContext('/') { HttpExchange exchange ->
            String path = exchange.requestURI.path
            String method = exchange.requestMethod
            String body = method == 'POST' ? exchange.requestBody.getText('UTF-8') : ''
            requested << "${method} ${path}${body ? ' ' + body : ''}\n"
            boolean signedIn = exchange.requestHeaders.getFirst('Cookie')?.contains('session=in')

            int status = 200
            String response = 'served'
            if (path == '/broken') {
                status = 500
                response = 'no'
            }
            else if (path == '/create') {
                response = TWO_FORMS
            }
            else if (path == '/login') {
                response = LOGIN_FORM
            }
            else if (path == '/authenticate') {
                exchange.responseHeaders.add('Set-Cookie', 'session=in; Path=/')
                status = 302
                exchange.responseHeaders.add('Location', '/')
                response = ''
            }
            else if (path == '/protected-form') {
                // the shape that made a redirected form page count as traced: the page it lands on
                // has a form of its own, so everything after the redirect succeeds
                if (!signedIn) {
                    status = 302
                    exchange.responseHeaders.add('Location', '/login')
                    response = ''
                }
                else {
                    response = PROTECTED_FORM
                }
            }
            else if (path == '/protected') {
                // answered from the login page until the login form has been submitted, which is
                // what makes asking for it before submitting that form record the wrong page
                if (!signedIn) {
                    status = 302
                    exchange.responseHeaders.add('Location', '/login')
                    response = ''
                }
                else {
                    response = 'the protected page'
                }
            }
            else if (path == '/save' && behaviour == 'save-fails') {
                status = 500
                response = 'no'
            }

            byte[] bytes = response.bytes
            exchange.sendResponseHeaders(status, bytes.length ?: -1)
            if (bytes.length) {
                exchange.responseBody.withCloseable { it.write(bytes) }
            }
            else {
                exchange.responseBody.close()
            }
        }
        server.start()
        println 'Started Application in 0.05 seconds'
        System.out.flush()
        new CountDownLatch(1).await()
    }
}
