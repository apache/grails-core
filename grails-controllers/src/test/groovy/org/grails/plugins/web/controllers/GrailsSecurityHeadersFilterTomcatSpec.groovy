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

package org.grails.plugins.web.controllers

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path

import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.FilterDef
import org.apache.tomcat.util.descriptor.web.FilterMap

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Runs {@link GrailsSecurityHeadersFilter} in front of servlets on embedded Tomcat. The Spring
 * mocks accept header writes after the response has committed, so only a real container can
 * show that the headers are on the wire for every commit path: the body outgrowing the
 * response buffer (including multi-byte text, which fills the byte-sized buffer before the
 * character count does), a declared Content-Length reached through println, redirects,
 * errors, and a filter that serves the response itself without continuing the chain.
 */
class GrailsSecurityHeadersFilterTomcatSpec extends Specification {

    private static final String DEFAULT_REFERRER_POLICY = 'strict-origin-when-cross-origin'

    private static final int LINE_SEPARATOR_LENGTH = System.lineSeparator().length()

    @Shared
    @AutoCleanup('stop')
    Tomcat tomcat

    @Shared
    int port

    @Shared
    @TempDir
    Path baseDir

    @Shared
    @TempDir
    Path docBase

    @Shared
    HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    void setupSpec() {
        tomcat = new Tomcat()
        tomcat.baseDir = baseDir.toString()
        tomcat.setPort(0)
        tomcat.connector
        Context context = tomcat.addContext('', docBase.toString())

        Tomcat.addServlet(context, 'smallStream', new BodyServlet(100, false))
        context.addServletMappingDecoded('/small-stream', 'smallStream')
        Tomcat.addServlet(context, 'largeStream', new BodyServlet(50 * 1024, false))
        context.addServletMappingDecoded('/large-stream', 'largeStream')
        Tomcat.addServlet(context, 'largeWriter', new BodyServlet(50 * 1024, true))
        context.addServletMappingDecoded('/large-writer', 'largeWriter')
        Tomcat.addServlet(context, 'multiByteWriter', new MultiByteWriterServlet())
        context.addServletMappingDecoded('/multi-byte-writer', 'multiByteWriter')
        Tomcat.addServlet(context, 'println', new PrintlnServlet())
        context.addServletMappingDecoded('/println', 'println')
        Tomcat.addServlet(context, 'redirect', new RedirectServlet())
        context.addServletMappingDecoded('/redirect', 'redirect')
        Tomcat.addServlet(context, 'error', new ErrorServlet())
        context.addServletMappingDecoded('/error', 'error')
        Tomcat.addServlet(context, 'reset', new ResetServlet())
        context.addServletMappingDecoded('/reset', 'reset')
        Tomcat.addServlet(context, 'writerIdentity', new WriterIdentityServlet())
        context.addServletMappingDecoded('/writer-identity', 'writerIdentity')
        Tomcat.addServlet(context, 'streamIdentity', new StreamIdentityServlet())
        context.addServletMappingDecoded('/stream-identity', 'streamIdentity')
        Tomcat.addServlet(context, 'unreachable', new BodyServlet(1, false))
        context.addServletMappingDecoded('/assets/*', 'unreachable')

        addFilter(context, 'grailsSecurityHeadersFilter',
                new GrailsSecurityHeadersFilter(new GrailsSecurityHeadersProperties()), '/*')
        addFilter(context, 'assetFilter', new ServeAssetWithoutChainFilter(), '/assets/*')

        tomcat.start()
        port = tomcat.connector.localPort
    }

    private static void addFilter(Context context, String name, jakarta.servlet.Filter filter, String pattern) {
        def filterDef = new FilterDef(filterName: name, filter: filter)
        context.addFilterDef(filterDef)
        def filterMap = new FilterMap(filterName: name)
        filterMap.addURLPatternDecoded(pattern)
        filterMap.setDispatcher(DispatcherType.REQUEST.name())
        filterMap.setDispatcher(DispatcherType.ERROR.name())
        context.addFilterMap(filterMap)
    }

