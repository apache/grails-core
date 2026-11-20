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

import java.net.http.HttpTimeoutException
import java.security.MessageDigest
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.net.ssl.SSLHandshakeException
import org.apache.grails.forge.cli.pack.PackArtifacts.Reply
import org.apache.grails.forge.cli.pack.PackArtifacts.Transport
import org.apache.grails.forge.core.pack.Pack
import spock.lang.Specification
import spock.lang.Unroll

/**
 * What is asked of a repository that is not a directory, with the repository played here: nothing is
 * asked of the network.
 */
class PackArtifactsSpec extends Specification {

    static final String DESCRIPTOR = 'format: 1\nkind: feature\ntemplateApi: 1\nname: acme-audit\n' +
            "compatibility:\n  grails: '[9.0.0,10.0.0)'\n  forge: '[9.0.0,10.0.0)'\n" +
            'dependencies:\n  - scope: implementation\n    coordinates: com.acme:acme-audit-grails\n    version: 2.3.0\n'

    static final String JAR = 'https://repository.invalid/maven/com/acme/acme-audit-grails/2.3.0/acme-audit-grails-2.3.0.jar'
    static final String NOT_TAKEN = 'The feature pack of com.acme:acme-audit-grails:2.3.0 was not taken from a repository. '
    static final String HINT = '. A repository that fails ends the search; --feature-pack-repository names the repositories to ask'
    static final List<URI> REPOSITORY = [URI.create('https://repository.invalid/maven/')]

    List<URI> asked = []
    Map<String, Closure<Reply>> answers = [:]
    Transport transport = { URI address ->
        asked << address
        Closure<Reply> answer = answers[address.toString()]
        return answer == null ? new Reply(404, null, null) : answer.call()
    } as Transport
    PackArtifacts artifacts = new PackArtifacts(transport)

