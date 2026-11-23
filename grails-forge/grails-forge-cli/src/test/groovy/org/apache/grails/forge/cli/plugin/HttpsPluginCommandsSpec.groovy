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
package org.apache.grails.forge.cli.plugin

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.nio.file.Files
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import org.apache.grails.forge.cli.pack.PackArtifacts
import org.grails.forge.cli.ForgeContext
import org.springframework.context.ApplicationContext
import picocli.CommandLine
import spock.lang.Shared
import spock.lang.Specification

/**
 * {@code list-plugins} and {@code plugin-info} with the client the command line asks the network with,
 * against a plugin index and a Maven repository that are a server on the loopback address of this
 * computer, with a certificate made for the run: nothing leaves the computer.
 */
class HttpsPluginCommandsSpec extends Specification {

    static final char[] PASSWORD = 'changeit'.toCharArray()
    static final String INDEX_PATH = '/index/grails-plugins-index.json'
    static final String INDEX = '[{"coords":"org.example.plugins:audit-logging","name":"Audit Logging","desc":"Tracks changes to domain objects",' +
            '"versions":[{"version":"5.1.0","grailsVersion":"7.0.0 > *"}]}]'

    @Shared File store
    @Shared SSLContext context
    @Shared HttpsServer server
    @Shared CountDownLatch released = new CountDownLatch(1)
    @Shared Map<String, Closure> handlers = [:].asSynchronized()
    @Shared List<Map<String, List<String>>> headersAsked = [].asSynchronized()

    StringWriter out = new StringWriter()
    StringWriter err = new StringWriter()

    /** The Forge application of the command line, which these indexes, without a feature pack, never start */
    ForgeContext generator = new ForgeContext({ throw new IllegalStateException('no project generator is started by this specification') } as Supplier<ApplicationContext>)

    void setupSpec() {
        store = Files.createTempFile('grailsforgecertificate', '.p12').toFile()
        store.delete()
        Process keytool = [new File(System.getProperty('java.home'), 'bin/keytool').path, '-genkeypair', '-alias', 'index',
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
        server = start()
    }

    void cleanupSpec() {
        released.countDown()
        server?.stop(0)
        store?.delete()
    }

    void setup() {
        handlers.clear()
        headersAsked.clear()
    }

    void "the plugin index is read over TLS, by a request that says nothing of who asks"() {
        given:
        handlers[INDEX_PATH] = { HttpExchange exchange -> send(exchange, 200, INDEX.bytes) }

        when:
        int exit = list(Duration.ofSeconds(30), '--plugin-index', index(), 'audit')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().readLines().find { it.contains('audit-logging') } ==~ /  Audit Logging +org\.example\.plugins:audit-logging +5\.1\.0 +Tracks changes to domain objects/

        and: 'one request, with the name of the program and nothing of the user, the computer or the project'
        headersAsked.size() == 1
        headersAsked[0].collectEntries { String name, List<String> values -> [name.toLowerCase(), values] }.findAll { String name, List<String> values ->
            !(name in ['host', 'connection', 'content-length', 'http2-settings', 'upgrade'])
        } == ['user-agent': ['grails-forge'], 'accept-encoding': ['identity']]
    }

    void "a plugin index whose answer begins and then stops is not waited for"() {
        given:
        handlers[INDEX_PATH] = { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write('[{"coords":'.bytes)
            exchange.responseBody.flush()
            released.await(120, TimeUnit.SECONDS)
        }

        when:
        long started = System.currentTimeMillis()
        int exit = list(Duration.ofSeconds(2), '--plugin-index', index())

        then: 'the plugins of this release of Grails are the answer, with what was not read'
        exit == 0
        System.currentTimeMillis() - started < 30000
        err.toString().readLines() == ["| Warning The plugin index ${index()} was not read (timeout), so the plugins of the community are not known to this command: " +
                                               'only the plugins of this release of Grails are']
        out.toString().contains(' org.apache.grails:grails-cache ')
    }

    void "a plugin index that is never answered is not waited for"() {
        given:
        handlers[INDEX_PATH] = { HttpExchange exchange -> released.await(120, TimeUnit.SECONDS) }

        when:
        long started = System.currentTimeMillis()
        int exit = list(Duration.ofSeconds(2), '--plugin-index', index())

        then:
        exit == 0
        System.currentTimeMillis() - started < 30000
        err.toString().contains('was not read (timeout)')
        out.toString().contains(' org.apache.grails:grails-cache ')
    }

    void "a plugin index that does not end is read no further than an index can be large"() {
        given:
        handlers[INDEX_PATH] = { HttpExchange exchange ->
            exchange.sendResponseHeaders(200, 0)
            byte[] more = ('[' + (' ' * 65535)).bytes
            try {
                for (int sent = 0; sent < PluginIndex.MAX_BYTES * 2 && released.count > 0; sent += more.length) {
                    exchange.responseBody.write(more)
                }
            } catch (IOException ignored) {
                // the command stopped reading
            }
        }

        when:
        int exit = list(Duration.ofSeconds(60), '--plugin-index', index())

        then:
        exit == 0
        err.toString().contains("was not read (the plugin index has more than the ${PluginIndex.MAX_BYTES} bytes it can have)")
        out.toString().contains(' org.apache.grails:grails-cache ')
    }

    void "a server that is down is said to be, and the plugins of this release of Grails are listed"() {
        given: 'a port nothing listens on any more'
        HttpsServer gone = start()
        int port = gone.address.port
        gone.stop(0)
        String address = "https://127.0.0.1:${port}${INDEX_PATH}"

        when:
        int exit = list(Duration.ofSeconds(30), '--plugin-index', address)

        then:
        exit == 0
        err.toString().readLines() == ["| Warning The plugin index ${address} was not read (no connection (ConnectException)), so the plugins of the community are not " +
                                               'known to this command: only the plugins of this release of Grails are']
        out.toString().contains(' org.apache.grails:grails-cache ')
    }

    void "a plugin index that sends the command to an address that is not https is not followed there"() {
        given:
        List<String> reached = [].asSynchronized()
        handlers[INDEX_PATH] = { HttpExchange exchange ->
            exchange.responseHeaders.add('Location', "http://127.0.0.1:${server.address.port}/plain.json")
            exchange.sendResponseHeaders(302, -1)
        }
        handlers['/plain.json'] = { HttpExchange exchange ->
            reached << exchange.requestURI.path
            send(exchange, 200, INDEX.bytes)
        }

        when:
        int exit = list(Duration.ofSeconds(30), '--plugin-index', index())

        then:
        exit == 0
        err.toString().contains('was not read (redirect to an address that is not https)')
        reached.isEmpty()
        !out.toString().contains('audit-logging')
    }

    void "a plugin index whose certificate is not trusted is not read"() {
        given:
        handlers[INDEX_PATH] = { HttpExchange exchange -> send(exchange, 200, INDEX.bytes) }

        when: 'the client trusts what the JVM trusts, which this certificate is not among'
        int exit = run(new ListPluginsCommand(generator) {

            @Override
            protected String pluginIndexSetting() {
                return null
            }
        }, '--plugin-index', index())

        then:
        exit == 0
        err.toString().contains('was not read (TLS failure (SSLHandshakeException))')
        !out.toString().contains('audit-logging')
        out.toString().contains(' org.apache.grails:grails-cache ')
    }

    void "plugin-info reads the versions of a plugin and what it says of itself from a repository over TLS"() {
        given:
        handlers[INDEX_PATH] = { HttpExchange exchange -> send(exchange, 200, INDEX.bytes) }
        handlers['/maven/org/example/plugins/audit-logging/maven-metadata.xml'] = { HttpExchange exchange ->
            send(exchange, 200, ('<metadata><groupId>org.example.plugins</groupId><artifactId>audit-logging</artifactId><versioning><release>5.1.0</release>' +
                    '<versions><version>5.0.0</version><version>5.1.0</version></versions></versioning></metadata>').bytes)
        }
        handlers['/maven/org/example/plugins/audit-logging/5.1.0/audit-logging-5.1.0-plugin.xml'] = { HttpExchange exchange ->
            send(exchange, 200, "<plugin name='audit-logging' version='5.1.0'><title>Audit Logging Plugin</title><license>Apache-2.0</license></plugin>".bytes)
        }
        URI repository = URI.create("https://127.0.0.1:${server.address.port}/maven/")
        SSLContext trusted = context

        when:
        int exit = run(new PluginInfoCommand(generator) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(PackArtifacts.https(trusted, Duration.ofSeconds(30)))
            }

            @Override
            protected String pluginIndexSetting() {
                return null
            }

            @Override
            protected List<URI> repositories() {
                return [repository]
            }
        }, '--plugin-index', index(), 'audit-logging')

