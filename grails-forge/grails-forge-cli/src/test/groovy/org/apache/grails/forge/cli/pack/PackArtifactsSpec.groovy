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
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

/**
 * What is asked of a repository that is not a directory, with the repository played here: nothing is
 * asked of the network.
 */
class PackArtifactsSpec extends Specification {

    static final boolean WINDOWS = File.separatorChar == ('\\' as char)

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
        'https://2130706433/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://2130706433:8443/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://0/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://167772165/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://0x7f000001/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://0x7f.1/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://0177.0.0.1/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://127.1/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://127.0.0.01/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://1.2.3.4.5/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://256.1.1.1/x.jar' || 'redirect to an address with a host or port no request can be made to'
        'https://[64:ff9b::7f00:1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[::127.0.0.1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[::ffff:10.0.0.1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[::ffff:7f00:1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[2002:7f00:1::]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[2002:c0a8:101::1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://100.64.0.1/x.jar' || 'redirect to an address of this computer or of its network'
        'https://100.127.255.254/x.jar' || 'redirect to an address of this computer or of its network'
        'https://198.18.0.1/x.jar' || 'redirect to an address of this computer or of its network'
        'https://198.19.255.1/x.jar' || 'redirect to an address of this computer or of its network'
        'https://192.0.0.8/x.jar' || 'redirect to an address of this computer or of its network'
        'https://224.0.0.1/x.jar' || 'redirect to an address of this computer or of its network'
        'https://239.255.255.250/x.jar' || 'redirect to an address of this computer or of its network'
        'https://255.255.255.255/x.jar' || 'redirect to an address of this computer or of its network'
        'https://0.1.2.3/x.jar' || 'redirect to an address of this computer or of its network'
        'https://172.16.0.1/x.jar' || 'redirect to an address of this computer or of its network'
        'https://172.31.255.255/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[ff02::1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[fc00::1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[fec0::1]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://[::]/x.jar' || 'redirect to an address of this computer or of its network'
        'https://localhost.localdomain/x.jar' || 'redirect to an address of this computer or of its network'
        'https://ip6-localhost/x.jar' || 'redirect to an address of this computer or of its network'
        'https://ip6-loopback/x.jar' || 'redirect to an address of this computer or of its network'
        'https://LOCALHOST/x.jar' || 'redirect to an address of this computer or of its network'
    }

    @Unroll
    void "a redirect to #host, an address out there, is followed"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(302, "https://${host}/x.jar", null) }
        answers["https://${host}/x.jar".toString()] = { new Reply(200, null, new ByteArrayInputStream(jar)) }

        expect:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null).artifact.sha256 == digest(jar, 'SHA-256')

        where:
        host << ['93.184.216.34', '172.32.0.1', '100.128.0.1', '198.20.0.1', '192.0.1.1', '[2606:2800:220:1::1]', '[64:ff9b::5db8:d822]', '[2002:5db8:d822::1]', 'localhost.example']
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

    void "what watches the time of a file is ended when an error ends the file, and interrupts nothing later"() {
        given:
        answers[JAR] = { new Reply(200, null, new Asserting()) }

        when: 'the error comes well within the time an answer has'
        new PackArtifacts(transport, Duration.ofSeconds(30), Duration.ofSeconds(60))
                .resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then: 'it is what ends the file'
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + 'https://repository.invalid/maven/: cannot be asked (AssertionError)' + HINT

        when: 'the same, where an answer has a moment, so that a watch that was not ended comes while the thread goes on'
        new PackArtifacts(transport, Duration.ofMillis(300), Duration.ofSeconds(60))
                .resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then: 'the error ended the file, or on a busy machine the watch did'
        thrown(PackArtifactException)

        when: 'the thread goes on for longer than an answer has'
        Thread.sleep(1500)

        then:
        noExceptionThrown()
        !Thread.currentThread().isInterrupted()
    }

    void "an answer that overflows the stack of what reads it is a failure of the answer, and an error of the machine is not"() {
        given:
        answers[JAR] = { throw new StackOverflowError() }

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == NOT_TAKEN + 'https://repository.invalid/maven/: cannot be asked (StackOverflowError)' + HINT

        when:
        answers[JAR] = { throw new InternalError('of the machine') }
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), REPOSITORY, null)

        then:
        thrown(InternalError)
    }

    @Unroll
    void "the metadata of a snapshot that #what is a failure that names the file"() {
        given:
        String directory = 'https://repository.invalid/maven/com/acme/acme-audit-grails/2.3.0-SNAPSHOT/'
        answers[directory + 'maven-metadata.xml'] = answer
        // a moment for the answer that stops coming, which the time ends; the others are read well within theirs
        Duration answerTime = reason == ': timeout' ? Duration.ofMillis(300) : Duration.ofSeconds(30)

        when:
        new PackArtifacts(transport, answerTime, Duration.ofSeconds(60))
                .resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0-SNAPSHOT'), REPOSITORY, null)

        then:
        PackArtifactException e = thrown()
        e.message == 'The feature pack of com.acme:acme-audit-grails:2.3.0-SNAPSHOT was not taken from a repository. ' +
                "https://repository.invalid/maven/: maven-metadata.xml${reason}" + HINT
        asked*.toString() == [directory + 'maven-metadata.xml']

        where:
        what                           | answer                                                                                    || reason
        'stops coming'                 | ({ new Reply(200, null, new PipedInputStream(new PipedOutputStream())) })                 || ': timeout'
        'is an error of the server'    | ({ new Reply(500, null, null) })                                                          || ': HTTP status 500'
        'cannot be asked for'          | ({ throw new IllegalStateException('x') })                                                || ': cannot be asked (IllegalStateException)'
        'breaks what reads it'         | ({ new Reply(200, null, new Asserting()) })                                               || ': cannot be asked (AssertionError)'
        'is nested without end'        | ({ new Reply(200, null, text("<metadata><groupId>${'<a>' * 36000}${'</a>' * 36000}</groupId></metadata>")) }) || ' is not the metadata of a Maven repository'
        'is nested under a value'      | ({ new Reply(200, null, text('<metadata><versioning><snapshotVersions><snapshotVersion><extension>jar</extension><value>' +
                                              "${'<a>' * 36000}${'</a>' * 36000}</value></snapshotVersion></snapshotVersions></versioning></metadata>")) }) || ' is not the metadata of a Maven repository'
        'has elements without end'     | ({ new Reply(200, null, text("<metadata>${'<a/>' * 30000}</metadata>")) })                || ' is not the metadata of a Maven repository'
        'is a page of something else'  | ({ new Reply(200, null, text('<html><head><title>Sign in</title></head><body><form/></body></html>')) }) || ' is not the metadata of a Maven repository'
        'has a text without end'       | ({ new Reply(200, null, text("<metadata><groupId>${'g' * 5000}</groupId></metadata>")) }) || ' is not the metadata of a Maven repository'
        'has a document type'          | ({ new Reply(200, null, text('<!DOCTYPE metadata [<!ENTITY x "y">]><metadata><groupId>&x;</groupId></metadata>')) }) || ' cannot be read'
        'is not XML'                   | ({ new Reply(200, null, text('not xml')) })                                               || ' cannot be read'
    }

    void "of the metadata of a snapshot only the text that is directly in an element is read"() {
        given:
        byte[] jar = jar()
        String directory = 'https://repository.invalid/maven/com/acme/acme-audit-grails/2.3.0-SNAPSHOT/'
        answers[directory + 'maven-metadata.xml'] = { new Reply(200, null, text('<?xml version="1.0" encoding="UTF-8"?>\n<metadata modelVersion="1.1.0">' +
                '<groupId>com.<x>other</x>acme</groupId><artifactId><![CDATA[acme-audit-grails]]></artifactId><version> 2.3.0-SNAPSHOT </version>' +
                '<versioning><snapshot><timestamp>20261119.101112</timestamp><buildNumber>7</buildNumber></snapshot><lastUpdated>20261119101112</lastUpdated>' +
                '</versioning></metadata>')) }
        answers[directory + 'acme-audit-grails-2.3.0-20261119.101112-7.jar'] = { new Reply(200, null, new ByteArrayInputStream(jar)) }

        expect:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0-SNAPSHOT'), REPOSITORY, null)
                .artifact.file == 'acme-audit-grails-2.3.0-20261119.101112-7.jar'
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
        PackArtifacts.repositories([], WINDOWS ? 'mavenLocal();C:\\srv\\maven;file:///C:/srv/more' : 'mavenLocal();/srv/maven;file:///srv/more', warnings)*.scheme ==
                ['file', 'file', 'file', 'https']
        PackArtifacts.repositories([], 'mavenLocal()', warnings)[0].toString().endsWith('/')
        warnings.isEmpty()
    }

    @Requires({ os.windows })
    @Unroll
    void "on Windows a path without a drive is not absolute, also as an address, and a share of another computer is a directory: #entry"() {
        given:
        List<String> warnings = []

        when:
        List<URI> repositories = PackArtifacts.repositories([], "https://nexus.invalid/releases;${entry}", warnings)

        then:
        repositories*.toString() == ['https://nexus.invalid/releases/'] + used + ['https://repo.maven.apache.org/maven2/']
        warnings.size() == (used ? 0 : 1)
        used || warnings[0].contains('is a path that is not absolute: it is one of the generated application, and is not read from where the command runs')

        where:
        entry                    || used
        '/srv/maven'             || []
        '\\srv\\maven'           || []
        'file:///srv/more'       || []
        'file:/srv/more'         || []
        'C:\\srv\\maven'         || ['file:///C:/srv/maven/']
        'file:///C:/srv/more'    || ['file:///C:/srv/more/']
        'file://server/share'    || ['file://server/share/']
        '\\\\server\\share\\m2'  || ['file://server/share/m2/']
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
        ['alice', 'Xq7', 'Zk9', 'sec'].every { String part -> !warnings[0].contains(part) }

        where:
        entry                                          || reason
        'https://user:secret@nexus.invalid/private'    || "'https://nexus.invalid/private' is not a repository a feature pack is taken from: the address has a user or password, and credentials are not sent"
        'https://user:sec ret@nexus.invalid/private'   || 'An address that is not shown (https, 42 characters) is not the address of a repository'
        'https://alice:Xq7/Zk9@host.invalid/x'         || 'An address that is not shown (https, 36 characters) is not a repository a feature pack is taken from'
        'https://alice:Xq7#Zk9@host.invalid/x'         || 'An address that is not shown (https, 36 characters) is not a repository a feature pack is taken from'
        'https://alice:Xq7?Zk9@host.invalid/x'         || 'An address that is not shown (https, 36 characters) is not a repository a feature pack is taken from'
        'https://alice:8080/Xq7Zk9@host.invalid/x'     || 'the address has a user or password, and credentials are not sent'
        'https://alice:Xq7Zk9%40host.invalid/x'        || 'An address that is not shown (https, 37 characters) is not a repository a feature pack is taken from'
        'https:/alice:Xq7Zk9@host.invalid/x'           || 'An address that is not shown (https, 34 characters) is not a repository a feature pack is taken from'
        'https:alice:Xq7Zk9@host.invalid/x'            || 'An address that is not shown (https, 33 characters) is not a repository a feature pack is taken from'
        'alice:Xq7Zk9@host.invalid/x'                  || 'An address that is not shown (27 characters) is not a repository a feature pack is taken from'
        'mavenLocal'                                   || 'An address that is not shown (10 characters) is a path that is not absolute'
        'mavenlocal()'                                 || 'An address that is not shown (12 characters) is a path that is not absolute'
        'mavenCentral'                                 || 'An address that is not shown (12 characters) is a path that is not absolute'
        'relative/dir'                                 || 'An address that is not shown (12 characters) is a path that is not absolute'
        '../maven'                                     || 'An address that is not shown (8 characters) is a path that is not absolute'
        'https://2130706433/releases'                  || 'its host or port is not one a request can be made to'
        'https://nexus.invalid/private?token=secret'   || "'https://nexus.invalid/private' is not a repository a feature pack is taken from: the address cannot have a query or a fragment"
        'http://nexus.invalid/releases'                || 'the address has to be https, or file: for a directory'
        'mailto:x'                                     || 'the address has to be https, or file: for a directory'
        'jar:file:/x.jar!/'                            || 'the address has to be https, or file: for a directory'
        // an address with the name of a computer is a share on Windows, where it is a directory, and nothing elsewhere
        (WINDOWS ? 'file://server/share#part' : 'file://server/share') || 'is not the address of a repository or of a directory'
        'file:///srv/maven?secret=1'                   || 'is not the address of a repository or of a directory'
        'https://nexus.invalid:99999/releases'         || 'its host or port is not one a request can be made to'
    }

    void "the entries of GRAILS_REPO_URL that are left out are told of in a few lines, however many they are"() {
        given:
        List<String> warnings = []

        when: 'fifty entries that cannot be asked: one path forty times, and ten others'
        PackArtifacts.repositories([], ((['relative/dir'] * 40) + (1..10).collect { int each -> 'r' * each }).join(';'), warnings)

        then:
        warnings.size() == PackArtifacts.MAX_WARNINGS + 1
        warnings[0].startsWith('Entries 1, 2, 3 and 37 more of GRAILS_REPO_URL are not asked for the feature pack. ')
        warnings[1].startsWith('Entry 41 of GRAILS_REPO_URL is not asked for the feature pack. ')
        warnings.last() == '6 more entries of GRAILS_REPO_URL are not asked for the feature pack'
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
        'https://2130706433/m'                 || 'A host is a name, or an address written as four numbers from 0 to 255 with dots, or in brackets'
        'https://0x7f.1/m'                     || 'its host or port is not one a request can be made to'
        'https://010.0.0.1/m'                  || 'its host or port is not one a request can be made to'
        'https://alice:8080/secret@host/m'     || 'the address has a user or password, and credentials are not sent'
        "https://b${(char) 0xFC}cher.invalid/m" || 'a host name with other characters in its ASCII form, which begins with xn--'
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

    private static InputStream text(String text) {
        return new ByteArrayInputStream(text.getBytes('UTF-8'))
    }

    static class Asserting extends InputStream {

        @Override
        int read() {
            throw new AssertionError('what the other side says')
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
