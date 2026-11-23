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
package org.apache.grails.forge.cli.pack

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.apache.grails.forge.core.pack.Pack
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The client the command asks a repository with, against a repository that is a server on the loopback
 * address of this computer, with a certificate made for the run: nothing leaves the computer.
 */
class HttpsPackArtifactsSpec extends Specification {

    static final String PATH = '/m/com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar'
    static final char[] PASSWORD = 'changeit'.toCharArray()

    @Shared File store
    @Shared SSLContext context
    @Shared HttpsServer server
    @Shared CountDownLatch released = new CountDownLatch(1)
    @Shared Map<String, Closure> handlers = [:].asSynchronized()
    @Shared List<String> encodingsAsked = [].asSynchronized()

    void setupSpec() {
        store = Files.createTempFile('grailsforgecertificate', '.p12').toFile()
        store.delete()
        Process keytool = [new File(System.getProperty('java.home'), 'bin/keytool').path, '-genkeypair', '-alias', 'repository',
                           '-keyalg', 'RSA', '-keysize', '2048', '-dname', 'CN=localhost', '-ext', 'SAN=dns:localhost,ip:127.0.0.1',
                           '-validity', '2', '-storetype', 'PKCS12', '-keystore', store.path,
                           '-storepass', 'changeit', '-keypass', 'changeit'].execute()
        keytool.consumeProcessOutput()
        assert keytool.waitFor(60, TimeUnit.SECONDS) && keytool.exitValue() == 0
        KeyStore keys = KeyStore.getInstance('PKCS12')
        store.withInputStream { InputStream input -> keys.load(input, PASSWORD) }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
        keyManagers.init(keys, PASSWORD)
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.defaultAlgorithm)
        trustManagers.init(keys)
        context = SSLContext.getInstance('TLS')
        context.init(keyManagers.keyManagers, trustManagers.trustManagers, null)

