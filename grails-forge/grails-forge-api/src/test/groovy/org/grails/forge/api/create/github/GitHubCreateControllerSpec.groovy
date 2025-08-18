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

package org.grails.forge.api.create.github

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.grails.forge.util.GitHubUtil
import spock.lang.Ignore
import spock.lang.Retry
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths

@Ignore
@Retry
class GitHubCreateControllerSpec extends Specification {

    static Map<String, Object> getConfiguration(MapEntry ...entries) {
        Map<String, Object> m = [:]
        m.put("spec.name", "GitHubCreateControllerSpec")
        m.put("micronaut.server.port", "\${random.port}")
        m.put("micronaut.http.client.read-timeout", "20s")  // needs huge timeout otherwise it fails in GitHub CI
        m.put("micronaut.http.services.github-oauth.url", "http://localhost:\${micronaut.server.port}")
        m.put("micronaut.http.services.github-api-v3.url", "http://localhost:\${micronaut.server.port}")
        m.put("grails.forge.github.clientId", "clientId")
        m.put("grails.forge.github.clientSecret", "clientSecret")
        entries.each {m.put((String) it.getKey(), it.getValue())}
        return m
    }

    void "returns redirect with error when repository exists"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                getConfiguration(new MapEntry("micronaut.http.client.follow-redirects", false)))

        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

        when:
        HttpResponse response = httpClient.toBlocking().exchange(HttpRequest.GET("/github/web/foo.bar.existing?code=123&state=123"))

        then:
        response.getStatus().code == 307
        response.header(HttpHeaders.LOCATION).contains("error")
        response.header(HttpHeaders.LOCATION).startsWith("https://start.grails.org")

        cleanup:
        embeddedServer.close()
    }

    void "returns redirect to launcher if redirectUri is configured"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                getConfiguration(new MapEntry("micronaut.http.client.follow-redirects", false)))

        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

        when:
        HttpResponse response = httpClient.toBlocking().exchange(HttpRequest.GET("/github/web/foo?code=123&state=123"))

        then:
        response.getStatus().code == 307
        response.header(HttpHeaders.LOCATION).contains("cloneUrl")
        response.header(HttpHeaders.LOCATION).contains("htmlUrl")
        response.header(HttpHeaders.LOCATION).contains("url")
        response.header(HttpHeaders.LOCATION).startsWith("https://start.grails.org")

        cleanup:
        embeddedServer.close()
    }

    void "returns github repository details when launcher missing"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                getConfiguration(new MapEntry("grails.forge.redirectUri", "")))

        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

        when:
        GitHubCreateDTO dto = httpClient.toBlocking().retrieve(HttpRequest.GET("/github/web/foo?code=123&state=123"), GitHubCreateDTO.class)

        then:
        Path clonePath = Paths.get(new URL(dto.cloneUrl).toURI())
        Git bareRepo = Git.open(clonePath.toFile())
        List<RevCommit> commits = bareRepo.log().call().toList()
        commits.size() == 1
        commits.get(0).fullMessage == GitHubUtil.INIT_COMMIT_MESSAGE
        commits.get(0).authorIdent.name == "name"
        commits.get(0).authorIdent.emailAddress == "email"

        cleanup:
        embeddedServer.close()
        clonePath.toFile().deleteDir()
    }
}
