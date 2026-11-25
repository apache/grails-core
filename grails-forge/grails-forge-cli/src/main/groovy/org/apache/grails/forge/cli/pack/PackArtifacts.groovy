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
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader
import org.apache.grails.forge.core.pack.Pack
import org.apache.grails.forge.core.pack.PackArtifact
import org.apache.grails.forge.core.pack.PackDescriptorException
import org.apache.grails.forge.core.pack.PackLoader
import org.grails.forge.build.gradle.GradleRepository
import org.grails.forge.feature.FeatureSelectionException

/**
 * Takes the feature pack of a plugin from a Maven repository by the coordinates of the plugin: the main
 * jar of that one version and nothing else of it. No dependency of the plugin is resolved, nothing of
 * the jar is loaded as code, and the jar is held in memory, where the bytes that are hashed are the
 * bytes the pack is read from.
 *
 * <p>A jar that was fetched from a repository of the network is kept, by its SHA-256, in
 * {@code .grails/forge/packs} of the home of the user ({@link PackJarCache}), when this was told a home.
 * A jar is taken from there only by its digest: when the digest that is asked for is known, the copy is
 * read in the place of a request, and its bytes are hashed again. Without a digest every jar is fetched,
 * a release as much as a snapshot, since what a repository has under coordinates is what it has today;
 * only when nothing is asked of the network is the jar that was last fetched from a repository for the
 * coordinates used, and the command says so. A jar of a repository that is a directory is read from the
 * directory every time, and is not kept.</p>
 *
 * <p>This is in the command line and not in the generator, so that the hosted Forge, which is built on
 * the generator alone, has no way to fetch a pack.</p>
 *
 * @since 9.0
 */
@CompileStatic
class PackArtifacts {

    /** The most bytes the jar of a plugin can have */
    public static final int MAX_JAR_BYTES = 64 * 1024 * 1024

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

    /** The most lines that say which entries of {@code GRAILS_REPO_URL} are not asked */
    public static final int MAX_WARNINGS = 5

