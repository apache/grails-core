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
package org.grails.io.support

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

class AbstractFileResolvingResourceSpec extends Specification {

    @TempDir
    File dir

    @Shared
    HttpServer server

    @Shared
    int port

    void setupSpec() {
        server = HttpServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
        server.createContext('/ok') { HttpExchange exchange ->
            exchange.responseHeaders.add('Last-Modified', 'Thu, 01 Jan 2015 00:00:00 GMT')
            if (exchange.requestMethod == 'HEAD') {
                exchange.responseHeaders.add('Content-Length', '5')
                exchange.sendResponseHeaders(200, -1)
            }
            else {
                exchange.sendResponseHeaders(200, 5)
                exchange.responseBody.write('hello'.bytes)
            }
            exchange.close()
        }
        server.createContext('/missing') { HttpExchange exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.createContext('/error') { HttpExchange exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        port = server.address.port
    }

    void cleanupSpec() {
        server.stop(0)
    }

    private UrlResource http(String path) {
        new UrlResource("http://${InetAddress.loopbackAddress.hostAddress}:${port}${path}".toString())
    }

    private File fileWith(String name, String content) {
        File file = new File(dir, name)
        file.text = content
        file
    }

    void 'a file resource resolves, reads and measures the underlying file'() {
        given:
        File file = fileWith('data.txt', 'hello')
        UrlResource resource = new UrlResource(file.toURI().toURL())

        expect:
        resource.exists()
        resource.readable
        resource.file == file
        resource.contentLength() == 5
        resource.lastModified() == file.lastModified()
    }

    void 'a missing file is neither existing nor readable'() {
        given:
        UrlResource resource = new UrlResource(new File(dir, 'nope.txt').toURI().toURL())

        expect:
        !resource.exists()
        !resource.readable
    }

    void 'a directory exists but is not readable'() {
        given:
        UrlResource resource = new UrlResource(dir.toURI().toURL())

        expect:
        resource.exists()
        !resource.readable
    }

    void 'a resource outside the file system cannot be resolved to a file'() {
        when:
        new UrlResource('http://localhost/x').file

        then:
        FileNotFoundException e = thrown()
        e.message.contains('does not reside in the file system')
    }

    void 'readable swallows the failure to resolve a file for a class path resource that does not exist'() {
        expect:
        !new ClassPathResource('no/such/resource.txt').readable
        !new ClassPathResource('no/such/resource.txt').exists()
    }

    void 'exists reflects the status of an http resource'() {
        expect:
        http('/ok').exists()
        !http('/missing').exists()
        !http('/error').exists()
    }

    void 'an unreachable http resource does not exist'() {
        given:
        int unusedPort
        new ServerSocket(0).withCloseable { unusedPort = it.localPort }

        expect:
        !new UrlResource("http://${InetAddress.loopbackAddress.hostAddress}:${unusedPort}/x".toString()).exists()
    }

    void 'an http resource is readable'() {
        expect:
        http('/ok').readable
    }

    void 'contentLength and lastModified come from the http response headers'() {
        expect:
        http('/ok').contentLength() == 5
        http('/ok').lastModified() == 1420070400000L
    }

    void 'a resource inside a jar reports a length and exists'() {
        given:
        File jar = new File(dir, 'sample.jar')
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { JarOutputStream out ->
            out.putNextEntry(new JarEntry('inside.txt'))
            out.write('jar content'.bytes)
            out.closeEntry()
        }
        UrlResource resource = new UrlResource("jar:${jar.toURI()}!/inside.txt".toString())

        expect:
        resource.exists()
        resource.readable
        resource.contentLength() == 11
        resource.inputStream.text == 'jar content'
        resource.getFileForLastModifiedCheck() == jar
    }

    void 'the file used for the last modified check is the file itself for a file resource'() {
        given:
        File file = fileWith('plain.txt', 'x')

        expect:
        new UrlResource(file.toURI().toURL()).getFileForLastModifiedCheck() == file
    }

    void 'a class path resource inside a jar is read through the jar and not as a file'() {
        given:
        ClassPathResource resource = new ClassPathResource('spock/lang/Specification.class')

        expect:
        resource.exists()
        resource.readable
        resource.contentLength() > 0
        resource.getFileForLastModifiedCheck().name.endsWith('.jar')

        when:
        resource.file

        then:
        thrown(FileNotFoundException)
    }

}
