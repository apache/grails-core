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

import groovy.transform.CompileStatic
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.regex.Pattern
import javax.net.ssl.SSLException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackLoader
import org.grails.forge.build.gradle.GradleRepository
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

/**
 * Takes the feature pack of a plugin from a Maven repository by the coordinates of the plugin: the main
 * jar of that one version and nothing else of it. No dependency of the plugin is resolved, nothing of
 * the jar is loaded as code, and the jar is a temporary file that is deleted once the pack is read.
 *
 * <p>This is in the command line and not in the generator, so that the hosted Forge, which is built on
 * the generator alone, has no way to fetch a pack.</p>
 *
 * @since 9.0
 */
@CompileStatic
class PackArtifacts {

    /** The most bytes the jar of a plugin can have */
    public static final long MAX_JAR_BYTES = 64L * 1024 * 1024

    /** The most bytes the {@code maven-metadata.xml} of a snapshot can have */
    public static final int MAX_METADATA_BYTES = 256 * 1024

    /** The most bytes a checksum file can have */
    public static final int MAX_CHECKSUM_BYTES = 1024

    /** The most redirects that are followed for one file */
    public static final int MAX_REDIRECTS = 5

    /** Maven Central, as Gradle names it */
    public static final String MAVEN_CENTRAL = 'https://repo.maven.apache.org/maven2/'