    void "the jar is taken over https with its checksum, and nothing else of the plugin is asked for"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(200, null, new ByteArrayInputStream(jar)) }
        answers[JAR + '.sha1'] = { new Reply(200, null, new ByteArrayInputStream(digest(jar, 'SHA-1').bytes)) }

        when:
        Pack pack = artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'),
                [PackArtifacts.repository('https://repository.invalid/maven')], null)

        then:
        pack.descriptor.name == 'acme-audit'
        pack.origin == 'com.acme:acme-audit-grails:2.3.0 (https://repository.invalid/maven/)'
        pack.artifact.sha256 == digest(jar, 'SHA-256')
        pack.artifact.checkedAgainst == 'sha1'
        !pack.artifact.pinned
        pack.artifact.file == 'acme-audit-grails-2.3.0.jar'
        pack.artifact.recordableRepository == 'https://repository.invalid/maven/'

        and: 'the jar and the checksums of it, and neither a POM nor another jar'
        asked*.toString() == [JAR, JAR + '.sha256', JAR + '.sha1']
    }

    void "a redirect to an address that is not https is not followed"() {
        given:
        answers[JAR] = { new Reply(302, location, null) }

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + 'https://repository.invalid/maven/: redirect to an address that is not https' + HINT
        asked*.toString() == [JAR]

        where:
        location << ['http://repository.invalid/maven/acme.jar', 'file:///etc/passwd', 'ftp://repository.invalid/acme.jar',
                     'https://user:secret@other.invalid/acme.jar', 'jar:https://other.invalid/acme.jar!/x']
    }

    @Unroll
    void "a redirect to #location is refused before it is asked, and is no failure of this program"() {
        given:
        answers[JAR] = { new Reply(302, location, null) }

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + "https://repository.invalid/maven/: ${reason}" + HINT
        asked*.toString() == [JAR]

        where:
        location                              || reason
        'https://localhost:99999/x.jar'       || 'redirect to an address with a host or port no request can be made to'
        'https://other.invalid:0/x.jar'       || 'redirect to an address with a host or port no request can be made to'
        'https://other.invalid./x.jar'        || 'redirect to an address with a host or port no request can be made to'
        'https://localhost/x.jar'             || 'redirect to an address of this computer or of its network'
        'https://build.localhost/x.jar'       || 'redirect to an address of this computer or of its network'
        'https://127.0.0.1/x.jar'             || 'redirect to an address of this computer or of its network'
        'https://169.254.169.254/latest'      || 'redirect to an address of this computer or of its network'
        'https://10.0.0.8/x.jar'              || 'redirect to an address of this computer or of its network'
        'https://192.168.1.1:8443/x.jar'      || 'redirect to an address of this computer or of its network'
        'https://[::1]/x.jar'                 || 'redirect to an address of this computer or of its network'
        'https://[fe80::1]/x.jar'             || 'redirect to an address of this computer or of its network'
        'https://[fd00::1]/x.jar'             || 'redirect to an address of this computer or of its network'
        'https://0.0.0.0/x.jar'               || 'redirect to an address of this computer or of its network'
    }

    void "a repository of this computer can send on to this computer"() {
        given:
        byte[] jar = jar()
        String local = JAR.replace('repository.invalid', 'localhost:8443')
        answers[local] = { new Reply(302, 'https://127.0.0.1:8444/x.jar', null) }
        answers['https://127.0.0.1:8444/x.jar'] = { new Reply(200, null, new ByteArrayInputStream(jar)) }

        expect:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), [URI.create('https://localhost:8443/maven/')], null)
                .artifact.sha256 == digest(jar, 'SHA-256')
    }

    void "a redirect to an https address is followed, and only so many of them"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(301, '/mirror/acme.jar', null) }
        answers['https://repository.invalid/mirror/acme.jar'] = { new Reply(307, 'https://other.invalid/acme.jar', null) }
        answers['https://other.invalid/acme.jar'] = { new Reply(200, null, new ByteArrayInputStream(jar)) }

        when:
        Pack pack = artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        pack.artifact.sha256 == digest(jar, 'SHA-256')
        pack.artifact.checkedAgainst == null

        when: 'a repository that sends on to itself'
        asked.clear()
        answers[JAR] = { new Reply(302, JAR, null) }
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message.endsWith('https://repository.invalid/maven/: more than 5 redirects' + HINT)
        asked.size() == PackArtifacts.MAX_REDIRECTS + 1
    }

    @Unroll
    void "a repository that fails with #reason ends the search, and one that has no such jar does not"() {
        given:
        byte[] jar = jar()
        answers[JAR] = failure
        answers[JAR.replace('repository.invalid', 'second.invalid')] = { new Reply(200, null, new ByteArrayInputStream(jar)) }
        List<URI> repositories = [URI.create('https://first.invalid/maven/'), URI.create('https://repository.invalid/maven/'), URI.create('https://second.invalid/maven/')]

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), repositories, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + "https://first.invalid/maven/: not found; https://repository.invalid/maven/: ${reason} (1 more repository not asked)" + HINT
        !e.message.contains('what the other side says')
        !asked.any { URI address -> address.host == 'second.invalid' }

        where:
        failure                                                                    || reason
        ({ new Reply(500, null, null) })                                           || 'HTTP status 500'
        ({ new Reply(403, null, null) })                                           || 'HTTP status 403: the repository refuses access to the jar, and the search stops there so that the coordinates are sent to no other repository'
        ({ new Reply(401, null, null) })                                           || 'HTTP status 401: the repository refuses access to the jar, and the search stops there so that the coordinates are sent to no other repository'
        ({ throw new HttpTimeoutException('what the other side says') })           || 'timeout'
        ({ throw new SocketTimeoutException('what the other side says') })         || 'timeout'
        ({ throw new SSLHandshakeException('what the other side says') })          || 'TLS failure (SSLHandshakeException)'
        ({ throw new ConnectException('what the other side says') })               || 'no connection (ConnectException)'
        ({ throw new IOException('what the other side says') })                    || 'cannot be read (IOException)'
        ({ throw new IllegalArgumentException('what the other side says') })       || 'cannot be asked (IllegalArgumentException)'
        ({ throw new IllegalStateException('what the other side says') })          || 'cannot be asked (IllegalStateException)'
        ({ new Reply(200, null, new Failing()) })                                  || 'cannot be asked (UnsupportedOperationException)'
        ({ new Reply(302, null, null) })                                           || 'redirect without an address'
        ({ new Reply(302, 'https://exa mple.invalid/', null) })                    || 'redirect to an address that cannot be read'
        ({ new Reply(200, null, new ByteArrayInputStream(new byte[9]), 'gzip') })  || 'the answer is encoded, and only a file as it is was asked for'
    }

    void "an address that is not https is never asked, whoever passes it"() {
        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), [URI.create('http://repository.invalid/maven/')], null)

        then:
        PackArtifactException e = thrown()
        e.message.endsWith('http://repository.invalid/maven/: the address is not https' + HINT)
        asked.isEmpty()
    }

    @Unroll
    void "a repository that answers #status for the checksum of a jar it gave publishes none"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(200, null, new ByteArrayInputStream(jar)) }
        answers[JAR + '.sha256'] = { new Reply(status, null, null) }
        answers[JAR + '.sha1'] = { new Reply(status, null, null) }

        expect:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null).artifact.checkedAgainst == null

        where:
        status << [401, 403, 404, 410]
    }

    @Unroll
    void "a checksum that cannot be had for another reason ends the search, and the file is named: #reason"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(200, null, new ByteArrayInputStream(jar)) }
        answers[JAR + '.sha256'] = failure

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + "https://repository.invalid/maven/: acme-audit-grails-2.3.0.jar.sha256${reason}" + HINT

        where:
        failure                                                                          || reason
        ({ new Reply(500, null, null) })                                                 || ': HTTP status 500'
        ({ throw new HttpTimeoutException('x') })                                        || ': timeout'
        ({ throw new IllegalArgumentException('x') })                                    || ': cannot be asked (IllegalArgumentException)'
        ({ new Reply(302, 'http://other.invalid/x', null) })                             || ': redirect to an address that is not https'
        ({ new Reply(200, null, new ByteArrayInputStream([0xFF, 0xFE, 0x61, 0x00] as byte[])) }) || ' of the repository is not a sha256'
    }

    void "a checksum with a byte order mark before it is a checksum"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(200, null, new ByteArrayInputStream(jar)) }
        byte[] marked = ([0xEF, 0xBB, 0xBF] + ("${digest(jar, 'SHA-256')}\r\n".bytes as List)) as byte[]
        answers[JAR + '.sha256'] = { new Reply(200, null, new ByteArrayInputStream(marked)) }

        expect:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null).artifact.checkedAgainst == 'sha256'
    }

    void "the digest that is asked for is one in either case, and is not something else"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(200, null, new ByteArrayInputStream(jar)) }

        expect:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, digest(jar, 'SHA-256').toUpperCase()).artifact.pinned

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, 'abc')

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('is not a SHA-256')
    }

    void "an answer that stops coming is not waited for"() {
        given: 'a repository that begins a jar and then says nothing more'
        PipedOutputStream held = new PipedOutputStream()
        PipedInputStream body = new PipedInputStream(held)
        held.write(new byte[10])
        held.flush()
        answers[JAR] = { new Reply(200, null, body) }
        PackArtifacts impatient = new PackArtifacts(transport, Duration.ofMillis(500), Duration.ofSeconds(60))

        when:
        long started = System.currentTimeMillis()
        impatient.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + 'https://repository.invalid/maven/: timeout' + HINT
        System.currentTimeMillis() - started < 30000

        and: 'the interrupt that ended the read is not left for what the command does next'
        !Thread.currentThread().isInterrupted()

        cleanup:
        held.close()
    }

    void "a request that gets no answer is not waited for"() {
        given:
        answers[JAR] = { Thread.sleep(120000); new Reply(200, null, new ByteArrayInputStream(jar())) }
        PackArtifacts impatient = new PackArtifacts(transport, Duration.ofMillis(500), Duration.ofSeconds(60))

        when:
        long started = System.currentTimeMillis()
        impatient.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + 'https://repository.invalid/maven/: timeout' + HINT
        System.currentTimeMillis() - started < 30000
        !Thread.currentThread().isInterrupted()
    }

    void "a file that keeps coming and takes too long is ended"() {
        given: 'a repository that gives a byte now and then, without end'
        answers[JAR] = { new Reply(200, null, new Dripping()) }
        PackArtifacts impatient = new PackArtifacts(transport, Duration.ofSeconds(30), Duration.ofMillis(800))

        when:
        long started = System.currentTimeMillis()
        impatient.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + 'https://repository.invalid/maven/: timeout' + HINT
        System.currentTimeMillis() - started < 30000
    }

    void "without a repository given it is those of GRAILS_REPO_URL and then Maven Central, and with one only what is given"() {
        given:
        List<String> warnings = []

        expect:
        PackArtifacts.repositories([], null, warnings)*.toString() == ['https://repo.maven.apache.org/maven2/']
        PackArtifacts.repositories([], 'https://nexus.invalid/releases; mavenCentral() ;https://nexus.invalid/more/', warnings)*.toString() ==
                ['https://nexus.invalid/releases/', 'https://repo.maven.apache.org/maven2/', 'https://nexus.invalid/more/']
        PackArtifacts.repositories(['https://private.invalid/maven'], 'https://nexus.invalid/releases', warnings)*.toString() == ['https://private.invalid/maven/']

        and: 'the local repository of Maven and a directory are addresses of files'
        PackArtifacts.repositories([], 'mavenLocal();/srv/maven;file:///srv/more', warnings)*.scheme == ['file', 'file', 'file', 'https']
        PackArtifacts.repositories([], 'mavenLocal()', warnings)[0].toString().endsWith('/')
        warnings.isEmpty()
    }

    @Unroll
    void "the entry #entry of GRAILS_REPO_URL is left out with a warning, and is no mistake of the command"() {
        given:
        List<String> warnings = []

        when:
        List<URI> repositories = PackArtifacts.repositories([], "https://nexus.invalid/releases;${entry}", warnings)

        then:
        repositories*.toString() == ['https://nexus.invalid/releases/', 'https://repo.maven.apache.org/maven2/']
        warnings.size() == 1
        warnings[0].startsWith('Entry 2 of GRAILS_REPO_URL is not asked for the feature pack. ')
        warnings[0].contains(reason)

        and: 'what can be a secret is not repeated'
        !warnings[0].contains('secret')
        !warnings[0].contains('user:')

        where:
        entry                                          || reason
        'https://user:secret@nexus.invalid/private'    || "'https://nexus.invalid/private' is not a repository a feature pack is taken from: the address has a user or password, and credentials are not sent"
        'https://user:sec ret@nexus.invalid/private'   || "'https://nexus.invalid/private' is not the address of a repository"
        'https://nexus.invalid/private?token=secret'   || "'https://nexus.invalid/private' is not a repository a feature pack is taken from: the address cannot have a query or a fragment"
        'http://nexus.invalid/releases'                || 'the address has to be https, or file: for a directory'
        'mailto:x'                                     || 'the address has to be https, or file: for a directory'
        'jar:file:/x.jar!/'                            || 'the address has to be https, or file: for a directory'
        'file://server/share'                          || 'is not the address of a repository or of a directory'
        'file:///srv/maven?secret=1'                   || 'is not the address of a repository or of a directory'
        'https://nexus.invalid:99999/releases'         || 'its host or port is not one a request can be made to'
    }

    @Unroll
    void "the address #address is not one a request is made to"() {
        when:
        PackArtifacts.repository(address)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(reason)
        !e.message.contains('secret')

        where:
        address                                || reason
        'https://localhost:99999/m'            || 'its host or port is not one a request can be made to'
        'https://localhost:0/m'                || 'its host or port is not one a request can be made to'
        'https://localhost./m'                 || 'its host or port is not one a request can be made to'
        'https://user:secret@nexus.invalid/m'  || "'https://nexus.invalid/m' is not a repository a feature pack is taken from: the address has a user or password"
        'https://nexus.invalid/m?key=secret'   || "'https://nexus.invalid/m' is not a repository a feature pack is taken from: the address cannot have a query"
        'https://nexus.invalid/' + 'm' * 3000  || 'is not the address of a repository'
    }

    void "the address of a directory is not one to write into a project"() {
        expect:
        new org.apache.grails.forge.core.pack.PackArtifact('a', 'g:a:1', repository, 'a-1.jar', 'f' * 64, null, false).recordableRepository == recorded

        where:
        repository                           || recorded
        'https://nexus.invalid/m/'           || 'https://nexus.invalid/m/'
        'HTTPS://nexus.invalid/m/'           || 'HTTPS://nexus.invalid/m/'
        'file:///Users/me/.m2/repository/'   || null
        'https://user:pw@nexus.invalid/m/'   || null
        'http://nexus.invalid/m/'            || null
        'not an address'                     || null
        null                                 || null
    }

    private static byte[] jar() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ZipOutputStream(bytes).withCloseable { ZipOutputStream zip ->
            zip.putNextEntry(new ZipEntry('META-INF/grails-forge/feature.yml'))
            zip.write(DESCRIPTOR.getBytes('UTF-8'))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private static String digest(byte[] bytes, String algorithm) {
        return MessageDigest.getInstance(algorithm).digest(bytes).encodeHex().toString()
    }

    static class Failing extends InputStream {

        @Override
        int read() {
            throw new UnsupportedOperationException('what the other side says')
        }
    }

    static class Dripping extends InputStream {

        @Override
        int read() {
            Thread.sleep(50)
            return 1
        }

        @Override
        int read(byte[] bytes, int offset, int length) {
            bytes[offset] = (byte) read()
            return 1
        }
    }
}
