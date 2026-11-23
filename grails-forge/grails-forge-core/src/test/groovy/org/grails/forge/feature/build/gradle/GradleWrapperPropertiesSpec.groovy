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
package org.grails.forge.feature.build.gradle

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.regex.Matcher
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import spock.lang.Requires
import spock.lang.Unroll

/**
 * The {@code gradle/wrapper/gradle-wrapper.properties} of a generated application names the distribution of Gradle
 * together with its SHA-256, so that the Gradle wrapper refuses a download that is not that distribution. The
 * checksum is held to the version by {@code gradle-distribution-sha256.properties} beside this spec, which has the
 * checksum Gradle publishes for each version: a version that is changed without its checksum fails here, and
 * {@code -PverifyGradleChecksums} compares that file with what Gradle publishes.
 */
class GradleWrapperPropertiesSpec extends ApplicationContextSpec implements CommandOutputFixture {

    static final String WRAPPER_PROPERTIES = 'gradle/wrapper/gradle-wrapper.properties'

    @Unroll
    void "the Gradle wrapper of #type names the binary distribution of Gradle with its SHA-256"() {
        when:
        Properties wrapper = properties(generate(type, [])[WRAPPER_PROPERTIES])
        Matcher url = wrapper.getProperty('distributionUrl') =~ /^https:\/\/services\.gradle\.org\/distributions\/gradle-([0-9][0-9A-Za-z.-]*)-bin\.zip$/

        then:
        url.matches()
        wrapper.getProperty('distributionSha256Sum') ==~ /[0-9a-f]{64}/

        and: 'the checksum is the one Gradle publishes for that version'
        checksums().getProperty(url.group(1)) != null
        wrapper.getProperty('distributionSha256Sum') == checksums().getProperty(url.group(1))

        where:
        type << ApplicationType.values()
    }

    @Requires(value = { System.getProperty('forge.gradleChecksums.verify') == 'true' },
            reason = 'downloads the checksums Gradle publishes, and is asked for with -PverifyGradleChecksums')
    @Unroll
    void "the checksum of Gradle #version is the one Gradle publishes"() {
        when:
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
        HttpResponse<String> published = client.send(HttpRequest.newBuilder(URI.create("https://services.gradle.org/distributions/gradle-${version}-bin.zip.sha256"))
                .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString())

        then:
        published.statusCode() == 200
        published.body().trim() == checksum

        where:
        version << checksums().stringPropertyNames().toSorted()
        checksum = checksums().getProperty(version)
    }

    void "the table of checksums has one SHA-256 for each version"() {
        expect:
        !checksums().isEmpty()
        checksums().stringPropertyNames().every { String version -> version ==~ /[0-9][0-9A-Za-z.-]*/ && checksums().getProperty(version) ==~ /[0-9a-f]{64}/ }
    }

    private static Properties checksums() {
        InputStream table = GradleWrapperPropertiesSpec.getResourceAsStream('gradle-distribution-sha256.properties')
        assert table != null
        return table.withCloseable { InputStream it -> properties(it.getText('ISO-8859-1')) }
    }

    private static Properties properties(String text) {
        Properties read = new Properties()
        read.load(new StringReader(text))
        return read
    }
}