        then:
        exit == 0
        err.toString().isEmpty()
        out.toString().contains('Title: Audit Logging Plugin')
        out.toString().contains("* All Versions (${repository}): 5.0.0, 5.1.0")
        out.toString().contains('* License: Apache-2.0')
        out.toString().contains("    implementation 'org.example.plugins:audit-logging:5.1.0'")
        headersAsked.size() == 3
        headersAsked.every { it.find { it.key.equalsIgnoreCase('User-agent') }.value == ['grails-forge'] }
    }

    private String index() {
        return "https://127.0.0.1:${server.address.port}${INDEX_PATH}"
    }

    private HttpsServer start() {
        HttpsServer started = HttpsServer.create(new InetSocketAddress(InetAddress.loopbackAddress, 0), 0)
        started.httpsConfigurator = new HttpsConfigurator(context)
        started.executor = Executors.newCachedThreadPool { Runnable task ->
            Thread thread = new Thread(task)
            thread.daemon = true
            return thread
        }
        started.createContext('/', { HttpExchange exchange ->
            headersAsked << new LinkedHashMap<String, List<String>>(exchange.requestHeaders)
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
        started.start()
        return started
    }

    private static void send(HttpExchange exchange, int status, byte[] body) {
        exchange.sendResponseHeaders(status, body.length)
        exchange.responseBody.write(body)
    }

    private int list(Duration within, String... args) {
        SSLContext trusted = context
        return run(new ListPluginsCommand(generator) {

            @Override
            protected PackArtifacts createPackArtifacts() {
                return new PackArtifacts(PackArtifacts.https(trusted, Duration.ofSeconds(30)))
            }

            @Override
            protected String pluginIndexSetting() {
                return null
            }

            @Override
            protected Duration indexWithin() {
                return within
            }
        }, args)
    }

    private int run(Object command, String... args) {
        out = new StringWriter()
        err = new StringWriter()
        CommandLine commandLine = new CommandLine(command)
        commandLine.setOut(new PrintWriter(out, true))
        commandLine.setErr(new PrintWriter(err, true))
        return commandLine.execute(args)
    }
}
