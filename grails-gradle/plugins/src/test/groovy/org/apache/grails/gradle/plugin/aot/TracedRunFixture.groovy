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

    /** What the fields plugin renders for a new instance: a value that is there and says nothing. */
    private static final String SCAFFOLDED_FORM = '''<html><body>
        <form action="/scaffolded-save" method="post">
            <input type="hidden" name="_csrf" value="token"/>
            <input type="hidden" name="version" value=""/>
            <input type="text" name="username" value=""/>
            <input type="password" name="password" value=""/>
            <input type="text" name="nickname" value="kept"/>
        </form></body></html>'''

    /**
     * A field of every type rendered with nothing in it, as the fields plugin renders a new instance's: a number
     * or a date sent text fails to bind, and the page answers with the form again and a 200.
     */
    private static final String TYPED_FORM = '''<html><body>
        <form action="/typed-save" method="post">
            <input type="hidden" name="_available"/>
            <input type="checkbox" name="available" value=""/>
            <input type="text" name="title" value="" maxlength="12"/>
            <textarea name="notes" maxlength="5"></textarea>
            <input type="search" name="query" value=""/>
            <input type="password" name="secret" value=""/>
            <input type="number" name="pages" value=""/>
            <input type="number" name="rating" value="" min="3" max="5"/>
            <input type="number decimal" name="price" value="" step="any" max="0.5"/>
            <input type="range" name="volume" value="" min="10" max="20"/>
            <input type="date" name="released" value=""/>
            <input type="date" name="launched" value="" min="2999-01-01"/>
            <input type="datetime-local" name="startsAt" value=""/>
            <input type="time" name="opensAt" value=""/>
            <input type="month" name="issue" value=""/>
            <input type="week" name="sprint" value=""/>
            <input type="email" name="contact" value=""/>
            <input type="email" name="shortContact" value="" maxlength="16"/>
            <input type="url" name="homepage" value=""/>
            <input type="tel" name="phone" value=""/>
            <input type="color" name="colour" value=""/>
        </form></body></html>'''

    /**
     * What the fields plugin renders as a select: an enum, an association, and a date picker's parts. Each binds
     * only one of its options, so text sent for any of them fails to convert.
     */
    private static final String SELECT_FORM = '''<html><body>
        <form action="/select-save" method="post">
            <select name="status" required="" id="status">
                <option value="DRAFT">Draft</option>
                <option value="PUBLISHED">Published</option>
            </select>
            <select name="author" id="author">
                <option value="null"></option>
                <option value="1">An author</option>
            </select>
            <input type="hidden" name="released" value="date.struct"/>
            <select name="released_day" id="released_day">
                <option value="1">1</option>
                <option value="9" selected="selected">9</option>
                <option value="10">10</option>
            </select>
            <select name="format">
                <option value="PAPER" disabled>Paper</option>
                <option value="HARDBACK">Hardback</option>
                <option value="selected">Odd</option>
            </select>
            <select name="genre">
                <optgroup label="Fiction"><option>Crime</option><option>Romance</option></optgroup>
            </select>
            <select name="tags" multiple="multiple">
                <option value="1">first</option>
                <option value="2" selected>second</option>
            </select>
            <select name="nothing"></select>
            <input type="text" name="title"/>
        </form></body></html>'''

    static void main(String[] args) {
        int port = Integer.parseInt(args[0])
        File requested = new File(args[1])
        String behaviour = args.length > 2 ? args[2] : 'ok'

        long openAt = System.currentTimeMillis() + 1500

        HttpServer server = HttpServer.create(new InetSocketAddress('localhost', port), 0)
        server.createContext('/') { HttpExchange exchange ->
            String path = exchange.requestURI.path
            String method = exchange.requestMethod
            boolean holding = behaviour == 'gated' && System.currentTimeMillis() < openAt
            boolean homePageDown = behaviour == 'homePageDown' && path == '/'
            if (holding || homePageDown) {
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
                return
            }
            String body = exchange.requestBody.getText('UTF-8')
            requested << "${method} ${path}${body ? ' ' + body : ''}\n"
            if (!(method in ['GET', 'POST'])) {
                requested << "HEADERS ${exchange.requestHeaders.getFirst('Content-Type')} ${exchange.requestHeaders.getFirst('Accept')}\n"
            }
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
            else if (path == '/scaffolded') {
                response = SCAFFOLDED_FORM
            }
            else if (path == '/typed') {
                response = TYPED_FORM
            }
            else if (path == '/select') {
                response = SELECT_FORM
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