    private HttpResponse<byte[]> get(String path) {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray())
    }

    @Unroll
    void 'headers are on the wire for #path (#description)'() {
        when:
        def response = get(path)

        then:
        response.statusCode() == status
        response.headers().firstValue('X-Content-Type-Options').orElse(null) == 'nosniff'
        response.headers().firstValue('X-Frame-Options').orElse(null) == 'SAMEORIGIN'
        response.headers().firstValue('Referrer-Policy').orElse(null) == referrerPolicy
        response.headers().firstValue('X-XSS-Protection').orElse(null) == '0'
        bodyLength == null || response.body().length == bodyLength

        where:
        path                 | description                                                     | status | bodyLength                | referrerPolicy
        '/small-stream'      | 'body below the response buffer'                                | 200    | 100                       | DEFAULT_REFERRER_POLICY
        '/large-stream'      | 'output stream body outgrowing the buffer, no flush'            | 200    | 50 * 1024                 | DEFAULT_REFERRER_POLICY
        '/large-writer'      | 'writer body outgrowing the buffer, no flush'                   | 200    | 50 * 1024                 | DEFAULT_REFERRER_POLICY
        '/multi-byte-writer' | 'three-byte UTF-8 text filling an enlarged buffer, no flush'    | 200    | 60_000                    | DEFAULT_REFERRER_POLICY
        '/println'           | 'Content-Length reached by println'                             | 200    | 2 + LINE_SEPARATOR_LENGTH | 'no-referrer'
        '/redirect'          | 'redirect committed inside the chain'                           | 302    | null                      | DEFAULT_REFERRER_POLICY
        '/error'             | 'sendError committed inside the chain'                          | 404    | null                      | DEFAULT_REFERRER_POLICY
        '/reset'             | 'reset after the buffer filled but before it flushed'           | 500    | 5                         | DEFAULT_REFERRER_POLICY
        '/assets/a.js'       | 'served by an inner filter that never continues the chain'      | 200    | 14                        | DEFAULT_REFERRER_POLICY
    }

    void 'a header set by the servlet wins over the Grails default'() {
        when:
        def response = get('/println')

        then:
        response.headers().firstValue('Referrer-Policy').get() == 'no-referrer'
    }

    void 'the writer and output stream are the same instance on every call until a reset, like the container\'s'() {
        when:
        def writerResponse = get('/writer-identity')
        def streamResponse = get('/stream-identity')

        then:
        writerResponse.body() == 'ok'.bytes
        writerResponse.headers().firstValue('X-Content-Type-Options').orElse(null) == 'nosniff'
        writerResponse.headers().firstValue('X-Same-Writer').get() == 'true'
        writerResponse.headers().firstValue('X-New-Writer-After-Reset').get() == 'true'
        streamResponse.headers().firstValue('X-Same-Stream').get() == 'true'
    }

    private static class BodyServlet extends HttpServlet {

        private final int size
        private final boolean useWriter

        BodyServlet(int size, boolean useWriter) {
            this.size = size
            this.useWriter = useWriter
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.contentType = 'text/plain'
            if (useWriter) {
                response.writer.write('x' * size)
            }
            else {
                response.outputStream.write(('x' * size).bytes)
            }
        }
    }

    /**
     * The response buffer is sized in bytes, so three-byte characters fill it, and Tomcat
     * commits it, at a third of the character count. At Tomcat's default buffer size this
     * is masked by how it batches character conversion; an enlarged buffer exposes it.
     */
    private static class MultiByteWriterServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.bufferSize = 32 * 1024
            response.contentType = 'text/plain;charset=UTF-8'
            400.times { response.writer.write('日' * 50) }
        }
    }

    private static class PrintlnServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.contentType = 'text/plain'
            response.setHeader('Referrer-Policy', 'no-referrer')
            response.setContentLength(2 + System.lineSeparator().length())
            response.writer.println('hi')
        }
    }

    private static class RedirectServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.sendRedirect('/small-stream')
        }
    }

    /**
     * Fills the response buffer exactly, which fires the filter's callback while Tomcat has
     * not yet committed (it flushes on the write after the buffer fills, and a reached
     * Content-Length commits immediately, so this is the one legal window), then resets
     * and replaces the response the way an error handler does. The replacement must carry
     * the headers too.
     */
    private static class ResetServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.contentType = 'text/plain'
            response.outputStream.write(new byte[response.bufferSize])
            response.reset()
            response.status = 500
            response.contentType = 'text/plain'
            response.outputStream.write('error'.bytes)
        }
    }

    private static class WriterIdentityServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.contentType = 'text/plain'
            PrintWriter first = response.writer
            boolean sameWriter = first.is(response.writer)
            first.write('discarded')
            response.reset()
            response.contentType = 'text/plain'
            PrintWriter second = response.writer
            response.setHeader('X-Same-Writer', String.valueOf(sameWriter && second.is(response.writer)))
            response.setHeader('X-New-Writer-After-Reset', String.valueOf(!second.is(first)))
            second.write('ok')
        }
    }

    private static class StreamIdentityServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.contentType = 'text/plain'
            ServletOutputStream stream = response.outputStream
            response.setHeader('X-Same-Stream', String.valueOf(stream.is(response.outputStream)))
            stream.write('ok'.bytes)
        }
    }

    private static class ErrorServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.sendError(404, 'missing')
        }
    }

    /**
     * Mirrors the asset-pipeline filter: on a hit it streams the asset and flushes without
     * ever calling {@code chain.doFilter}.
     */
    private static class ServeAssetWithoutChainFilter extends HttpFilter {

        @Override
        protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
            response.contentType = 'application/javascript'
            response.outputStream.write('console.log(1)'.bytes)
            response.flushBuffer()
        }
    }
}
