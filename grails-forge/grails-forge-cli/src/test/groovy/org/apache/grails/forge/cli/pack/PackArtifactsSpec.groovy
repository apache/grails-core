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

    List<URI> asked = []
    Map<String, Closure<Reply>> answers = [:]
    PackArtifacts artifacts = new PackArtifacts({ URI address ->
        asked << address
        Closure<Reply> answer = answers[address.toString()]
        return answer == null ? new Reply(404, null, null) : answer.call()
    } as Transport)

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

        and: 'the jar and the checksums of it, and neither a POM nor another jar'
        asked*.toString() == [JAR, JAR + '.sha256', JAR + '.sha1']
    }

    void "a redirect to an address that is not https is not followed"() {
        given:
        answers[JAR] = { new Reply(302, location, null) }

        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), [URI.create('https://repository.invalid/maven/')], null)

        then:
        PackArtifactException e = thrown()
        e.message == 'The feature pack of com.acme:acme-audit-grails:2.3.0 was not taken from a repository. ' +
                'https://repository.invalid/maven/: redirect to an address that is not https'
        asked*.toString() == [JAR]

        where:
        location << ['http://repository.invalid/maven/acme.jar', 'file:///etc/passwd', 'ftp://repository.invalid/acme.jar',
                     'https://user:secret@other.invalid/acme.jar', 'jar:https://other.invalid/acme.jar!/x']
    }

    void "a redirect to an https address is followed, and only so many of them"() {
        given:
        byte[] jar = jar()
        answers[JAR] = { new Reply(301, '/mirror/acme.jar', null) }
        answers['https://repository.invalid/mirror/acme.jar'] = { new Reply(307, 'https://other.invalid/acme.jar', null) }
        answers['https://other.invalid/acme.jar'] = { new Reply(200, null, new ByteArrayInputStream(jar)) }

        when:
        Pack pack = artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), [URI.create('https://repository.invalid/maven/')], null)

        then:
        pack.artifact.sha256 == digest(jar, 'SHA-256')
        pack.artifact.checkedAgainst == null

        when: 'a repository that sends on to itself'
        asked.clear()
        answers[JAR] = { new Reply(302, JAR, null) }
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), [URI.create('https://repository.invalid/maven/')], null)

        then:
        PackArtifactException e = thrown()
        e.message.endsWith('https://repository.invalid/maven/: more than 5 redirects')
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
        e.message == 'The feature pack of com.acme:acme-audit-grails:2.3.0 was not taken from a repository. ' +
                "https://first.invalid/maven/: not found; https://repository.invalid/maven/: ${reason} (1 more repository not asked)"
        !e.message.contains('what the other side says')
        !asked.any { URI address -> address.host == 'second.invalid' }

        where:
        failure                                                                    || reason
        ({ new Reply(500, null, null) })                                           || 'HTTP status 500'
        ({ new Reply(403, null, null) })                                           || 'HTTP status 403'
        ({ throw new HttpTimeoutException('what the other side says') })           || 'timeout'
        ({ throw new SocketTimeoutException('what the other side says') })         || 'timeout'
        ({ throw new SSLHandshakeException('what the other side says') })          || 'TLS failure (SSLHandshakeException)'
        ({ throw new ConnectException('what the other side says') })               || 'no connection (ConnectException)'
        ({ throw new IOException('what the other side says') })                    || 'cannot be read (IOException)'
        ({ new Reply(302, null, null) })                                           || 'redirect without an address'
        ({ new Reply(302, 'https://exa mple.invalid/', null) })                    || 'redirect to an address that cannot be read'
    }

    void "an address that is not https is never asked, whoever passes it"() {
        when:
        artifacts.resolve(PackArtifacts.coordinates('com.acme:acme-audit-grails:2.3.0'), [URI.create('http://repository.invalid/maven/')], null)

        then:
        PackArtifactException e = thrown()
        e.message.endsWith('http://repository.invalid/maven/: the address is not https')
        asked.isEmpty()
    }

    void "without a repository given it is those of GRAILS_REPO_URL and then Maven Central, and with one only what is given"() {
        expect:
        PackArtifacts.repositories([], null)*.toString() == ['https://repo.maven.apache.org/maven2/']
        PackArtifacts.repositories([], 'https://nexus.invalid/releases; mavenCentral() ;https://nexus.invalid/more/')*.toString() ==
                ['https://nexus.invalid/releases/', 'https://repo.maven.apache.org/maven2/', 'https://nexus.invalid/more/']
        PackArtifacts.repositories(['https://private.invalid/maven'], 'https://nexus.invalid/releases')*.toString() == ['https://private.invalid/maven/']

        and: 'the local repository of Maven and a directory are addresses of files'
        PackArtifacts.repositories([], 'mavenLocal();/srv/maven')*.scheme == ['file', 'file', 'https']
        PackArtifacts.repositories([], 'mavenLocal()')[0].toString().endsWith('/')

        when: 'a repository of the environment a generated application could not use either'
        PackArtifacts.repositories([], 'http://nexus.invalid/releases')

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('the address has to be https, or file: for a directory')
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
}