        server = HttpsServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
        server.httpsConfigurator = new HttpsConfigurator(context)
        server.executor = Executors.newCachedThreadPool { Runnable task ->
            Thread thread = new Thread(task)
            thread.daemon = true
            return thread
        }
        server.createContext('/', { HttpExchange exchange ->
            encodingsAsked << exchange.requestHeaders.getFirst('Accept-Encoding')
            Closure handler = handlers[exchange.requestURI.path]
            try {
                if (handler == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    handler.call(exchange)
                }
            } finally {
                exchange.close()
            }
        } as HttpHandler)
        server.start()
    }

    void cleanupSpec() {
        released.countDown()
        server?.stop(0)
        store?.delete()
    }

    void setup() {
        handlers.clear()
        encodingsAsked.clear()
    }

    void "a jar and its checksum are taken over TLS, as the file it is"() {
        given:
        byte[] jar = jar()
        handlers[PATH] = { HttpExchange exchange -> send(exchange, 200, jar) }
        handlers[PATH + '.sha256'] = { HttpExchange exchange -> send(exchange, 200, digest(jar).bytes) }

        when:
        Pack pack = artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        pack.artifact.sha256 == digest(jar)
        pack.artifact.checkedAgainst == 'sha256'
        encodingsAsked == ['identity', 'identity']
    }

    void "a repository whose certificate is not trusted is a TLS failure"() {
        when: 'the client trusts what the JVM trusts, which this certificate is not among'
        new PackArtifacts().resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: TLS failure (SSLHandshakeException)")
    }

    void "an answer that begins and then stops is ended at the time an answer has"() {
        given:
        handlers[PATH] = { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write(new byte[10])
            exchange.responseBody.flush()
            released.await(120, TimeUnit.SECONDS)
        }

        when:
        long started = System.currentTimeMillis()
        artifacts(Duration.ofSeconds(1), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: timeout")
        System.currentTimeMillis() - started < 30000
    }

    void "a request that is never answered is ended at the time an answer has"() {
        given:
        handlers[PATH] = { HttpExchange exchange -> released.await(120, TimeUnit.SECONDS) }

        when:
        long started = System.currentTimeMillis()
        artifacts(Duration.ofSeconds(1), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: timeout")
        System.currentTimeMillis() - started < 30000
    }

    void "a request that is never answered is ended by the client itself when its time is the shorter"() {
        given:
        handlers[PATH] = { HttpExchange exchange -> released.await(120, TimeUnit.SECONDS) }

        when:
        long started = System.currentTimeMillis()
        new PackArtifacts(PackArtifacts.https(context, Duration.ofSeconds(1)), Duration.ofSeconds(120), Duration.ofSeconds(120))
                .resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: timeout")
        System.currentTimeMillis() - started < 30000
    }

    void "a redirect to this computer written as one number is not followed"() {
        given:
        byte[] jar = jar()
        List<String> reached = [].asSynchronized()
        handlers[PATH] = { HttpExchange exchange ->
            exchange.responseHeaders.add('Location', "https://2130706433:${server.address.port}/reached.jar")
            exchange.sendResponseHeaders(302, -1)
        }
        handlers['/reached.jar'] = { HttpExchange exchange ->
            reached << exchange.requestURI.path
            send(exchange, 200, jar)
        }

        when:
        artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: redirect to an address with a host or port no request can be made to")
        reached.isEmpty()
    }

    void "a checksum that is never answered is ended too, and is named"() {
        given:
        byte[] jar = jar()
        handlers[PATH] = { HttpExchange exchange -> send(exchange, 200, jar) }
        handlers[PATH + '.sha256'] = { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 0)
            released.await(120, TimeUnit.SECONDS)
        }

        when: 'the time an answer has is one in which the jar is answered on a busy machine too'
        artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: acme-audit-grails-2.3.0.jar.sha256: timeout")
    }

    void "a file that keeps coming slowly is ended at the time a file has"() {
        given:
        handlers[PATH] = { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 0)
            while (released.count > 0) {
                exchange.responseBody.write(1)
                exchange.responseBody.flush()
                Thread.sleep(100)
            }
        }

        when:
        long started = System.currentTimeMillis()
        artifacts(Duration.ofSeconds(30), Duration.ofSeconds(2)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: timeout")
        System.currentTimeMillis() - started < 30000
    }

    void "a redirect to an address the client would refuse with an exception of its own is a refusal like another"() {
        given:
        handlers[PATH] = { HttpExchange exchange ->
            exchange.responseHeaders.add('Location', location)
            exchange.sendResponseHeaders(302, -1)
        }

        when:
        artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: redirect to an address with a host or port no request can be made to")

        where:
        location << ['https://localhost:99999/x.jar', 'https://localhost./x.jar']
    }

    void "an answer that is encoded is not taken for the file"() {
        given:
        handlers[PATH] = { HttpExchange exchange ->
            exchange.responseHeaders.add('Content-Encoding', 'gzip')
            send(exchange, 200, jar())
        }

        when:
        artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null)

        then:
        PackArtifactException e = thrown()
        e.message.contains("${repository()}: the answer is encoded, and only a file as it is was asked for")
    }

    void "a repository that answers 403 for the checksum of a jar it gave is one that publishes none"() {
        given:
        byte[] jar = jar()
        handlers[PATH] = { HttpExchange exchange -> send(exchange, 200, jar) }
        handlers[PATH + '.sha256'] = { HttpExchange exchange -> exchange.sendResponseHeaders(403, -1) }
        handlers[PATH + '.sha1'] = { HttpExchange exchange -> exchange.sendResponseHeaders(401, -1) }

        expect:
        artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).resolve(coordinates(), [repository()], null).artifact.checkedAgainst == null
    }

    @Unroll
    void "a catalog that is given a few seconds is ended after them by the client as the command has it, when #what"() {
        given: 'the times of a jar, which are far longer'
        handlers['/catalog.json'] = handler
        PackArtifacts artifacts = new PackArtifacts(PackArtifacts.https(context, PackArtifacts.ANSWER_TIMEOUT), PackArtifacts.ANSWER_TIMEOUT, PackArtifacts.FILE_TIMEOUT)

        when:
        long started = System.nanoTime()
        artifacts.file(URI.create("https://localhost:${server.address.port}/catalog.json"), 4 * 1024 * 1024, 'the catalog', Duration.ofSeconds(2))

        then:
        PackArtifactException e = thrown()
        e.message == 'timeout'
        Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(30)
        Duration.ofNanos(System.nanoTime() - started) >= Duration.ofSeconds(2)

        where:
        what                                 | handler
        'the request is never answered'      | { HttpExchange exchange -> released.await(120, TimeUnit.SECONDS) }
        'the answer begins and then stops'   | { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 1000)
            exchange.responseBody.write('{"format": 1'.bytes)
            exchange.responseBody.flush()
            released.await(120, TimeUnit.SECONDS)
        }
        'the answer keeps coming slowly'     | { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 0)
            for (int sent = 0; sent < 600 && released.count > 0; sent++) {
                exchange.responseBody.write(' '.bytes)
                exchange.responseBody.flush()
                Thread.sleep(200)
            }
        }
    }

    void "a catalog is read over TLS as a jar is"() {
        given:
        handlers['/catalog.json'] = { HttpExchange exchange -> send(exchange, 200, '{"format": 1, "plugins": []}'.bytes) }

        expect:
        new String(artifacts(Duration.ofSeconds(30), Duration.ofSeconds(60)).file(URI.create("https://localhost:${server.address.port}/catalog.json"),
                1024, 'the catalog', Duration.ofSeconds(30)), 'UTF-8') == '{"format": 1, "plugins": []}'
    }

    private PackArtifacts artifacts(Duration answer, Duration file) {
        // the client has far longer for an answer than what watches it: what ends a stall here is the watch
        return new PackArtifacts(PackArtifacts.https(context, Duration.ofSeconds(120)), answer, file)
    }

    private static PackArtifacts.Coordinates coordinates() {
        return PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0')
    }

    private URI repository() {
        return PackArtifacts.repository("https://localhost:${server.address.port}/m")
    }

    private static void send(HttpExchange exchange, int status, byte[] body) {
        exchange.sendResponseHeaders(status, body.length)
        exchange.responseBody.write(body)
    }

    private static byte[] jar() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ZipOutputStream(bytes).withCloseable { ZipOutputStream zip ->
            zip.putNextEntry(new ZipEntry('META-INF/grails-forge/feature.yml'))
            zip.write(PackArtifactsSpec.DESCRIPTOR.getBytes('UTF-8'))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private static String digest(byte[] bytes) {
        return MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }
}
