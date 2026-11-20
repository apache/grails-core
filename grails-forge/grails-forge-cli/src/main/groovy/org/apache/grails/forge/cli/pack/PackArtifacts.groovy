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
import java.nio.file.DirectoryStream
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptorException
import org.apache.grails.forge.core.pack.PackLoader
import org.grails.forge.build.gradle.GradleRepository
import org.grails.forge.feature.FeatureSelectionException
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

    /** How long a repository can leave a request without an answer, or an answer without its next bytes */
    public static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(60)

    /** How long one file can take */
    public static final Duration FILE_TIMEOUT = Duration.ofMinutes(5)

    /** The option that names the repositories to ask */
    public static final String REPOSITORY_OPTION = '--feature-pack-repository'

    // the parts of coordinates as the descriptor of a pack has them, and a version without the '+'
    // a descriptor allows: here it would be a version that is chosen, not the one that was asked for
    private static final Pattern PART = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]*/)
    private static final Pattern SHA256 = Pattern.compile(/[0-9a-fA-F]{64}/)
    private static final Pattern HEXADECIMAL = Pattern.compile(/[0-9a-fA-F]+/)
    private static final Pattern NOT_IN_AN_ADDRESS = Pattern.compile(/[\p{Cc}\p{Cf}\p{Z}]/)
    private static final Pattern UNREADABLE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)
    private static final Pattern USER_IN_ADDRESS = Pattern.compile('(?<=//)[^/?#]*@')
    private static final Pattern AFTER_THE_PATH = Pattern.compile('[?#].*$')
    private static final Pattern HOST_NAME = Pattern.compile(/[A-Za-z0-9]([A-Za-z0-9\-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9\-]*[A-Za-z0-9])?)*/)
    private static final Pattern IPV4 = Pattern.compile(/\d{1,3}(\.\d{1,3}){3}/)
    private static final String BUILD = /-(\d{8}\.\d{6})-(\d{1,9})/
    private static final Pattern BUILD_TIME = Pattern.compile(/\d{8}\.\d{6}/)
    private static final Pattern BUILD_NUMBER = Pattern.compile(/\d{1,9}/)
    private static final String SNAPSHOT = '-SNAPSHOT'
    private static final String METADATA = 'maven-metadata.xml'
    private static final String TEMPORARY_PREFIX = 'grails-forge-pack-'
    private static final String TEMPORARY_SUFFIX = '.jar'
    private static final int MAX_PART = 200
    private static final int MAX_ADDRESS = 2000
    private static final int MAX_HOST = 253
    private static final int MAX_PORT = 65535
    private static final int MAX_REPOSITORY_ECHO = 200
    private static final String HTTPS = 'https'
    private static final String FILE = 'file'
    private static final String LOCALHOST = 'localhost'
    private static final String TIMEOUT = 'timeout'
    private static final String BOM = String.valueOf((char) 0xFEFF)
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15)
    private static final Duration LEFTOVER_AGE = Duration.ofDays(1)
    private static final long MAX_WATCH_MILLIS = 250
    private static final Set<Integer> REDIRECTS = [301, 302, 303, 307, 308] as Set<Integer>
    private static final Set<Integer> NOT_THERE = [404, 410] as Set<Integer>
    private static final Set<Integer> NOT_GIVEN = [401, 403] as Set<Integer>

    private static ScheduledExecutorService watcher

    private final Transport transport
    private final Duration answerTimeout
    private final Duration fileTimeout
    private final PackLoader loader = new PackLoader()
    private boolean swept

    PackArtifacts() {
        this(new HttpTransport(null, ANSWER_TIMEOUT))
    }

    /**
     * @param transport what asks a repository that is not a directory for a file
     */
    PackArtifacts(Transport transport) {
        this(transport, ANSWER_TIMEOUT, FILE_TIMEOUT)
    }

    /**
     * @param transport what asks a repository that is not a directory for a file
     * @param answerTimeout how long a repository can leave a request without an answer, or an answer without its next bytes
     * @param fileTimeout how long one file can take
     */
    PackArtifacts(Transport transport, Duration answerTimeout, Duration fileTimeout) {
        this.transport = transport
        this.answerTimeout = answerTimeout
        this.fileTimeout = fileTimeout
    }

    /**
     * @param context whose certificates are trusted, or {@code null} for those of the JVM
     * @param answerTimeout how long a repository can take to begin its answer
     * @return what asks a repository over https, as the command does
     */
    static Transport https(SSLContext context, Duration answerTimeout) {
        return new HttpTransport(context, answerTimeout)
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
     * <p>The variable is written for the build of the generated application, not for this: an entry of
     * it that a pack cannot be taken from is left out with a warning, and is no mistake of the command.
     * It is read as the generator reads it. An entry with a user or password is left out too: credentials
     * are not sent, and are not repeated in the warning.</p>
     *
     * @param given the addresses that were given for this command
     * @param grailsRepoUrl the value of {@code GRAILS_REPO_URL}, or {@code null}
     * @param warnings takes a line for each entry of the variable that is left out
     * @return the addresses, each of a directory
     * @throws IllegalArgumentException when an address that was given is not one a pack can be taken from
     */
    static List<URI> repositories(List<String> given, String grailsRepoUrl, List<String> warnings) {
        Set<URI> repositories = new LinkedHashSet<>()
        if (given) {
            for (String address : given) {
                repositories << repository(address)
            }
            return repositories.toList()
        }
        int number = 0
        for (String entry : (grailsRepoUrl ?: '').split(';')) {
            String address = entry.trim()
            if (address.isEmpty()) {
                continue
            }
            number++
            try {
                if (address == 'mavenCentral()') {
                    repositories << URI.create(MAVEN_CENTRAL)
                } else if (address == 'mavenLocal()') {
                    repositories << directory(System.getProperty('maven.repo.local') ?: "${System.getProperty('user.home')}/.m2/repository".toString(), address)
                } else if (!isLocal(address)) {
                    repositories << repository(address)
                } else if (address.regionMatches(true, 0, 'file:', 0, 5)) {
                    repositories << directory(Path.of(new URI(address)).toString(), address)
                } else {
                    repositories << directory(address, address)
                }
            } catch (Exception e) {
                warnings << "Entry ${number} of GRAILS_REPO_URL is not asked for the feature pack. ".toString() +
                        (e instanceof IllegalArgumentException && e.message?.startsWith("'") ? e.message : "'${redacted(address)}' is not the address of a repository or of a directory")
            }
        }
        repositories << URI.create(MAVEN_CENTRAL)
        return repositories.toList()
    }

    // as GradleRepository tells a directory from a remote repository in the variable
    private static boolean isLocal(String entry) {
        try {
            URI uri = new URI(entry)
            return uri.scheme == null || FILE.equalsIgnoreCase(uri.scheme) || (uri.scheme.length() == 1 && uri.rawAuthority == null)
        } catch (URISyntaxException ignored) {
            return !GradleRepository.URL_SCHEME_PREFIX.matcher(entry).matches()
        }
    }

    /**
     * @param address the address of a repository: {@code https}, or {@code file:} for a directory
     * @return the address, as that of a directory
     * @throws IllegalArgumentException when it is neither, names a user, a query or a fragment, or has a
     * host or port no request can be made to. The message does not repeat a user or a query.
     */
    static URI repository(String address) {
        if (address == null || address.isEmpty() || address.length() > MAX_ADDRESS || NOT_IN_AN_ADDRESS.matcher(address).find()) {
            throw new IllegalArgumentException("'${redacted(address ?: '')}' is not the address of a repository")
        }
        URI uri
        try {
            uri = new URI(address)
        } catch (URISyntaxException ignored) {
            throw new IllegalArgumentException("'${redacted(address)}' is not the address of a repository")
        }
        if (FILE.equalsIgnoreCase(uri.scheme)) {
            Path path
            try {
                path = Path.of(uri)
            } catch (IllegalArgumentException | FileSystemNotFoundException ignored) {
                throw new IllegalArgumentException("'${redacted(address)}' is not the address of a directory")
            }
            return directory(path.toString(), address)
        }
        if (!HTTPS.equalsIgnoreCase(uri.scheme) || !uri.host) {
            throw new IllegalArgumentException("'${redacted(address)}' is not a repository a feature pack is taken from: the address has to be https, or file: for a directory")
        }
        if (uri.rawUserInfo != null) {
            throw new IllegalArgumentException("'${redacted(address)}' is not a repository a feature pack is taken from: the address has a user or password, and credentials are not sent")
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            throw new IllegalArgumentException("'${redacted(address)}' is not a repository a feature pack is taken from: the address cannot have a query or a fragment")
        }
        if (!canBeAsked(uri)) {
            throw new IllegalArgumentException("'${redacted(address)}' is not a repository a feature pack is taken from: its host or port is not one a request can be made to")
        }
        return uri.rawPath?.endsWith('/') ? uri : URI.create("${uri}/")
    }

    private static URI directory(String path, String given) {
        try {
            String address = Path.of(path).toAbsolutePath().normalize().toUri().toString()
            return URI.create(address.endsWith('/') ? address : address + '/')
        } catch (InvalidPathException ignored) {
            throw new IllegalArgumentException("'${redacted(given)}' is not the address of a directory")
        }
    }

    // what the client of the JDK would refuse with an exception of its own: a port that is none, and a
    // host that is neither a name nor an address
    private static boolean canBeAsked(URI address) {
        String host = address.host
        if (host == null || host.length() > MAX_HOST || address.port == 0 || address.port > MAX_PORT) {
            return false
        }
        return host.startsWith('[') || HOST_NAME.matcher(host).matches()
    }

    // a host of this computer or of the network it is in, when the address says so itself: no name is looked up
    private static boolean isNearby(String host) {
        String name = host.toLowerCase(Locale.ROOT)
        if (name == LOCALHOST || name.endsWith('.' + LOCALHOST)) {
            return true
        }
        boolean literal = name.startsWith('[') || IPV4.matcher(name).matches()
        if (!literal) {
            return false
        }
        try {
            InetAddress address = InetAddress.getByName(name.startsWith('[') ? name.substring(1, name.length() - 1) : name)
            return address.loopbackAddress || address.linkLocalAddress || address.siteLocalAddress || address.anyLocalAddress ||
                    (address instanceof Inet6Address && (address.address[0] & 0xfe) == 0xfc)
        } catch (IOException | RuntimeException ignored) {
            // not an address a request is made to
            return true
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
     * @throws PackArtifactException when no repository gave the jar, the jar is not the one expected, or
     * it holds no pack that can be used. Nothing a repository answers leaves this method as another exception.
     * @throws IllegalArgumentException when the digest that is asked for is not a SHA-256
     */
    Pack resolve(Coordinates coordinates, List<URI> repositories, String sha256) {
        if (sha256 != null && !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("'${echo(sha256)}' is not a SHA-256, which is 64 hexadecimal digits")
        }
        String pinned = sha256?.toLowerCase(Locale.ROOT)
        List<String> tried = []
        for (int index = 0; index < repositories.size(); index++) {
            URI repository = repositories[index]
            String where = shown(repository)
            String why
            try {
                return from(repository, coordinates, pinned)
            } catch (Missing ignored) {
                tried << "${where}: not found".toString()
                continue
            } catch (Local e) {
                throw new PackArtifactException("The feature pack of ${coordinates} was not taken from ${where}: ${e.message}. " +
                        'This is about the disk of this computer, not about the repository')
            } catch (PackDescriptorException e) {
                // the jar is what the repository has, and it is the jar that is wrong: every problem of it is said
                throw new PackArtifactException("The feature pack of ${coordinates} from ${where} cannot be used:\n" +
                        e.problems.collect { String problem -> '  - ' + problem }.join('\n'))
            } catch (NotGiven e) {
                // the jar can be there and not be for whoever asks: asking the next repository would send
                // the coordinates of a plugin that is kept from others to it
                why = "${e.message}: the repository refuses access to the jar, and the search stops there so that the " +
                        'coordinates are sent to no other repository'
            } catch (Refused e) {
                why = e.message
            } catch (IOException e) {
                why = reason(e)
            } catch (Exception e) {
                // an address or an answer the client itself refuses: what it says can be text of the other side
                why = "cannot be asked (${e.class.simpleName})"
            }
            int left = repositories.size() - index - 1
            tried << "${where}: ${why}".toString() + (left > 0 ? " (${left} more ${left == 1 ? 'repository' : 'repositories'} not asked)" : '')
            throw new PackArtifactException("The feature pack of ${coordinates} was not taken from a repository. ${tried.join('; ')}. " +
                    "A repository that fails ends the search; ${REPOSITORY_OPTION} names the repositories to ask")
        }
        throw new PackArtifactException("The feature pack of ${coordinates} was not taken from a repository. ${tried.join('; ')}")
    }

    private Pack from(URI repository, Coordinates coordinates, String pinned) {
        URI directory = repository.resolve("${coordinates.group.replace('.', '/')}/${coordinates.artifact}/${coordinates.version}/")
        String file = fileName(directory, coordinates)
        URI jar = directory.resolve(file)
        Path local = temporary()
        try {
            MessageDigest sha256 = MessageDigest.getInstance('SHA-256')
            MessageDigest sha1 = MessageDigest.getInstance('SHA-1')
            OutputStream sink
            try {
                sink = Files.newOutputStream(local)
            } catch (IOException e) {
                throw new Local("the copy of the jar could not be written (${e.class.simpleName})")
            }
            try (OutputStream kept = new Kept(sink)) {
                copy(jar, repository, MAX_JAR_BYTES, file, kept, [sha256, sha1])
            }
            String digest = HexFormat.of().formatHex(sha256.digest())
            // before the jar is opened
            if (pinned != null && pinned != digest) {
                throw new Refused("checksum mismatch: the sha256 of ${file} is ${digest}, and ${pinned} was asked for")
            }
            String checkedAgainst = checkedAgainst(jar, repository, file, [sha256: digest, sha1: HexFormat.of().formatHex(sha1.digest())])
            Pack pack = loader.fromJar(local, "${coordinates} (${repository})")
            // the digest is of what was copied, and the pack of what was then read from the copy: they are
            // one thing only when the copy is still what was written
            if (digestOf(local) != digest) {
                throw new Local('the copy of the jar was changed while it was read')
            }
            return pack.withArtifact(new PackArtifact(pack.descriptor.name, coordinates.toString(), repository.toString(), file,
                    digest, checkedAgainst, pinned != null))
        } finally {
            try {
                Files.deleteIfExists(local)
            } catch (IOException ignored) {
                // it is deleted when the command ends, or by a later one
            }
        }
    }

    private static String digestOf(Path file) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024]
            int read
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read)
            }
        } catch (IOException e) {
            throw new Local("the copy of the jar could not be read again (${e.class.simpleName})")
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    /**
     * The file the jar is copied to. It is deleted when the pack is read, and when the command ends;
     * what a command that was killed left behind is deleted here a day later.
     */
    private Path temporary() {
        try {
            Path file = Files.createTempFile(TEMPORARY_PREFIX, TEMPORARY_SUFFIX)
            file.toFile().deleteOnExit()
            if (!swept) {
                swept = true
                sweep(file.parent)
            }
            return file
        } catch (IOException e) {
            throw new Local("no temporary file could be made for the jar (${e.class.simpleName})")
        }
    }

    private static void sweep(Path directory) {
        long before = System.currentTimeMillis() - LEFTOVER_AGE.toMillis()
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, TEMPORARY_PREFIX + '*' + TEMPORARY_SUFFIX)) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toMillis() < before) {
                    Files.deleteIfExists(file)
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // what is left stays for the next time
        }
    }

    /**
     * Checks the jar against the first checksum the repository publishes for it. A repository that gave
     * the jar and says of its checksum that it is not there, or not to be had, publishes none.
     *
     * @return the kind of checksum, or {@code null} when the repository publishes none
     */
    private String checkedAgainst(URI jar, URI repository, String file, Map<String, String> digests) {
        for (Map.Entry<String, String> digest : digests.entrySet()) {
            String name = "${file}.${digest.key}"
            ByteArrayOutputStream sink = new ByteArrayOutputStream()
            try {
                copy(URI.create("${jar}.${digest.key}"), repository, MAX_CHECKSUM_BYTES, name, sink, [])
            } catch (Missing | NotGiven ignored) {
                continue
            } catch (Refused e) {
                throw new Refused(e.message.startsWith(name) ? e.message : "${name}: ${e.message}")
            } catch (IOException e) {
                throw new Refused("${name}: ${reason(e)}")
            } catch (Exception e) {
                throw new Refused("${name}: cannot be asked (${e.class.simpleName})")
            }
            String text = new String(sink.toByteArray(), StandardCharsets.UTF_8)
            // some repositories write the name of the file after the checksum, and some tools a mark before it
            String published = (text.startsWith(BOM) ? text.substring(1) : text).trim().split(/\s+/)[0].toLowerCase(Locale.ROOT)
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
            copy(directory.resolve(METADATA), directory, MAX_METADATA_BYTES, METADATA, metadata, [])
        } catch (Missing | NotGiven ignored) {
            return plain
        } catch (IOException e) {
            throw new Refused("${METADATA}: ${reason(e)}")
        }
        String build = build(metadata.toByteArray(), coordinates)
        return build == null ? plain : "${coordinates.artifact}-${build}.jar"
    }

    /**
     * The version of the latest build of the main jar a {@code maven-metadata.xml} names: the version
     * that was asked for with the time and the number of a build in the place of {@code SNAPSHOT}, and
     * nothing else. The metadata has to be that of what was asked for where it says what it is of.
     *
     * @return the version of the build, or {@code null} when the repository keeps the snapshot under the
     * name of the version
     */
    private static String build(byte[] metadata, Coordinates coordinates) {
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
        Map<String, String> asked = [groupId: coordinates.group, artifactId: coordinates.artifact, version: coordinates.version]
        for (Map.Entry<String, String> each : asked.entrySet()) {
            String said = text(root, each.key)
            if (said && said != each.value) {
                throw new Refused("${METADATA} is that of another ${each.key} than the one asked for")
            }
        }
        Element versioning = child(root, 'versioning')
        if (versioning == null) {
            return null
        }
        String release = coordinates.version.substring(0, coordinates.version.length() - SNAPSHOT.length())
        Pattern buildOfIt = Pattern.compile(Pattern.quote(release) + BUILD)
        String latest = null
        String latestTime = null
        long latestNumber = -1
        boolean underTheVersion = false
        Element builds = child(versioning, 'snapshotVersions')
        for (Node node = builds?.firstChild; node != null; node = node.nextSibling) {
            if (!(node instanceof Element) || node.nodeName != 'snapshotVersion' ||
                    text((Element) node, 'extension') != 'jar' || text((Element) node, 'classifier')) {
                continue
            }
            String value = text((Element) node, 'value') ?: ''
            if (value == coordinates.version) {
                underTheVersion = true
                continue
            }
            Matcher matcher = buildOfIt.matcher(value)
            if (!matcher.matches()) {
                throw new Refused("${METADATA} names a build that is not one of this snapshot")
            }
            long number = Long.parseLong(matcher.group(2))
            int order = latestTime == null ? 1 : matcher.group(1) <=> latestTime
            if (order > 0 || (order == 0 && number > latestNumber)) {
                latest = value
                latestTime = matcher.group(1)
                latestNumber = number
            }
        }
        if (latest != null || underTheVersion) {
            return latest
        }
        Element snapshot = child(versioning, 'snapshot')
        String time = snapshot == null ? null : text(snapshot, 'timestamp')
        String number = snapshot == null ? null : text(snapshot, 'buildNumber')
        if (snapshot == null || text(snapshot, 'localCopy') == 'true' || (!time && !number)) {
            return null
        }
        if (!BUILD_TIME.matcher(time ?: '').matches() || !BUILD_NUMBER.matcher(number ?: '').matches()) {
            throw new Refused("${METADATA} names a build that is not one of this snapshot")
        }
        return "${release}-${time}-${number}"
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
     * Copies a file of a repository, and no more of it than the limit. A repository that stops answering
     * is not waited for: the request and each read of the answer have the time of an answer, and the
     * file has its own.
     */
    private void copy(URI address, URI repository, long limit, String name, OutputStream sink, List<MessageDigest> digests) {
        Watch watch = new Watch(answerTimeout, fileTimeout)
        try {
            InputStream input = open(address, repository, watch)
            try {
                watch.reads(input)
                byte[] buffer = new byte[16 * 1024]
                long total = 0
                int read
                while ((read = input.read(buffer)) != -1) {
                    watch.progress()
                    total += read
                    if (total > limit) {
                        throw new Refused("${name} has more than the ${limit} bytes it can have")
                    }
                    sink.write(buffer, 0, read)
                    for (MessageDigest digest : digests) {
                        digest.update(buffer, 0, read)
                    }
                }
            } finally {
                try {
                    input.close()
                } catch (IOException ignored) {
                    // what was read is what counts
                }
            }
        } catch (Exception e) {
            // closing the answer under a read, or interrupting a write of the copy, ends it in whatever way the stream has
            if (watch.end()) {
                throw new Refused(TIMEOUT)
            }
            throw e
        }
        // a read that was cut can also look like the end of the answer
        if (watch.end()) {
            throw new Refused(TIMEOUT)
        }
    }

    private InputStream open(URI address, URI repository, Watch watch) {
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
            if (!canBeAsked(current)) {
                throw new Refused(redirects == 0 ? 'the address has a host or port no request can be made to' :
                        'redirect to an address with a host or port no request can be made to')
            }
            // a repository out there does not send this computer to itself or to the network it is in
            if (redirects > 0 && isNearby(current.host) && !(HTTPS.equalsIgnoreCase(repository.scheme) && repository.host && isNearby(repository.host))) {
                throw new Refused('redirect to an address of this computer or of its network')
            }
            Reply reply = transport.get(current)
            watch.progress()
            if (reply.status == 200 && reply.body != null) {
                if (reply.encoding && !reply.encoding.trim().equalsIgnoreCase('identity')) {
                    reply.close()
                    throw new Refused('the answer is encoded, and only a file as it is was asked for')
                }
                return reply.body
            }
            reply.close()
            if (NOT_THERE.contains(reply.status)) {
                throw new Missing()
            }
            if (NOT_GIVEN.contains(reply.status)) {
                throw new NotGiven(reply.status)
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
            return TIMEOUT
        }
        if (e instanceof SSLException) {
            return "TLS failure (${e.class.simpleName})"
        }
        if (e instanceof ConnectException || e instanceof UnknownHostException || e instanceof NoRouteToHostException) {
            return "no connection (${e.class.simpleName})"
        }
        return "cannot be read (${e.class.simpleName})"
    }

    // text that was given, for a message: without a user, a password or a query of an address, readable and short
    // one rule for what was typed in a message, that of the features: FeatureSelectionException.shown
    private static String echo(String text) {
        return FeatureSelectionException.shown(text)
    }

    // an address that was given, for a message: without its user, password and query, which can be secrets
    private static String redacted(String text) {
        return FeatureSelectionException.shown(AFTER_THE_PATH.matcher(USER_IN_ADDRESS.matcher(text).replaceAll('')).replaceAll(''))
    }

    private static String shown(URI repository) {
        return cut(repository.toString(), MAX_REPOSITORY_ECHO)
    }

    private static String cut(String text, int length) {
        String shown = UNREADABLE.matcher(text).replaceAll('?').replace('@|', '@ |')
        return shown.length() > length ? shown.substring(0, length) + '...' : shown
    }

    private static synchronized ScheduledExecutorService watchdog() {
        if (watcher == null) {
            watcher = Executors.newSingleThreadScheduledExecutor({ Runnable task ->
                Thread thread = new Thread(task, 'grails-forge-pack-timeout')
                thread.daemon = true
                return thread
            } as ThreadFactory)
        }
        return watcher
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
        /** The {@code Content-Encoding} of the body, or {@code null} when it is the file as it is */
        final String encoding

        Reply(int status, String location, InputStream body) {
            this(status, location, body, null)
        }

        Reply(int status, String location, InputStream body, String encoding) {
            this.status = status
            this.location = location
            this.body = body
            this.encoding = encoding
        }

        @Override
        void close() throws IOException {
            body?.close()
        }
    }

    /**
     * Ends a file that takes too long. A read of an answer that does not come cannot be given a time of
     * its own, so the answer is closed under it, and the thread that waits is interrupted, which also
     * ends a request that has no answer yet.
     */
    private static final class Watch {

        private final Thread reader = Thread.currentThread()
        private final long answerNanos
        private final long deadline
        private final ScheduledFuture<?> task
        private volatile long last
        private Closeable answer
        private boolean done
        private boolean expired

        Watch(Duration answerTimeout, Duration fileTimeout) {
            answerNanos = answerTimeout.toNanos()
            last = System.nanoTime()
            deadline = last + fileTimeout.toNanos()
            long every = Math.max(1L, Math.min(MAX_WATCH_MILLIS, answerTimeout.toMillis() >> 2))
            task = watchdog().scheduleWithFixedDelay({ -> check() } as Runnable, every, every, TimeUnit.MILLISECONDS)
        }

        void progress() {
            last = System.nanoTime()
        }

        synchronized void reads(Closeable answer) {
            this.answer = answer
        }

        private synchronized void check() {
            long now = System.nanoTime()
            if (done || (now - deadline < 0 && now - last < answerNanos)) {
                return
            }
            done = true
            expired = true
            try {
                answer?.close()
            } catch (IOException | RuntimeException ignored) {
                // the interrupt ends the read then
            }
            reader.interrupt()
        }

        /**
         * @return whether the file was ended for taking too long
         */
        synchronized boolean end() {
            done = true
            task.cancel(false)
            if (expired) {
                // the interrupt was for the read, not for what the thread does next
                Thread.interrupted()
            }
            return expired
        }
    }

    // the stream of the copy on this computer: what fails here is not the repository
    private static final class Kept extends FilterOutputStream {

        Kept(OutputStream sink) {
            super(sink)
        }

        @Override
        void write(byte[] bytes, int offset, int length) {
            try {
                out.write(bytes, offset, length)
            } catch (IOException e) {
                throw new Local("the copy of the jar could not be written (${e.class.simpleName})")
            }
        }

        @Override
        void close() {
            try {
                out.close()
            } catch (IOException e) {
                throw new Local("the copy of the jar could not be written (${e.class.simpleName})")
            }
        }
    }

    private static final class HttpTransport implements Transport {

        private final SSLContext context
        private final Duration answerTimeout
        private HttpClient client

        HttpTransport(SSLContext context, Duration answerTimeout) {
            this.context = context
            this.answerTimeout = answerTimeout
        }

        @Override
        Reply get(URI address) throws IOException {
            if (client == null) {
                HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(CONNECT_TIMEOUT)
                client = (context == null ? builder : builder.sslContext(context)).build()
            }
            // the file as it is: its bytes are what is hashed
            HttpRequest request = HttpRequest.newBuilder(address).timeout(answerTimeout).header('User-Agent', 'grails-forge')
                    .header('Accept-Encoding', 'identity').GET().build()
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                return new Reply(response.statusCode(), response.headers().firstValue('Location').orElse(null), response.body(),
                        response.headers().firstValue('Content-Encoding').orElse(null))
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

    private static class Refused extends RuntimeException {

        Refused(String reason) {
            super(reason, null, false, false)
        }
    }

    // an answer that says the file is not to be had, which for the jar is a failure and for its checksum is none published
    private static final class NotGiven extends Refused {

        NotGiven(int status) {
            super("HTTP status ${status}")
        }
    }

    private static final class Local extends RuntimeException {

        Local(String reason) {
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
