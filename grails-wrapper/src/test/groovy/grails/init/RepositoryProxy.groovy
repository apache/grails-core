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
package grails.init

import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

/**
 * A proxy on the loopback address that stands in for the remote repositories of the wrapper, Maven Central and
 * the ASF repository. The CONNECT for a host it serves is answered by the proxy itself, which then speaks TLS with
 * a certificate made for those hosts and answers the request from a directory laid out as a Maven repository. A
 * JVM that is given {@link #getSystemProperties} sends its HTTPS requests here and trusts that certificate, and no
 * other. The CONNECT for every other host is refused. A connection carries one request, so every request is
 * recorded.
 */
class RepositoryProxy implements Closeable {

    static final String CENTRAL = 'repo1.maven.org'
    static final String ASF = 'repository.apache.org'

    // where each repository is on its host
    private static final Map<String, String> PATHS = [(CENTRAL): '/maven2/', (ASF): '/content/groups/public/']
    private static final String ALIAS = 'repositories'
    private static final String PASSWORD = 'repositories'
    private static final int LINE_FEED = 10
    private static final int CARRIAGE_RETURN = 13
    // a whole TLS record of application data, 32 bytes that no key decrypts. Bytes that are no record would not do:
    // once a connection is set up, TLS takes any five bytes as the header of a record, and a stream that ends before
    // the length that header gives is read as the end of the connection
    private static final byte[] FORGED_RECORD = ([0x17, 0x03, 0x03, 0x00, 0x20] + [0] * 32) as byte[]