    // the parts of coordinates as the descriptor of a pack has them, and a version without the '+'
    // a descriptor allows: here it would be a version that is chosen, not the one that was asked for
    private static final Pattern PART = Pattern.compile(/[A-Za-z0-9][A-Za-z0-9_.\-]*/)
    private static final Pattern SHA256 = Pattern.compile(/[0-9a-fA-F]{64}/)
    private static final Pattern HEXADECIMAL = Pattern.compile(/[0-9a-fA-F]+/)
    private static final Pattern NOT_IN_AN_ADDRESS = Pattern.compile(/[\p{Cc}\p{Cf}\p{Z}]/)
    private static final Pattern NOT_ASCII = Pattern.compile(/[^\x00-\x7F]/)
    private static final Pattern UNREADABLE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)
    private static final Pattern HOST_NAME = Pattern.compile(/[A-Za-z0-9]([A-Za-z0-9\-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9\-]*[A-Za-z0-9])?)*/)
    private static final Pattern NUMBER = Pattern.compile(/(?i)0x[0-9a-f]*|[0-9]+/)
    private static final Pattern PART_OF_IPV4 = Pattern.compile(/0|[1-9][0-9]{0,2}/)
    private static final Pattern IPV6 = Pattern.compile(/\[[0-9A-Fa-f:.]+\]/)
    private static final Set<String> NAMES_OF_THIS_COMPUTER = ['localhost', 'localhost.localdomain', 'ip6-localhost', 'ip6-loopback'] as Set<String>
    private static final String BUILD = /-(\d{8}\.\d{6})-(\d{1,9})/
    private static final Pattern BUILD_NUMBER = Pattern.compile(/\d{1,9}/)
    private static final DateTimeFormatter BUILD_TIME = DateTimeFormatter.ofPattern('uuuuMMdd.HHmmss').withResolverStyle(ResolverStyle.STRICT)
    private static final String SNAPSHOT = '-SNAPSHOT'
    private static final String METADATA = 'maven-metadata.xml'
    private static final String METADATA_ROOT = 'metadata'
    private static final String BUILD_OF_A_FILE = 'metadata/versioning/snapshotVersions/snapshotVersion'
    private static final String LATEST_BUILD = 'metadata/versioning/snapshot'
    private static final Set<String> READ_OF_METADATA = ['metadata/groupId', 'metadata/artifactId', 'metadata/version',
                                                         LATEST_BUILD + '/timestamp', LATEST_BUILD + '/buildNumber', LATEST_BUILD + '/localCopy',
                                                         BUILD_OF_A_FILE + '/extension', BUILD_OF_A_FILE + '/classifier', BUILD_OF_A_FILE + '/value'] as Set<String>
    private static final int MAX_METADATA_DEPTH = 8
    private static final int MAX_METADATA_ELEMENTS = 20000
    private static final int MAX_PART = 200
    private static final int MAX_ADDRESS = 2000
    private static final int MAX_HOST = 253
    private static final int MAX_PORT = 65535
    private static final int MAX_REPOSITORY_ECHO = 200
    private static final int MAX_ENTRIES_NAMED = 3
    private static final String HTTPS = 'https'
    private static final String FILE = 'file'
    private static final String TIMEOUT = 'timeout'
    private static final String BOM = String.valueOf((char) 0xFEFF)
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15)
    private static final long MAX_WATCH_MILLIS = 250
    private static final Set<Integer> REDIRECTS = [301, 302, 303, 307, 308] as Set<Integer>
    private static final Set<Integer> NOT_THERE = [404, 410] as Set<Integer>
    private static final Set<Integer> NOT_GIVEN = [401, 403] as Set<Integer>

    private static ScheduledExecutorService watcher
    private static volatile Consumer<URI> beforeRequest

    private final Transport transport
    private final Duration answerTimeout
    private final Duration fileTimeout
    private final PackLoader loader = new PackLoader()

    /** Whether nothing is asked of the network: only a directory, or a jar that is kept on this computer, gives a file then */
    boolean offline

    /** Whether every jar is fetched, whatever copy of it is kept on this computer */
    boolean refresh

    // the jars that are kept on this computer, or null when none is read or kept
    private PackJarCache kept
    // what is said of each jar that was taken from its copy, by its digest
    private final Map<String, String> copiesUsed = new HashMap<>()

    /**
     * What a command asks with: over https, with the certificates the JVM trusts, and with the jars
     * kept under the home of the user.
     */
    PackArtifacts() {
        this(new HttpTransport(null, ANSWER_TIMEOUT))
        String home = System.getProperty('user.home')
        if (home) {
            keepingJarsUnder(Path.of(home))
        }
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
     * @param home the home of the user, under which the jars that are fetched are kept, in
     * {@code .grails/forge/packs}, or {@code null} for no copies
     * @return this
     */
    PackArtifacts keepingJarsUnder(Path home) {
        kept = home == null ? null : new PackJarCache(home)
        return this
    }

    /**
     * @param sha256 the SHA-256 of the jar of a pack {@link #resolve} gave
     * @return what is said of the jar when it was not fetched and is the copy that is kept on this
     * computer, as a sentence without its first capital and its full stop; {@code null} for a jar
     * that was fetched or read from a directory
     */
    String copyUsed(String sha256) {
        return copiesUsed.get(sha256)
    }

    /**
     * @return what there is to say about the copies of jars that were read, each once
     */
    List<String> takeWarnings() {
        return kept == null ? Collections.<String>emptyList() : kept.takeWarnings()
    }

    /**
     * Has every request this class makes of a server announced before it is made, to code that is
     * part of the program: the tests of this module refuse a request to another computer with it. A
     * {@link RequestRefused} of what is told is not taken for a failure of a repository, and ends the
     * command. Nothing a user can set reaches this.
     *
     * @param before is told the address of each request, or {@code null} for nothing
     */
    static void beforeEachRequest(Consumer<URI> before) {
        beforeRequest = before
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
     * Of what the generator reads in it, this takes {@code mavenLocal()}, {@code mavenCentral()}, an
     * https address and a directory by its absolute path or {@code file:} address. A path that is not
     * absolute is one of the generated application for the generator and Gradle; here it would be one
     * of wherever the command runs, where anybody's directory of that name would give the pack, so it is
     * left out. An entry with a user or password is left out too: credentials are not sent, and no
     * warning repeats an address as it was given.</p>
     *
     * @param given the addresses that were given for this command
     * @param grailsRepoUrl the value of {@code GRAILS_REPO_URL}, or {@code null}
     * @param warnings takes the lines that say which entries of the variable are left out: at most
     * {@link #MAX_WARNINGS} and one that counts the rest, with the entries that are left out for one
     * reason on one line
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
        Map<String, List<Integer>> leftOut = new LinkedHashMap<>()
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
                    repositories << directory(Path.of(System.getProperty('maven.repo.local') ?: "${System.getProperty('user.home')}/.m2/repository".toString()))
                } else if (!isLocal(address)) {
                    repositories << repository(address)
                } else {
                    // On Windows a path is absolute with a drive or with the name of a computer and a share,
                    // also where it is written as an address: file:///srv/maven is the directory \srv\maven of
                    // whichever drive the command runs on there, and is left out as the path /srv/maven is.
                    Path path = address.regionMatches(true, 0, 'file:', 0, 5) ? Path.of(new URI(address)) : Path.of(address)
                    if (!path.isAbsolute()) {
                        throw new Unusable("${described(address)} is a path that is not absolute: it is one of the generated application, and is " +
                                'not read from where the command runs')
                    }
                    repositories << directory(path)
                }
            } catch (Unusable e) {
                leftOut.computeIfAbsent(e.message) { String reason -> new ArrayList<Integer>() } << number
            } catch (Exception ignored) {
                // what a path or an address is refused with can repeat it
                leftOut.computeIfAbsent("${described(address)} is not the address of a repository or of a directory".toString()) { String reason ->
                    new ArrayList<Integer>()
                } << number
            }
        }
        int lines = 0
        int more = 0
        for (Map.Entry<String, List<Integer>> each : leftOut.entrySet()) {
            List<Integer> entries = each.value
            if (lines == MAX_WARNINGS) {
                more += entries.size()
                continue
            }
            lines++
            String named = entries.take(MAX_ENTRIES_NAMED).join(', ') + (entries.size() > MAX_ENTRIES_NAMED ? " and ${entries.size() - MAX_ENTRIES_NAMED} more" : '')
            warnings << "${entries.size() == 1 ? 'Entry' : 'Entries'} ${named} of GRAILS_REPO_URL ${entries.size() == 1 ? 'is' : 'are'} not asked for the feature pack. ${each.key}".toString()
        }
        if (more > 0) {
            warnings << "${more} more ${more == 1 ? 'entry' : 'entries'} of GRAILS_REPO_URL ${more == 1 ? 'is' : 'are'} not asked for the feature pack".toString()
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
     * host or port no request can be made to. The message does not repeat the address as it was given:
     * see {@link GradleRepository#shownAddress(String)}.
     */
    static URI repository(String address) {
        URI uri = checked(address, 'repository', 'a repository a feature pack is taken from', true)
        return uri.rawPath?.endsWith('/') ? uri : URI.create("${uri}/")
    }

    /**
     * @param address the address of a feature catalog: {@code https}, or {@code file:} for a file
     * @return the address
     * @throws IllegalArgumentException when it is not one a catalog is read from, by the rules of
     * {@link #repository(String)}
     */
    static URI catalog(String address) {
        return checked(address, 'feature catalog', 'a feature catalog that is read', false)
    }

    /**
     * @param address the address of a plugin index: {@code https}, or {@code file:} for a file
     * @return the address
     * @throws IllegalArgumentException when it is not one a plugin index is read from, by the rules of
     * {@link #repository(String)}
     */
    static URI pluginIndex(String address) {
        return checked(address, 'plugin index', 'a plugin index that is read', false)
    }

    /**
     * The repository a catalog names for a plugin. A catalog is read from elsewhere, and what it names
     * is asked as a repository is that a redirect leads to: over https, and not on this computer or
     * in its network, which only the command line and the environment can name.
     *
     * @param address the address as the catalog has it
     * @return the address, as that of a directory
     * @throws IllegalArgumentException when it is not such an address
     */
    static URI repositoryOfCatalog(String address) {
        URI uri = repository(address)
        if (!HTTPS.equalsIgnoreCase(uri.scheme) || isNearby(uri.host)) {
            throw new Unusable('a catalog names only https repositories that are not of this computer or of its network')
        }
        // the name the plugin index has for Maven Central
        return uri.host.equalsIgnoreCase('repo1.maven.org') && uri.rawPath == '/maven2/' && uri.port == -1 ? URI.create(MAVEN_CENTRAL) : uri
    }

    private static URI checked(String address, String kind, String use, boolean directory) {
        if (address == null || address.isEmpty() || address.length() > MAX_ADDRESS || NOT_IN_AN_ADDRESS.matcher(address).find()) {
            throw new Unusable("${described(address)} is not the address of a ${kind}")
        }
        if (NOT_ASCII.matcher(address).find()) {
            throw new Unusable("${described(address)} is not ${use}: an address is written in ASCII, and a " +
                    'host name with other characters in its ASCII form, which begins with xn--')
        }
        URI uri
        try {
            uri = new URI(address)
        } catch (URISyntaxException ignored) {
            throw new Unusable("${described(address)} is not the address of a ${kind}")
        }
        if (FILE.equalsIgnoreCase(uri.scheme)) {
            try {
                return directory ? PackArtifacts.directory(Path.of(uri)) : Path.of(uri).toAbsolutePath().normalize().toUri()
            } catch (Exception ignored) {
                throw new Unusable("${described(address)} is not the address of a ${directory ? 'directory' : 'file'}")
            }
        }
        if (!HTTPS.equalsIgnoreCase(uri.scheme)) {
            throw new Unusable("${described(address)} is not ${use}: the address has to be https, or file: for a ${directory ? 'directory' : 'file'}")
        }
        // an at sign is where a user and a password end, also where they are not read as such
        if (uri.rawUserInfo != null || address.contains('@')) {
            throw new Unusable("${described(address)} is not ${use}: the address has a user or password, and credentials are not sent")
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            throw new Unusable("${described(address)} is not ${use}: the address cannot have a query or a fragment")
        }
        if (!canBeAsked(uri)) {
            throw new Unusable("${described(address)} is not ${use}: its host or port is not one a request can be made to. " +
                    'A host is a name, or an address written as four numbers from 0 to 255 with dots, or in brackets')
        }
        return uri
    }

    private static URI directory(Path path) {
        String address = path.toAbsolutePath().normalize().toUri().toString()
        return URI.create(address.endsWith('/') ? address : address + '/')
    }

    /**
     * Whether a request can be made to the host and port of an address. A host is a name or an address,
     * and which of them is not left to whoever resolves it: an address is four numbers from 0 to 255
     * with dots, or one in brackets, and what a resolver would also read as an address (one number,
     * fewer or more than four, numbers that begin with 0 or 0x) is neither.
     */
    private static boolean canBeAsked(URI address) {
        String host = address.host
        if (host == null || host.length() > MAX_HOST || address.port == 0 || address.port > MAX_PORT) {
            return false
        }
        if (host.startsWith('[')) {
            return inBrackets(host) != null
        }
        if (dotted(host) != null) {
            return true
        }
        return HOST_NAME.matcher(host).matches() && !NUMBER.matcher(host.substring(host.lastIndexOf('.') + 1)).matches()
    }

    /**
     * @return the four numbers of an address that is written as such, or {@code null} for anything else
     */
    private static int[] dotted(String host) {
        List<String> parts = host.split(/\./, -1).toList()
        if (parts.size() != 4 || parts.any { String part -> !PART_OF_IPV4.matcher(part).matches() || Integer.parseInt(part) > 255 }) {
            return null
        }
        return parts.collect { String part -> Integer.parseInt(part) } as int[]
    }

    /**
     * @return the address that is written in brackets, which is made of what is written and is not
     * looked up, or {@code null} when it is not one
     */
    private static InetAddress inBrackets(String host) {
        if (!IPV6.matcher(host).matches()) {
            return null
        }
        try {
            return InetAddress.getByName(host)
        } catch (IOException | RuntimeException ignored) {
            return null
        }
    }

    /**
     * Whether a host is this computer or in a network that is not the internet, when the address says so
     * itself: no name is looked up, so a name that resolves to such an address is not seen here.
     */
    private static boolean isNearby(String host) {
        String name = host.toLowerCase(Locale.ROOT)
        if (NAMES_OF_THIS_COMPUTER.contains(name) || name.endsWith('.localhost')) {
            return true
        }
        int[] quad = dotted(name)
        if (quad != null) {
            return isNearby(quad)
        }
        if (!name.startsWith('[')) {
            return false
        }
        InetAddress address = inBrackets(name)
        if (address == null) {
            return true
        }
        byte[] bytes = address.address
        if (bytes.length == 4) {
            // an address of the older kind that was written as one of the newer
            return isNearby(numbers(bytes, 0))
        }
        if (address.loopbackAddress || address.linkLocalAddress || address.siteLocalAddress || address.anyLocalAddress ||
                address.multicastAddress || (bytes[0] & 0xfe) == 0xfc) {
            return true
        }
        // the ways an address of the older kind is carried in one of the newer: after zeros, after
        // 64:ff9b::, and after 2002:
        boolean afterZeros = (0..<10).every { int index -> bytes[index] == (byte) 0 } &&
                ((bytes[10] == (byte) 0 && bytes[11] == (byte) 0) || (bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff))
        boolean translated = bytes[0] == (byte) 0 && bytes[1] == (byte) 0x64 && bytes[2] == (byte) 0xff && bytes[3] == (byte) 0x9b &&
                (4..<12).every { int index -> bytes[index] == (byte) 0 }
        if (afterZeros || translated) {
            return isNearby(numbers(bytes, 12))
        }
        return bytes[0] == (byte) 0x20 && bytes[1] == (byte) 0x02 && isNearby(numbers(bytes, 2))
    }

    private static int[] numbers(byte[] bytes, int from) {
        return [bytes[from] & 0xff, bytes[from + 1] & 0xff, bytes[from + 2] & 0xff, bytes[from + 3] & 0xff] as int[]
    }

    // this network, loopback, private, link-local, shared between a provider and its customers, for
    // protocols and for measuring, multicast and what is kept back
    private static boolean isNearby(int[] address) {
        int first = address[0]
        int second = address[1]
        return first == 0 || first == 127 || first == 10 || first >= 224 ||
                (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168) ||
                (first == 100 && second >= 64 && second <= 127) || (first == 198 && (second == 18 || second == 19)) ||
                (first == 192 && second == 0 && address[2] == 0)
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
     * it holds no pack that can be used. Nothing a repository answers leaves this method in another
     * way: not an exception of whatever read the answer, and not the error of a stack that an answer
     * was made to overflow. Only an error of the virtual machine itself, such as memory that ran out
     * elsewhere, is let through.
     * @throws IllegalArgumentException when the digest that is asked for is not a SHA-256
     */
    Pack resolve(Coordinates coordinates, List<URI> repositories, String sha256) {
        if (sha256 != null && !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("'${echo(sha256)}' is not a SHA-256, which is 64 hexadecimal digits")
        }
        String pinned = sha256?.toLowerCase(Locale.ROOT)
        try {
            Pack ofCopy = pinned == null || refresh || offline ? null : fromCopy(coordinates, repositories, pinned)
            if (ofCopy != null) {
                return ofCopy
            }
        } catch (PackDescriptorException e) {
            throw new PackArtifactException("The feature pack of ${coordinates} from ${kept.shownFile(pinned)} cannot be used:\n" +
                    e.problems.collect { String problem -> '  - ' + problem }.join('\n'))
        }
        List<String> tried = []
        boolean notAsked = false
        for (int index = 0; index < repositories.size(); index++) {
            URI repository = repositories[index]
            String where = shown(repository)
            String why
            try {
                return from(repository, where, coordinates, pinned)
            } catch (Missing ignored) {
                tried << "${where}: not found".toString()
                continue
            } catch (Offline e) {
                tried << "${where}: ${e.message}".toString()
                notAsked = true
                continue
            } catch (Local e) {
                throw new PackArtifactException("The feature pack of ${coordinates} was not taken from ${where}: ${e.message}. " +
                        'This is about this computer, not about the repository')
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
            } catch (Throwable e) {
                if (isOfTheMachine(e)) {
                    throw e
                }
                // an address or an answer the client itself refuses, or one that was made to break what
                // reads it: what the exception says can be text of the other side
                why = "cannot be asked (${e.class.simpleName})"
            }
            int left = repositories.size() - index - 1
            tried << "${where}: ${why}".toString() + (left > 0 ? " (${left} more ${left == 1 ? 'repository' : 'repositories'} not asked)" : '')
            throw new PackArtifactException("The feature pack of ${coordinates} was not taken from a repository. ${tried.join('; ')}. " +
                    "A repository that fails ends the search; ${REPOSITORY_OPTION} names the repositories to ask")
        }
        if (!notAsked || kept == null) {
            throw new PackArtifactException("The feature pack of ${coordinates} was not taken from a repository. ${tried.join('; ')}")
        }
        // nothing is asked of the network, and no repository that was named gave the jar or had given it before:
        // a jar with the digest that is asked for is that jar, wherever it was fetched from
        try {
            Pack ofCopy = pinned == null ? null : fromCopy(coordinates, pinned, null, null)
            if (ofCopy != null) {
                return ofCopy
            }
        } catch (PackDescriptorException e) {
            throw new PackArtifactException("The feature pack of ${coordinates} from ${kept.shownFile(pinned)} cannot be used:\n" +
                    e.problems.collect { String problem -> '  - ' + problem }.join('\n'))
        }
        throw new PackArtifactException("The feature pack of ${coordinates} was not taken from a repository. ${tried.join('; ')}. " +
                (kept.refused != null ? "No copy of a jar is read on this computer, whether there is one or not: ${kept.refused}" :
                        pinned != null ? "No copy of the jar of ${coordinates} with the SHA-256 ${pinned} is kept on this computer, in ${kept.shownDirectory}" :
                                "No copy of a jar that was fetched for ${coordinates} from ${tried.size() == 1 ? 'that repository' : 'one of those repositories'} " +
                                        "is kept on this computer, in ${kept.shownDirectory}"))
    }

    /**
     * The pack of the jar that is kept on this computer and has the digest that is asked for, when
     * one of the repositories gave that jar for the coordinates before: the first of them, which is
     * then where the jar was found. No repository is asked.
     *
     * @return the pack, or {@code null} when there is no such jar
     */
    private Pack fromCopy(Coordinates coordinates, List<URI> repositories, String pinned) {
        if (kept == null) {
            return null
        }
        for (URI repository : repositories) {
            if (FILE.equalsIgnoreCase(repository.scheme)) {
                continue
            }
            PackJarCache.Fetched last = kept.last(repository, coordinates.toString())
            if (last != null && last.sha256 == pinned) {
                return fromCopy(coordinates, pinned, repository, last)
            }
        }
        return null
    }

    /**
     * @param repository the repository the jar was fetched from, or {@code null} when it is not known
     * @param last what is kept of that fetch, or {@code null}
     * @return the pack of the jar of a digest that is kept on this computer, or {@code null} when none is
     */
    private Pack fromCopy(Coordinates coordinates, String digest, URI repository, PackJarCache.Fetched last) {
        byte[] bytes = kept.jar(digest, MAX_JAR_BYTES)
        if (bytes == null) {
            return null
        }
        String where = repository == null ? 'a copy on this computer' : shown(repository)
        Pack pack = loader.fromJarBytes(bytes, bytes.length, "${coordinates} (${where})")
        copiesUsed.put(digest, "the jar is the copy that is kept on this computer, ${kept.shownFile(digest)}, which has that SHA-256".toString())
        return pack.withArtifact(new PackArtifact(pack.descriptor.name, coordinates.toString(), repository?.toString(),
                last?.file ?: "${coordinates.artifact}-${coordinates.version}.jar".toString(), digest, last?.checkedAgainst, true))
    }

    /**
     * The pack of the jar that was last fetched from a repository for the coordinates, for when nothing
     * is asked of the network.
     *
     * @param pinned the digest the jar has to have, or {@code null} when none was asked for
     * @return the pack, or {@code null} when no such jar is kept
     */
    private Pack lastFetched(URI repository, String where, Coordinates coordinates, String pinned) {
        PackJarCache.Fetched last = kept?.last(repository, coordinates.toString())
        if (last == null || (pinned != null && last.sha256 != pinned)) {
            return null
        }
        Pack pack = fromCopy(coordinates, last.sha256, repository, last)
        if (pack == null || pinned != null) {
            return pack
        }
        copiesUsed.put(last.sha256, ("the jar is the copy that is kept on this computer, ${kept.shownFile(last.sha256)}, of the jar that was last fetched from " +
                "${where} for ${coordinates}: --offline asks nothing of the network, so whether the repository still has that jar is not known").toString())
        return pack.withArtifact(new PackArtifact(pack.descriptor.name, coordinates.toString(), repository.toString(), last.file, last.sha256,
                last.checkedAgainst, false))
    }

    /**
     * Reads one file that is no jar of a plugin, such as a catalog, as a file of a repository is read:
     * over https or from a file, with the limits, the times and the rules for a redirect that hold for
     * a jar.
     *
     * @param address the address of the file, as {@link #catalog(String)} gives it
     * @param limit the most bytes the file can have
     * @param name what the file is called in a message
     * @return the bytes of the file
     * @throws PackArtifactException when the file was not read, with the reason and nothing else as its
     * message; as with {@link #resolve}, nothing an answer holds leaves this method in another way
     */
    byte[] file(URI address, int limit, String name) {
        return file(address, limit, name, null)
    }

    /**
     * @param within how long the file can take, from the request to its last byte, in the place of
     * the times of a jar; {@code null} for those
     * @see #file(URI, int, String)
     */
    byte[] file(URI address, int limit, String name, Duration within) {
        return file(address, limit, name, within, false)
    }

    /**
     * Reads one file as {@link #file(URI, int, String, Duration)} does, for a caller that looks for it
     * in more than one place: a file that is not there is no failure then.
     *
     * @return the bytes of the file, or {@code null} when the address has no such file, or when it is
     * one of the network and nothing is asked of the network
     * @throws PackArtifactException when the file was not read for another reason
     */
    byte[] fileIfThere(URI address, int limit, String name, Duration within) {
        return file(address, limit, name, within, true)
    }

    private byte[] file(URI address, int limit, String name, Duration within, boolean orNone) {
        ByteArrayOutputStream sink = new ByteArrayOutputStream()
        String why
        try {
            copy(address, address, limit, name, sink, [], within == null ? new Watch(answerTimeout, fileTimeout) : new Watch(within, within))
            return sink.toByteArray()
        } catch (Missing ignored) {
            if (orNone) {
                return null
            }
            why = 'not found'
        } catch (Offline e) {
            if (orNone) {
                return null
            }
            why = e.message
        } catch (Refused e) {
            why = e.message
        } catch (IOException e) {
            why = reason(e)
        } catch (Throwable e) {
            if (isOfTheMachine(e)) {
                throw e
            }
            why = "cannot be asked (${e.class.simpleName})"
        }
        throw new PackArtifactException(why)
    }

    /**
     * @param address an address this class made
     * @return the address as a message has it
     */
    static String shownAddress(URI address) {
        return shown(address)
    }

    // an answer can be made to overflow the stack of what reads it, which is a failure of the answer;
    // any other error of the virtual machine is not one a repository is told of
    private static boolean isOfTheMachine(Throwable e) {
        return (e instanceof VirtualMachineError && !(e instanceof StackOverflowError)) || e instanceof RequestRefused
    }

    private Pack from(URI repository, String where, Coordinates coordinates, String pinned) {
        if (offline && !FILE.equalsIgnoreCase(repository.scheme)) {
            Pack ofCopy = lastFetched(repository, where, coordinates, pinned)
            if (ofCopy == null) {
                throw new Offline()
            }
            return ofCopy
        }
        URI directory = repository.resolve("${coordinates.group.replace('.', '/')}/${coordinates.artifact}/${coordinates.version}/")
        String file = fileName(directory, repository, coordinates)
        URI jar = directory.resolve(file)
        MessageDigest sha256 = MessageDigest.getInstance('SHA-256')
        MessageDigest sha1 = MessageDigest.getInstance('SHA-1')
        // what is hashed, checked and read is one array
        Held held = new Held()
        try {
            copy(jar, repository, MAX_JAR_BYTES, file, held, [sha256, sha1])
        } catch (OutOfMemoryError ignored) {
            held = null
            throw new Local("the jar, which can have ${MAX_JAR_BYTES} bytes, does not fit into the memory this command has")
        }
        String digest = HexFormat.of().formatHex(sha256.digest())
        // before the jar is opened
        if (pinned != null && pinned != digest) {
            throw new Refused("checksum mismatch: the sha256 of ${file} is ${digest}, and ${pinned} was asked for")
        }
        String checkedAgainst = checkedAgainst(jar, repository, file, [sha256: digest, sha1: HexFormat.of().formatHex(sha1.digest())])
        Pack pack = loader.fromJarBytes(held.bytes(), held.size(), "${coordinates} (${where})")
        copiesUsed.remove(digest)
        // only a jar that was checked and holds a pack is kept, and one of a directory is where it is already
        if (kept != null && !FILE.equalsIgnoreCase(repository.scheme)) {
            kept.keep(held.bytes(), held.size(), digest, repository, coordinates.toString(), file, checkedAgainst)
        }
        return pack.withArtifact(new PackArtifact(pack.descriptor.name, coordinates.toString(), repository.toString(), file,
                digest, checkedAgainst, pinned != null))
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
            } catch (Throwable e) {
                throw failureOf(name, e)
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
     * The failure of a request for a file that is asked for besides the jar, which names that file,
     * whatever the failure is.
     */
    private static RuntimeException failureOf(String name, Throwable e) {
        if (isOfTheMachine(e)) {
            throw e
        }
        if (e instanceof Refused) {
            return e.message.startsWith(name) ? (Refused) e : new Refused("${name}: ${e.message}")
        }
        return new Refused(e instanceof IOException ? "${name}: ${reason((IOException) e)}" : "${name}: cannot be asked (${e.class.simpleName})")
    }

    /**
     * The name of the jar. That of a snapshot is the name of its latest build, which the
     * {@code maven-metadata.xml} of the version gives; a repository without one, such as the local
     * repository of Maven, keeps the jar under the name of the version.
     */
    private String fileName(URI directory, URI repository, Coordinates coordinates) {
        String plain = "${coordinates.artifact}-${coordinates.version}.jar"
        if (!coordinates.snapshot) {
            return plain
        }
        ByteArrayOutputStream metadata = new ByteArrayOutputStream()
        String latest
        try {
            copy(directory.resolve(METADATA), repository, MAX_METADATA_BYTES, METADATA, metadata, [])
            latest = build(metadata.toByteArray(), coordinates)
        } catch (Missing | NotGiven ignored) {
            return plain
        } catch (Throwable e) {
            throw failureOf(METADATA, e)
        }
        return latest == null ? plain : "${coordinates.artifact}-${latest}.jar"
    }

    /**
     * The version of the latest build of the main jar a {@code maven-metadata.xml} names: the version
     * that was asked for with the time and the number of a build in the place of {@code SNAPSHOT}, and
     * nothing else. The metadata has to be that of what was asked for where it says what it is of.
     *
     * <p>The file is read as it comes, element after element, and no tree is made of it: how deep it is
     * nested and how many elements it has are counted and bounded, and of the few elements that are
     * read only the text that is directly in them is taken. Nothing here goes deeper for a deeper file.</p>
     *
     * @return the version of the build, or {@code null} when the repository keeps the snapshot under the
     * name of the version
     */
    private static String build(byte[] metadata, Coordinates coordinates) {
        Map<String, String> said = new HashMap<>()
        List<Map<String, String>> builds = []
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory()
            // neither a document type nor anything from outside the document is read
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(metadata))
            try {
                List<String> path = []
                int elements = 0
                // the element whose text is being read, as the depth it is at, and none at 0
                int reading = 0
                StringBuilder text = new StringBuilder()
                Map<String, String> ofOneFile = null
                while (reader.hasNext()) {
                    int event = reader.next()
                    if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new Refused("${METADATA} cannot be read")
                    }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if (path.size() == MAX_METADATA_DEPTH || ++elements > MAX_METADATA_ELEMENTS ||
                                (path.isEmpty() && reader.localName != METADATA_ROOT)) {
                            throw new Refused("${METADATA} is not the metadata of a Maven repository")
                        }
                        path << reader.localName
                        String at = path.join('/')
                        if (at == BUILD_OF_A_FILE) {
                            ofOneFile = new HashMap<>()
                        }
                        if (reading == 0 && READ_OF_METADATA.contains(at)) {
                            reading = path.size()
                            text.setLength(0)
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        String at = path.join('/')
                        if (reading == path.size()) {
                            reading = 0
                            String name = path.last()
                            if (at.startsWith(BUILD_OF_A_FILE + '/')) {
                                ofOneFile.putIfAbsent(name, text.toString().trim())
                            } else {
                                said.putIfAbsent(at, text.toString().trim())
                            }
                        }
                        if (at == BUILD_OF_A_FILE) {
                            builds << ofOneFile
                            ofOneFile = null
                        }
                        path.removeLast()
                    } else if (reading == path.size() && reading > 0 && (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)) {
                        text.append(reader.text)
                        if (text.length() > MAX_PART * 2) {
                            throw new Refused("${METADATA} is not the metadata of a Maven repository")
                        }
                    }
                }
            } finally {
                reader.close()
            }
        } catch (XMLStreamException ignored) {
            throw new Refused("${METADATA} cannot be read")
        }
        Map<String, String> asked = [groupId: coordinates.group, artifactId: coordinates.artifact, version: coordinates.version]
        for (Map.Entry<String, String> each : asked.entrySet()) {
            String value = said.get(METADATA_ROOT + '/' + each.key)
            if (value && value != each.value) {
                throw new Refused("${METADATA} is that of another ${each.key} than the one asked for")
            }
        }
        String release = coordinates.version.substring(0, coordinates.version.length() - SNAPSHOT.length())
        Pattern buildOfIt = Pattern.compile(Pattern.quote(release) + BUILD)
        String latest = null
        String latestTime = null
        long latestNumber = -1
        boolean underTheVersion = false
        for (Map<String, String> each : builds) {
            if (each.get('extension') != 'jar' || each.get('classifier')) {
                continue
            }
            String value = each.get('value') ?: ''
            if (value == coordinates.version) {
                underTheVersion = true
                continue
            }
            Matcher matcher = buildOfIt.matcher(value)
            if (!matcher.matches() || !isTime(matcher.group(1))) {
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
        String time = said.get(LATEST_BUILD + '/timestamp')
        String number = said.get(LATEST_BUILD + '/buildNumber')
        if (said.get(LATEST_BUILD + '/localCopy') == 'true' || (!time && !number)) {
            return null
        }
        if (!isTime(time ?: '') || !BUILD_NUMBER.matcher(number ?: '').matches()) {
            throw new Refused("${METADATA} names a build that is not one of this snapshot")
        }
        return "${release}-${time}-${number}"
    }

    // the time of a build as Maven writes it, which is a day and a time of day there are
    private static boolean isTime(String text) {
        try {
            LocalDateTime.parse(text, BUILD_TIME)
            return true
        } catch (DateTimeParseException ignored) {
            return false
        }
    }

    /**
     * Copies a file of a repository, and no more of it than the limit. A repository that stops answering
     * is not waited for: the request and each read of the answer have the time of an answer, and the
     * file has its own. Whatever ends the copy, what watches the time is ended with it.
     */
    private void copy(URI address, URI repository, long limit, String name, OutputStream sink, List<MessageDigest> digests) {
        copy(address, repository, limit, name, sink, digests, new Watch(answerTimeout, fileTimeout))
    }

    private void copy(URI address, URI repository, long limit, String name, OutputStream sink, List<MessageDigest> digests, Watch watch) {
        Throwable failure = null
        boolean expired = false
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
        } catch (Throwable e) {
            failure = e
        } finally {
            expired = watch.end()
        }
        if (failure != null && (isOfTheMachine(failure) || failure instanceof OutOfMemoryError)) {
            throw failure
        }
        // closing the answer under a read ends the read in whatever way the stream has, which can also
        // look like the end of the answer
        if (expired) {
            throw new Refused(TIMEOUT)
        }
        if (failure != null) {
            throw failure
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
        if (offline) {
            throw new Offline()
        }
        URI current = address
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            // every address that is asked, the first and each one a repository sends on to
            if (!HTTPS.equalsIgnoreCase(current.scheme) || current.rawUserInfo != null || (current.rawAuthority ?: '').contains('@')) {
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

    // one rule for what was typed in a message, that of the features: FeatureSelectionException.shown
    private static String echo(String text) {
        return FeatureSelectionException.shown(text)
    }

    // an address that was given, at the start of a message. It is not repeated as it was given, since
    // a password can be anywhere in what does not parse as it was meant: see GradleRepository.shownAddress
    private static String described(String address) {
        return GradleRepository.shownAddress(address).capitalize()
    }

    /**
     * A repository in a line that says what came of asking it. The addresses this class makes have
     * neither a user nor a query; one that was made elsewhere and has is not repeated.
     */
    private static String shown(URI repository) {
        boolean directory = FILE.equalsIgnoreCase(repository.scheme) && repository.rawAuthority == null
        boolean plain = repository.host != null && repository.rawUserInfo == null && repository.rawQuery == null && repository.rawFragment == null
        if (!directory && !plain) {
            return GradleRepository.shownAddress(repository.toString())
        }
        String text = UNREADABLE.matcher(repository.toString()).replaceAll('?').replace('@|', '@ |')
        return text.length() > MAX_REPOSITORY_ECHO ? text.substring(0, MAX_REPOSITORY_ECHO) + '...' : text
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
     * ends a request that has no answer yet. Once it is ended it interrupts nothing.
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
        private boolean cleared

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
            if (expired && !cleared) {
                // the interrupt was for the read, not for what the thread does next
                cleared = true
                Thread.interrupted()
            }
            return expired
        }
    }

    // the bytes of the jar, without a copy of them being made to hand them on
    private static final class Held extends ByteArrayOutputStream {

        byte[] bytes() {
            return buf
        }
    }

    private static final class HttpTransport implements Transport {

        private final SSLContext context
        private final Duration answerTimeout
        // the client of a transport with certificates of its own, made when it is first asked for
        private HttpClient own

        HttpTransport(SSLContext context, Duration answerTimeout) {
            this.context = context
            this.answerTimeout = answerTimeout
        }

        @Override
        Reply get(URI address) throws IOException {
            Consumer<URI> before = beforeRequest
            if (before != null) {
                before.accept(address)
            }
            // the file as it is: its bytes are what is hashed
            HttpRequest request = HttpRequest.newBuilder(address).timeout(answerTimeout).header('User-Agent', 'grails-forge')
                    .header('Accept-Encoding', 'identity').GET().build()
            try {
                HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream())
                return new Reply(response.statusCode(), response.headers().firstValue('Location').orElse(null), response.body(),
                        response.headers().firstValue('Content-Encoding').orElse(null))
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt()
                throw new InterruptedIOException()
            }
        }

        // a transport that trusts the certificates of the JVM, which every command has, asks with the one client of all of
        // them: a client has a thread and connections of its own, and those of a client for each command that an
        // interactive console runs would add up while the console runs
        private synchronized HttpClient client() {
            if (context == null) {
                return OfTheJvm.CLIENT
            }
            if (own == null) {
                own = newClient(context)
            }
            return own
        }

        private static HttpClient newClient(SSLContext context) {
            HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(CONNECT_TIMEOUT)
            return (context == null ? builder : builder.sslContext(context)).build()
        }

        // made when it is first asked for, once
        private static final class OfTheJvm {

            static final HttpClient CLIENT = newClient(null)
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

    /**
     * Thrown by what {@link #beforeEachRequest} was given for a request that is not to be made. It is
     * no failure of a repository: nothing here catches it.
     */
    static final class RequestRefused extends AssertionError {

        RequestRefused(String message) {
            super((Object) message)
        }
    }

    // a file that is not asked for because nothing is asked of the network
    private static final class Offline extends RuntimeException {

        Offline() {
            super('not asked, since --offline makes no request to the network', null, false, false)
        }
    }

    // what this computer could not do, which is not for the repository to answer for
    private static final class Local extends RuntimeException {

        Local(String reason) {
            super(reason, null, false, false)
        }
    }

    // an address a pack cannot be taken from, with a message that is made here and repeats no secret
    private static final class Unusable extends IllegalArgumentException {

        Unusable(String message) {
            super(message)
        }
    }
}