    // the parts of coordinates as the descriptor of a pack has them, and a version without the '+'
    // a descriptor allows: here it would be a version that is chosen, not the one that was asked for
    private static final Pattern PART = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]*/)
    private static final Pattern SHA256 = Pattern.compile(/[0-9a-fA-F]{64}/)
    private static final Pattern HEXADECIMAL = Pattern.compile(/[0-9a-fA-F]+/)
    private static final Pattern NOT_IN_AN_ADDRESS = Pattern.compile(/[\p{Cc}\p{Cf}\p{Z}]/)
    private static final Pattern UNREADABLE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]/)
    private static final String SNAPSHOT = '-SNAPSHOT'
    private static final String METADATA = 'maven-metadata.xml'
    private static final int MAX_PART = 200
    private static final int MAX_ADDRESS = 2000
    private static final int MAX_ECHO = 80
    private static final String HTTPS = 'https'
    private static final String FILE = 'file'
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15)
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60)
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5)
    private static final Set<Integer> REDIRECTS = [301, 302, 303, 307, 308] as Set<Integer>

    private final Transport transport
    private final PackLoader loader = new PackLoader()

    PackArtifacts() {
        this(new HttpTransport())
    }

    /**
     * @param transport what asks a repository that is not a directory for a file
     */
    PackArtifacts(Transport transport) {
        this.transport = transport
    }

    /**
     * @param feature one of the features that were asked for
     * @return whether it is the coordinates of a plugin and not the name of a feature, which has no colon
     */
    static boolean isCoordinates(String feature) {
        return feature.contains(':')
    }

    /**
     * @param text coordinates as {@code group:artifact:version}
     * @return the coordinates
     * @throws IllegalArgumentException when the text is not the coordinates of one version
     */
    static Coordinates coordinates(String text) {
        List<String> parts = text.split(':', -1).toList()
        if (parts.size() != 3) {
            throw new IllegalArgumentException("'${echo(text)}' is not the coordinates of a plugin: they are group:artifact:version")
        }
        for (String part : parts) {
            if (part.length() > MAX_PART || !PART.matcher(part).matches()) {
                throw new IllegalArgumentException("'${echo(text)}' is not the coordinates of a plugin: each of group, artifact and " +
                        'version starts with a letter or digit and has letters, digits, dots, hyphens and underscores, and the version is ' +
                        'one version, not a range and not one with a +')
            }
        }
        if (parts[0].contains('..') || parts[0].endsWith('.')) {
            throw new IllegalArgumentException("'${echo(text)}' is not the coordinates of a plugin: the group has an empty part")
        }
        String version = parts[2]
        if (version.startsWith('latest.') || version == 'LATEST' || version == 'RELEASE') {
            throw new IllegalArgumentException("'${echo(text)}' does not name one version: the feature pack of a plugin is taken from the version that is asked for")
        }
        return new Coordinates(parts[0], parts[1], version)
    }

    /**
     * @param text {@code group:artifact:version=sha256}
     * @return the coordinates as text and the digest in lowercase
     * @throws IllegalArgumentException when the text is not that
     */
    static Map.Entry<String, String> digest(String text) {
        int at = text.indexOf('=')
        if (at < 0) {
            throw new IllegalArgumentException("'${echo(text)}' is not group:artifact:version=sha256")
        }
        String digest = text.substring(at + 1)
        if (!SHA256.matcher(digest).matches()) {
            throw new IllegalArgumentException("'${echo(text)}' does not end with a SHA-256, which is 64 hexadecimal digits")
        }
        return new AbstractMap.SimpleImmutableEntry<String, String>(coordinates(text.substring(0, at)).toString(), digest.toLowerCase(Locale.ROOT))
    }

    /**
     * The repositories a pack is looked for in. Those that are given are the only ones when there are
     * any, so that the coordinates of a private plugin are not sent anywhere else. Without them it is the
     * repositories of {@code GRAILS_REPO_URL}, in its order, and then Maven Central.
     *
     * @param given the addresses that were given for this command
     * @param grailsRepoUrl the value of {@code GRAILS_REPO_URL}, or {@code null}
     * @return the addresses, each of a directory
     * @throws IllegalArgumentException when an address is not one a pack can be taken from
     */
    static List<URI> repositories(List<String> given, String grailsRepoUrl) {
        Set<URI> repositories = new LinkedHashSet<>()
        if (given) {
            for (String address : given) {
                repositories << repository(address)
            }
            return repositories.toList()
        }
        for (String entry : (grailsRepoUrl ?: '').split(';')) {
            String address = entry.trim()
            if (address.isEmpty()) {
                continue
            }
            if (address == 'mavenCentral()') {
                repositories << URI.create(MAVEN_CENTRAL)
            } else if (address == 'mavenLocal()') {
                repositories << directory(System.getProperty('maven.repo.local') ?: "${System.getProperty('user.home')}/.m2/repository".toString(), address)
            } else if (GradleRepository.URL_SCHEME_PREFIX.matcher(address).matches() || address.toLowerCase(Locale.ROOT).startsWith('file:')) {
                repositories << repository(address)
            } else {
                repositories << directory(address, address)
            }
        }
        repositories << URI.create(MAVEN_CENTRAL)
        return repositories.toList()
    }

    /**
     * @param address the address of a repository: {@code https}, or {@code file:} for a directory
     * @return the address, as that of a directory
     * @throws IllegalArgumentException when it is neither, or names a user, a query or a fragment
     */
    static URI repository(String address) {
        if (address == null || address.isEmpty() || address.length() > MAX_ADDRESS || NOT_IN_AN_ADDRESS.matcher(address).find()) {
            throw new IllegalArgumentException("'${echo(address ?: '')}' is not the address of a repository")
        }
        URI uri
        try {
            uri = new URI(address)
        } catch (URISyntaxException ignored) {
            throw new IllegalArgumentException("'${echo(address)}' is not the address of a repository")
        }
        if (FILE.equalsIgnoreCase(uri.scheme)) {
            Path path
            try {
                path = Path.of(uri)
            } catch (IllegalArgumentException | FileSystemNotFoundException ignored) {
                throw new IllegalArgumentException("'${echo(address)}' is not the address of a directory")
            }
            return directory(path.toString(), address)
        }
        if (!HTTPS.equalsIgnoreCase(uri.scheme) || !uri.host) {
            throw new IllegalArgumentException("'${echo(address)}' is not a repository a feature pack is taken from: the address has to be https, or file: for a directory")
        }
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            throw new IllegalArgumentException("'${echo(address)}' is not a repository a feature pack is taken from: the address cannot have a user, a query or a fragment")
        }
        return uri.rawPath?.endsWith('/') ? uri : URI.create("${uri}/")
    }

    private static URI directory(String path, String given) {
        try {
            String address = Path.of(path).toAbsolutePath().normalize().toUri().toString()
            return URI.create(address.endsWith('/') ? address : address + '/')
        } catch (InvalidPathException ignored) {
            throw new IllegalArgumentException("'${echo(given)}' is not the address of a directory")
        }
    }

    /**
     * Takes the pack of a plugin from the first of the repositories that has the jar. A repository that
     * does not have it is passed over; one that fails in another way ends the search, since a jar of the
     * same name somewhere else is not known to be the one that was meant.
     *
     * @param coordinates the coordinates of the plugin
     * @param repositories where to look, in this order
     * @param sha256 the digest the jar has to have, or {@code null} when none was asked for
     * @return the pack, with the jar it was read from
     * @throws PackArtifactException when no repository gave the jar, or the jar is not the one expected
     * @throws org.apache.grails.forge.core.pack.PackDescriptorException when the jar holds no pack that can be used
     */
    Pack resolve(Coordinates coordinates, List<URI> repositories, String sha256) {
        List<String> tried = []
        for (int index = 0; index < repositories.size(); index++) {
            URI repository = repositories[index]
            String why
            try {
                return from(repository, coordinates, sha256)
            } catch (Missing ignored) {
                tried << "${repository}: not found".toString()
                continue
            } catch (Refused e) {
                why = e.message
            } catch (IOException e) {
                why = reason(e)
            }
            int left = repositories.size() - index - 1
            tried << "${repository}: ${why}".toString() + (left > 0 ? " (${left} more ${left == 1 ? 'repository' : 'repositories'} not asked)" : '')
            break
        }
        throw new PackArtifactException("The feature pack of ${coordinates} was not taken from a repository. ${tried.join('; ')}")
    }

    private Pack from(URI repository, Coordinates coordinates, String pinned) {
        URI directory = repository.resolve("${coordinates.group.replace('.', '/')}/${coordinates.artifact}/${coordinates.version}/")
        String file = fileName(directory, coordinates)
        URI jar = directory.resolve(file)
        Path local = Files.createTempFile('grails-forge-pack', '.jar')
        try {
            MessageDigest sha256 = MessageDigest.getInstance('SHA-256')
            MessageDigest sha1 = MessageDigest.getInstance('SHA-1')
            try (OutputStream sink = Files.newOutputStream(local)) {
                copy(jar, MAX_JAR_BYTES, file, sink, [sha256, sha1])
            }
            String digest = HexFormat.of().formatHex(sha256.digest())
            // before the jar is opened
            if (pinned != null && pinned != digest) {
                throw new Refused("checksum mismatch: the sha256 of ${file} is ${digest}, and ${pinned} was asked for")
            }
            String checkedAgainst = checkedAgainst(jar, file, [sha256: digest, sha1: HexFormat.of().formatHex(sha1.digest())])
            Pack pack = loader.fromJar(local, "${coordinates} (${repository})")
            return pack.withArtifact(new PackArtifact(pack.descriptor.name, coordinates.toString(), repository.toString(), file,
                    digest, checkedAgainst, pinned != null))
        } finally {
            Files.deleteIfExists(local)
        }
    }

    /**
     * Checks the jar against the first checksum the repository publishes for it.
     *
     * @return the kind of checksum, or {@code null} when the repository publishes none
     */
    private String checkedAgainst(URI jar, String file, Map<String, String> digests) {
        for (Map.Entry<String, String> digest : digests.entrySet()) {
            String name = "${file}.${digest.key}"
            ByteArrayOutputStream sink = new ByteArrayOutputStream()
            try {
                copy(URI.create("${jar}.${digest.key}"), MAX_CHECKSUM_BYTES, name, sink, [])
            } catch (Missing ignored) {
                continue
            }
            // some repositories write the name of the file after the checksum
            String published = new String(sink.toByteArray(), StandardCharsets.ISO_8859_1).trim().split(/\s+/)[0].toLowerCase(Locale.ROOT)
            if (!HEXADECIMAL.matcher(published).matches() || published.length() != digest.value.length()) {
                throw new Refused("${name} of the repository is not a ${digest.key}")
            }
            if (published != digest.value) {
                throw new Refused("checksum mismatch: the ${digest.key} of ${file} is ${digest.value}, and the repository publishes ${published}")
            }
            return digest.key
        }
        return null
    }

    /**
     * The name of the jar. That of a snapshot is the name of its latest build, which the
     * {@code maven-metadata.xml} of the version gives; a repository without one, such as the local
     * repository of Maven, keeps the jar under the name of the version.
     */
    private String fileName(URI directory, Coordinates coordinates) {
        String plain = "${coordinates.artifact}-${coordinates.version}.jar"
        if (!coordinates.snapshot) {
            return plain
        }
        ByteArrayOutputStream metadata = new ByteArrayOutputStream()
        try {
            copy(directory.resolve(METADATA), MAX_METADATA_BYTES, METADATA, metadata, [])
        } catch (Missing ignored) {
            return plain
        }
        String build = build(metadata.toByteArray())
        if (build == null) {
            return plain
        }
        String release = coordinates.version.substring(0, coordinates.version.length() - SNAPSHOT.length())
        if (build.length() > MAX_PART || !PART.matcher(build).matches() || !build.startsWith(release + '-')) {
            throw new Refused("${METADATA} names a build that is not one of this snapshot")
        }
        return "${coordinates.artifact}-${build}.jar"
    }

    /**
     * @return the version of the latest build of the main jar a {@code maven-metadata.xml} names, or
     * {@code null} when it names none
     */
    private static String build(byte[] metadata) {
        Element root
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
            // neither a document type nor anything from outside the document is read
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
            factory.setXIncludeAware(false)
            factory.setExpandEntityReferences(false)
            DocumentBuilder builder = factory.newDocumentBuilder()
            builder.setErrorHandler(new Strict())
            root = builder.parse(new ByteArrayInputStream(metadata)).documentElement
        } catch (SAXException | ParserConfigurationException | IOException ignored) {
            throw new Refused("${METADATA} cannot be read")
        }
        Element versioning = child(root, 'versioning')
        if (versioning == null) {
            return null
        }
        Element builds = child(versioning, 'snapshotVersions')
        if (builds != null) {
            for (Node node = builds.firstChild; node != null; node = node.nextSibling) {
                if (node instanceof Element && node.nodeName == 'snapshotVersion' &&
                        text((Element) node, 'extension') == 'jar' && !text((Element) node, 'classifier')) {
                    return text((Element) node, 'value')
                }
            }
        }
        Element snapshot = child(versioning, 'snapshot')
        String version = text(root, 'version')
        if (snapshot == null || text(snapshot, 'localCopy') == 'true' || !text(snapshot, 'timestamp') || !text(snapshot, 'buildNumber') ||
                version == null || !version.endsWith(SNAPSHOT)) {
            return null
        }
        return "${version.substring(0, version.length() - SNAPSHOT.length())}-${text(snapshot, 'timestamp')}-${text(snapshot, 'buildNumber')}"
    }

    private static Element child(Element parent, String name) {
        for (Node node = parent.firstChild; node != null; node = node.nextSibling) {
            if (node instanceof Element && node.nodeName == name) {
                return (Element) node
            }
        }
        return null
    }

    private static String text(Element parent, String name) {
        return child(parent, name)?.textContent?.trim()
    }

    /**
     * Copies a file of a repository, and no more of it than the limit.
     */
    private void copy(URI address, long limit, String name, OutputStream sink, List<MessageDigest> digests) {
        long end = System.nanoTime() + DOWNLOAD_TIMEOUT.toNanos()
        try (InputStream input = open(address)) {
            byte[] buffer = new byte[16 * 1024]
            long total = 0
            int read
            while ((read = input.read(buffer)) != -1) {
                total += read
                if (total > limit) {
                    throw new Refused("${name} has more than the ${limit} bytes it can have")
                }
                if (System.nanoTime() > end) {
                    throw new Refused('timeout')
                }
                sink.write(buffer, 0, read)
                for (MessageDigest digest : digests) {
                    digest.update(buffer, 0, read)
                }
            }
        }
    }

    private InputStream open(URI address) {
        if (FILE.equalsIgnoreCase(address.scheme)) {
            Path file = Path.of(address)
            if (!Files.isRegularFile(file)) {
                throw new Missing()
            }
            return Files.newInputStream(file)
        }
        URI current = address
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            // every address that is asked, the first and each one a repository sends on to
            if (!HTTPS.equalsIgnoreCase(current.scheme) || !current.host || current.rawUserInfo != null) {
                throw new Refused(redirects == 0 ? 'the address is not https' : 'redirect to an address that is not https')
            }
            Reply reply = transport.get(current)
            if (reply.status == 200 && reply.body != null) {
                return reply.body
            }
            reply.close()
            if (reply.status == 404 || reply.status == 410) {
                throw new Missing()
            }
            if (!REDIRECTS.contains(reply.status)) {
                throw new Refused("HTTP status ${reply.status}")
            }
            if (!reply.location) {
                throw new Refused('redirect without an address')
            }
            try {
                current = current.resolve(new URI(reply.location))
            } catch (URISyntaxException ignored) {
                throw new Refused('redirect to an address that cannot be read')
            }
        }
        throw new Refused("more than ${MAX_REDIRECTS} redirects")
    }

    // what an exception says can be text of the other side: only its kind is told
    private static String reason(IOException e) {
        if (e instanceof HttpTimeoutException || e instanceof SocketTimeoutException) {
            return 'timeout'
        }
        if (e instanceof SSLException) {
            return "TLS failure (${e.class.simpleName})"
        }
        if (e instanceof ConnectException || e instanceof UnknownHostException || e instanceof NoRouteToHostException) {
            return "no connection (${e.class.simpleName})"
        }
        return "cannot be read (${e.class.simpleName})"
    }

    private static String echo(String text) {
        String shown = UNREADABLE.matcher(text).replaceAll('?').replace('@|', '@ |')
        return shown.length() > MAX_ECHO ? shown.substring(0, MAX_ECHO) + '...' : shown
    }

    /**
     * The coordinates of one version of a plugin.
     */
    static final class Coordinates {

        final String group
        final String artifact
        final String version

        private Coordinates(String group, String artifact, String version) {
            this.group = group
            this.artifact = artifact
            this.version = version
        }

        boolean isSnapshot() {
            return version.endsWith(SNAPSHOT)
        }

        @Override
        String toString() {
            return "${group}:${artifact}:${version}"
        }
    }

    /**
     * Asks a repository for a file. A redirect is not followed here: it is an answer like any other, so
     * that where it leads is looked at before it is asked.
     */
    interface Transport {

        /**
         * @param address an https address
         * @return the answer, which the caller closes
         */
        Reply get(URI address) throws IOException
    }

    /**
     * The answer of a repository.
     */
    static final class Reply implements Closeable {

        final int status
        final String location
        final InputStream body

        Reply(int status, String location, InputStream body) {
            this.status = status
            this.location = location
            this.body = body
        }

        @Override
        void close() throws IOException {
            body?.close()
        }
    }

    private static final class HttpTransport implements Transport {

        private HttpClient client

        @Override
        Reply get(URI address) throws IOException {
            if (client == null) {
                client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(CONNECT_TIMEOUT).build()
            }
            HttpRequest request = HttpRequest.newBuilder(address).timeout(REQUEST_TIMEOUT).header('User-Agent', 'grails-forge').GET().build()
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                return new Reply(response.statusCode(), response.headers().firstValue('Location').orElse(null), response.body())
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt()
                throw new InterruptedIOException()
            }
        }
    }

    private static final class Missing extends RuntimeException {

        Missing() {
            super('not found', null, false, false)
        }
    }

    private static final class Refused extends RuntimeException {

        Refused(String reason) {
            super(reason, null, false, false)
        }
    }

    private static final class Strict implements ErrorHandler {

        @Override
        void warning(SAXParseException exception) {
        }

        @Override
        void error(SAXParseException exception) throws SAXException {
            throw exception
        }

        @Override
        void fatalError(SAXParseException exception) throws SAXException {
            throw exception
        }
    }
}