    private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.loopbackAddress)
    private final Map<String, File> served = new ConcurrentHashMap<>()
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>()
    private final Set<String> cutOff = ConcurrentHashMap.newKeySet()
    private final Set<String> brokenTls = ConcurrentHashMap.newKeySet()
    private final List<String> requestedHosts = new CopyOnWriteArrayList<>()
    private final List<String> requests = new CopyOnWriteArrayList<>()
    private final File trustStore
    private final SSLContext context
    private final Thread thread

    /**
     * @param directory where the key of the certificate and the trust store with it are written
     */
    RepositoryProxy(File directory) {
        File keyStore = new File(directory, 'repositories-key.p12')
        createKey(keyStore)
        KeyStore keys = KeyStore.getInstance('PKCS12')
        keyStore.withInputStream { InputStream stream -> keys.load(stream, PASSWORD.toCharArray()) }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
        keyManagers.init(keys, PASSWORD.toCharArray())
        context = SSLContext.getInstance('TLS')
        context.init(keyManagers.keyManagers, null, null)

        KeyStore trusted = KeyStore.getInstance('PKCS12')
        trusted.load(null, null)
        trusted.setCertificateEntry(ALIAS, keys.getCertificate(ALIAS))
        trustStore = new File(directory, 'repositories-trust.p12')
        trustStore.withOutputStream { OutputStream stream -> trusted.store(stream, PASSWORD.toCharArray()) }

        thread = new Thread({ acceptConnections() }, 'repository-proxy')
        thread.daemon = true
        thread.start()
    }

    /**
     * Serves a host from a directory laid out as a Maven repository. A host that is not served is refused.
     */
    RepositoryProxy serve(String host, File repository) {
        served.put(host, repository)
        this
    }

    /**
     * Answers the request for a file of a repository with a status and no content.
     *
     * @param path the path of the file in the repository
     */
    RepositoryProxy answer(String host, String path, int status) {
        statuses.put(key(host, path), status)
        this
    }

    /**
     * Answers the request for a file of a repository with the length of the file and half of its bytes.
     *
     * @param path the path of the file in the repository
     */
    RepositoryProxy cutOff(String host, String path) {
        cutOff.add(key(host, path))
        this
    }

    /**
     * Answers the request for a file of a repository with the length of the file and half of its bytes, and then
     * writes a whole TLS record that was not encrypted with the key of the connection to the connection under TLS:
     * the TLS of the client fails on it, before its HTTP stream can find that the content ended early.
     *
     * @param path the path of the file in the repository
     */
    RepositoryProxy breakTls(String host, String path) {
        brokenTls.add(key(host, path))
        this
    }

    /**
     * @return the hosts that were asked for, in the order of the requests
     */
    List<String> getRequestedHosts() {
        new ArrayList<>(requestedHosts)
    }

    /**
     * @return each request a host that is served got, as the host and the path in its repository
     */
    List<String> getRequests() {
        new ArrayList<>(requests)
    }

    /**
     * @return the system properties that send the HTTPS requests of a JVM to this proxy and trust its certificate
     */
    Map<String, String> getSystemProperties() {
        ['https.proxyHost': socket.inetAddress.hostAddress, 'https.proxyPort': socket.localPort as String,
         'javax.net.ssl.trustStore': trustStore.absolutePath, 'javax.net.ssl.trustStoreType': 'PKCS12',
         'javax.net.ssl.trustStorePassword': PASSWORD]
    }

    private static String key(String host, String path) {
        "${host} ${path}".toString()
    }

    private static void createKey(File keyStore) {
        String keytool = new File(System.getProperty('java.home'), 'bin/keytool').path
        Process process = new ProcessBuilder(keytool, '-genkeypair', '-alias', ALIAS, '-keyalg', 'EC', '-groupname', 'secp256r1',
                '-validity', '2', '-dname', "CN=${CENTRAL}".toString(), '-ext', "SAN=dns:${CENTRAL},dns:${ASF}".toString(),
                '-keystore', keyStore.path, '-storetype', 'PKCS12', '-storepass', PASSWORD, '-keypass', PASSWORD)
                .redirectErrorStream(true)
                .start()
        String output = process.inputStream.text
        assert process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0: output
    }

    private void acceptConnections() {
        while (!socket.closed) {
            try {
                socket.accept().withCloseable { Socket connection ->
                    connection.soTimeout = 60_000
                    connect(connection)
                }
            } catch (IOException ignored) {
                // the socket was closed, or a client went away
            }
        }
    }

    private void connect(Socket connection) {
        // CONNECT repo1.maven.org:443 HTTP/1.1
        String requestLine = readHead(connection.inputStream)
        if (!requestLine) {
            return
        }
        String host = requestLine.split(' ')[1].split(':')[0]
        requestedHosts.add(host)
        OutputStream out = connection.outputStream
        if (!served.containsKey(host)) {
            write(out, 'HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n', null)
            return
        }
        write(out, 'HTTP/1.1 200 Connection established\r\n\r\n', null)
        SSLSocket tls = (SSLSocket) context.socketFactory.createSocket(connection, host, connection.port, true)
        tls.useClientMode = false
        tls.withCloseable { respond(host, tls, connection) }
    }

    private void respond(String host, SSLSocket tls, Socket connection) {
        // GET /maven2/org/apache/grails/grails-cli/9.0.0/grails-cli-9.0.0-all.jar HTTP/1.1
        String requestLine = readHead(tls.inputStream)
        if (!requestLine) {
            return
        }
        String prefix = PATHS[host]
        String requested = requestLine.split(' ')[1]
        String path = requested.startsWith(prefix) ? requested.substring(prefix.length()) : requested
        requests.add(key(host, path))
        Integer status = statuses[key(host, path)]
        File file = new File(served[host], path)
        if (status != null) {
            write(tls.outputStream, "HTTP/1.1 ${status} Answered\r\nContent-Length: 0\r\nConnection: close\r\n\r\n", null)
        } else if (!requested.startsWith(prefix) || !file.isFile()) {
            write(tls.outputStream, 'HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n', null)
        } else {
            byte[] content = file.bytes
            boolean half = cutOff.contains(key(host, path)) || brokenTls.contains(key(host, path))
            byte[] sent = half ? Arrays.copyOf(content, content.length.intdiv(2) as int) : content
            write(tls.outputStream, "HTTP/1.1 200 OK\r\nContent-Length: ${content.length}\r\nConnection: close\r\n\r\n", sent)
            if (brokenTls.contains(key(host, path))) {
                write(connection.outputStream, '', FORGED_RECORD)
            }
        }
    }

    private static void write(OutputStream out, String head, byte[] content) {
        out.write(head.getBytes(StandardCharsets.ISO_8859_1))
        if (content != null) {
            out.write(content)
        }
        out.flush()
    }

    /**
     * Reads the request line and the headers after it, a byte at a time, so that nothing of what follows them
     * is read.
     *
     * @return the request line, or null when the client sent nothing
     */
    private static String readHead(InputStream input) {
        String requestLine = readLine(input)
        String header = requestLine
        while (header) {
            header = readLine(input)
        }
        requestLine
    }

    private static String readLine(InputStream input) {
        ByteArrayOutputStream line = new ByteArrayOutputStream()
        int read
        while ((read = input.read()) != -1 && read != LINE_FEED) {
            if (read != CARRIAGE_RETURN) {
                line.write(read)
            }
        }
        read == -1 && line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1.name())
    }

    @Override
    void close() {
        socket.close()
        thread.join()
    }
}
