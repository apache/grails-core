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
import org.grails.forge.util.VersionInfo
import spock.lang.Requires
import spock.lang.Unroll

/**
 * The {@code gradle/wrapper/gradle-wrapper.properties} of a generated application names the distribution of Gradle
 * together with its SHA-256, so that the Gradle wrapper refuses a download that is not that distribution. Both are
 * written by the build from the gradle.properties of the root of the repository, the checksum
 * ({@code gradleDistributionSha256Sum}) beside the version ({@code gradleToolingApiVersion}), so that a change of the
 * one is seen with the other; {@code -PverifyGradleChecksums} compares the checksum with the one Gradle publishes for
 * the version, which fails a version that was changed without its checksum.
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
        url.group(1) == VersionInfo.gradleVersion
        wrapper.getProperty('distributionSha256Sum') == VersionInfo.gradleDistributionSha256Sum

        where:
        type << ApplicationType.values()
    }

    void "the checksum is a SHA-256 as Gradle publishes it and as the wrapper compares it"() {
        expect:
        VersionInfo.gradleDistributionSha256Sum ==~ /[0-9a-f]{64}/
    }

    @Requires(value = { System.getProperty('forge.gradleChecksums.verify') == 'true' },
            reason = 'downloads the checksum Gradle publishes, and is asked for with -PverifyGradleChecksums')
    void "the checksum is the one Gradle publishes for the distribution of the version"() {
        given:
        String version = VersionInfo.gradleVersion

        when:
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build()
        HttpResponse<String> published = client.send(HttpRequest.newBuilder(URI.create("https://services.gradle.org/distributions/gradle-${version}-bin.zip.sha256"))
                .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString())

        then:
        published.statusCode() == 200
        published.body().trim() == VersionInfo.gradleDistributionSha256Sum
    }

    private static Properties properties(String text) {
        Properties read = new Properties()
        read.load(new StringReader(text))
        return read
    }
}
