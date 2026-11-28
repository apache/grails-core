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
package org.grails.forge.web

import com.github.difflib.UnifiedDiffUtils
import grails.testing.mixin.integration.Integration
import grails.web.mapping.cors.GrailsCorsConfiguration
import groovy.json.JsonSlurper
import org.apache.grails.forge.core.state.ProjectState
import org.grails.forge.util.VersionInfo
import org.grails.forge.template.GspView
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.support.AbstractResourceBasedMessageSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@Integration(applicationClass = Application)
class ForgeApiIntegrationSpec extends Specification {

    private static final String NEO4J_WITH_HIBERNATE7 = 'gorm-neo4j cannot be combined with gorm-hibernate7: an application with GORM for Neo4j uses ' +
            'grails-neo4j-bom, which manages Hibernate 5, and no Grails BOM manages the Neo4j driver of GORM for Neo4j together with Hibernate 7. ' +
            'Select gorm-hibernate5, or no Hibernate implementation, with gorm-neo4j.'

    @Value('${local.server.port}')
    int serverPort

    @Autowired
    GrailsCorsConfiguration grailsCorsConfiguration

    @Autowired
    AbstractResourceBasedMessageSource messageSource

    void "GET /versions returns version map"() {
        when:
        Map response = get('/versions')
        Map json = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        json.versions['grails.version']
    }

    void "GET /versions reports the end of support date outside the version map"() {
        when:
        Map json = json(get('/versions'))

        then:
        json.versions['grails.version'] == VersionInfo.getGrailsVersion()
        LocalDate.parse(json.endOfSupport as String) == VersionInfo.getEndOfSupport().get()
        !(json.versions as Map).containsKey('grails.endOfSupport')
    }

    void "GET application type endpoints expose the web application contract"() {
        when:
        Map listResponse = get('/application-types')
        Map typeResponse = get('/application-types/web')
        Map list = json(listResponse)
        Map web = json(typeResponse)

        then:
        listResponse.status == 200
        listResponse.contentType.startsWith('application/json')
        list.types.find { it.name == 'web' }
        typeResponse.status == 200
        typeResponse.contentType.startsWith('application/json')
        web.name == 'web'
        web.title
        web.description
        web.features instanceof List
    }

    void "GET feature endpoints distinguish available and default web features"() {
        when:
        Map featuresResponse = get('/application-types/web/features')
        Map defaultsResponse = get('/application-types/web/features/default')
        Map features = json(featuresResponse)
        Map defaults = json(defaultsResponse)

        then:
        featuresResponse.status == 200
        featuresResponse.contentType.startsWith('application/json')
        features.features
        features.features.any { it.name == 'gorm-mongodb' }
        !features.features.any { it.name == 'asset-pipeline-grails' }
        defaultsResponse.status == 200
        defaultsResponse.contentType.startsWith('application/json')
        defaults.features
        defaults.features.any { it.name == 'asset-pipeline-grails' }
    }

    void "GET feature endpoints retain query parameter compatibility"() {
        when:
        Map legacyGormResponse = get('/application-types/web/features?gorm=hibernate')
        Map invalidFilterResponse = get('/application-types/web/features?gorm=invalid&javaVersion=invalid')

        then:
        legacyGormResponse.status == 200
        json(legacyGormResponse).features
        invalidFilterResponse.status == 200
        json(invalidFilterResponse).features.any { it.name == 'gorm-mongodb' }
    }

    void "GET /create/web/:name returns a named zip archive"() {
        when:
        Map response = get('/create/web/forge-contract?features=gorm-mongodb&gorm=mongodb&reloading=devtools')

        then:
        response.status == 201
        response.contentType.startsWith('application/zip')
        response.contentDisposition.contains('forge-contract.zip')
        isZip(response.body as byte[])
        zipText(response.body as byte[], 'forge-contract/build.gradle').contains('org.apache.grails:grails-data-mongodb')
        zipText(response.body as byte[], 'forge-contract/build.gradle').contains('spring-boot-devtools')
        zipText(response.body as byte[], 'forge-contract/grails-app/init/forge/contract/Application.groovy')
        zipEntries(response.body as byte[]).every { it.startsWith('forge-contract/') }
    }

    void "an application with GORM for Neo4j is created with grails-neo4j-bom as its BOM of Grails: #query"() {
        when:
        Map response = get("/create/web/forge-neo4j?${query}")
        String buildGradle = zipText(response.body as byte[], 'forge-neo4j/build.gradle')

        then:
        response.status == 201
        buildGradle.contains('implementation platform("org.apache.grails:grails-neo4j-bom:$grailsVersion")')
        buildGradle.contains('classpath platform("org.apache.grails:grails-neo4j-bom:$grailsVersion")')
        !buildGradle.contains('org.apache.grails:grails-bom:')
        buildGradle.contains('org.apache.grails:grails-data-neo4j')

        where:
        query << ['gorm=neo4j', 'features=gorm-neo4j']
    }

    void "GET /:name.zip returns a zip archive for the default application type"() {
        when:
        Map response = get('/forge-default-contract.zip')

        then:
        response.status == 201
        response.contentType.startsWith('application/zip')
        response.contentDisposition.contains('forge-default-contract.zip')
        isZip(response.body as byte[])
    }

    void "GET /create preserves the application name and package in a qualified name ending in #appName"() {
        when:
        Map response = get("/create/web/com.example.${appName}?features=gradle-settings-file")

        then:
        response.status == 201
        response.contentType.startsWith('application/zip')
        response.contentDisposition == "attachment; filename=${appName}.zip"
        zipText(response.body as byte[], "${appName}/settings.gradle").contains("rootProject.name = \"${appName}\"")
        zipText(response.body as byte[], "${appName}/grails-app/init/com/example/Application.groovy").contains('package com.example')

        where:
        appName << ['demo', 'json']
    }

    void "GET /preview/:type/:name returns generated project contents as JSON"() {
        when:
        Map response = get('/preview/web/forge-preview?features=grails-quartz')
        Map preview = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        preview.contents['build.gradle']
        preview.contents['build.gradle'].contains('org.apache.grails:grails-quartz')
    }

    void "GET diff endpoints return plain text feature changes"() {
        when:
        Map appResponse = get('/diff/web/forge-diff?features=gorm-mongodb')
        Map featureResponse = get('/diff/web/feature/gorm-mongodb')
        String appDiff = text(appResponse)
        String featureDiff = text(featureResponse)

        then:
        appResponse.status == 200
        appResponse.contentType.startsWith('text/plain')
        appDiff.contains('+## Feature gorm-mongodb documentation')
        featureResponse.status == 200
        featureResponse.contentType.startsWith('text/plain')
        featureDiff.contains('+## Feature gorm-mongodb documentation')
    }

    void "a zip archive has the state of the project, with the baseline of every other file"() {
        when:
        byte[] zip = get('/create/web/com.example.forge-state?features=grails-quartz&servlet=jetty').body as byte[]
        List<String> entries = zipEntries(zip).findAll { !it.endsWith('/') }.collect { it - 'forge-state/' }
        List<String> application = entries.findAll { !it.startsWith('.grails/') }
        String manifest = zipText(zip, 'forge-state/.grails/project.yml')
        String lock = zipText(zip, 'forge-state/.grails/forge.lock.yml')
        String index = zipText(zip, 'forge-state/.grails/baseline/index.yml')

        then: 'the manifest and the lock'
        manifest.contains('applicationType: web\nname: forge-state\ndefaultPackage: com.example\n')
        manifest.contains('  servlet: jetty\n')
        manifest.contains('requestedFeatures:\n- name: grails-quartz\n')
        lock.contains('- name: grails-quartz\n  source: bundled-pack\n')
        lock.contains("manifest:\n  sha256: ${sha256(manifest.getBytes('UTF-8'))}\n")
        lock.contains("baselineIndex:\n  sha256: ${sha256(index.getBytes('UTF-8'))}\n")

        and: 'the lock ends with the digest of itself'
        lock.endsWith("seal:\n  sha256: ${sha256(lock.substring(0, lock.lastIndexOf('seal:\n')).getBytes('UTF-8'))}\n")

        and: 'the baseline is the files of the application, each once, and not the state; no copy is a file Git reads, and the record of earlier versions is not written'
        !application.contains('grails-forge-cli.yml')
        entries.findAll { it.startsWith('.grails/') }.toSorted() ==
                (['.grails/project.yml', '.grails/forge.lock.yml', '.grails/baseline/index.yml', '.grails/.gitattributes', '.grails/.gitignore'] +
                        application.collect { '.grails/baseline/files/' + (it in ['.gitignore', '.gitattributes'] ? it + '.baseline' : it) }).toSorted()
        application.every { index.contains("- path: ${it}\n") }
        index.contains("- path: build.gradle\n  sha256: ${sha256(zipText(zip, 'forge-state/build.gradle').getBytes('UTF-8'))}\n  kind: text\n  executable: false\n")
        index.contains("- path: gradlew\n  sha256: ${sha256(zipText(zip, 'forge-state/gradlew').getBytes('UTF-8'))}\n  kind: binary\n  executable: true\n")
        zipText(zip, 'forge-state/.grails/baseline/files/build.gradle') == zipText(zip, 'forge-state/build.gradle')
        zipText(zip, 'forge-state/.gitignore').contains('.grails/cache/\n')
        zipText(zip, 'forge-state/.gitattributes').contains('*.bat text eol=crlf\n')
    }

    void "the difference the service gives for a feature, applied to the project it gives without it, leaves a project whose state reads"() {
        given: 'the project without the feature, as it is downloaded'
        Path project = Files.createTempDirectory('forgediff')
        unzipTo(get('/create/web/com.example.forge-state').body as byte[], project)
        Path root = project.resolve('forge-state')
        byte[] with = get('/create/web/com.example.forge-state?features=grails-quartz').body as byte[]

        when: 'the difference is applied as patch applies it'
        String diff = text(get('/diff/web/com.example.forge-state?features=grails-quartz'))
        List<String> changed = applyDiff(root, diff)
        ProjectState state = ProjectState.read(root)
        state.verifyBaseline(root)

        then: 'the files of the application are those of the project with the feature'
        changed.containsAll(['build.gradle', 'README.md', 'grails-app/conf/application.yml'])
        !changed.any { it.startsWith('.grails') }
        changed.every { root.resolve(it).toFile().getText('UTF-8').trim() == zipText(with, 'forge-state/' + it).trim() }

        and: 'the state is the one the project was generated with, to which the changed files are changed files'
        !state.lock.featureNames.contains('grails-quartz')
        !state.isAsGenerated(root, 'build.gradle')
        state.isAsGenerated(root, 'gradlew')
        state.isAsGenerated(root, '.gitattributes')

        cleanup:
        project?.toFile()?.deleteDir()
    }

    void "the state of a project does not depend on who asks for it"() {
        when:
        List<Map<String, String>> states = [
                [:],
                ['User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15'],
                ['User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'],
                ['User-Agent': 'curl/8.7.1']
        ].collect { Map<String, String> headers ->
            byte[] zip = get('/create/web/com.example.forge-state?features=grails-quartz', headers).body as byte[]
            zipEntries(zip).findAll { it.startsWith('forge-state/.grails/') && !it.endsWith('/') }.collectEntries { [(it): sha256(zipText(zip, it).getBytes('ISO-8859-1'))] }
        }

        then:
        states[0].size() > 60
        !zipText(get('/create/web/com.example.forge-state').body as byte[], 'forge-state/.grails/forge.lock.yml').contains('operatingSystem')
        states.every { it == states[0] }
    }

    void "a preview shows the manifest and the lock of the project, and not the baseline"() {
        when:
        Map contents = json(get('/preview/web/com.example.forge-state?features=grails-quartz')).contents as Map

        then:
        (contents['.grails/project.yml'] as String).contains('requestedFeatures:\n- name: grails-quartz\n')
        (contents['.grails/forge.lock.yml'] as String).contains('- name: grails-quartz\n  source: bundled-pack\n')
        contents.containsKey('build.gradle')
        !contents.keySet().any { (it as String).startsWith('.grails/baseline') }
    }

    void "a diff shows what a feature changes in the application, and nothing of the state of the project"() {
        when: 'the manifest and the lock name each other by digest: a difference of them cannot be applied by hand'
        String diff = text(get('/diff/web/forge-state?features=grails-quartz'))
        String feature = text(get('/diff/web/feature/grails-quartz'))

        then:
        diff.contains('+++ build.gradle')
        feature.contains('+++ build.gradle')
        !(diff + feature).readLines().any { it.startsWith('+++ .grails') || it.startsWith('--- .grails') || it.contains('sha256') }
    }

    void "GET /preview preserves the application name and package in a qualified name ending in #appName"() {
        when:
        Map response = get("/preview/web/com.example.${appName}?features=gradle-settings-file")
        Map preview = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        preview.contents['settings.gradle'].contains("rootProject.name = \"${appName}\"")
        preview.contents['grails-app/init/com/example/Application.groovy'].contains('package com.example')

        where:
        appName << ['demo', 'json']
    }

    void "GET /diff preserves the application name in a qualified name ending in #appName"() {
        when:
        Map response = get("/diff/web/com.example.${appName}?features=gradle-settings-file")
        String diff = text(response)

        then:
        response.status == 200
        response.contentType.startsWith('text/plain')
        diff.contains('+++ settings.gradle')
        diff.contains("+rootProject.name = \"${appName}\"")

        where:
        appName << ['demo', 'json']
    }

    void "GET /select-options exposes every supported option group"() {
        when:
        Map response = get('/select-options')
        Map options = json(response)

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        options.keySet().containsAll(['type', 'jdkVersion', 'lang', 'reloading', 'gorm', 'servlet'])
        options.values().every { Object group ->
            group instanceof Map && group.options && group.defaultOption
        }
        options.type.options.any { it.name == 'web' }
        (options.reloading.options*.value as Set) == (['DEVTOOLS', 'JREBEL', 'NONE'] as Set)
        options.lang.options*.value == ['GROOVY']
        options.type.options*.value.containsAll(['WEB', 'REST_API', 'PLUGIN', 'WEB_PLUGIN', 'REST_API_PLUGIN'])
    }

    void "GET / returns the plain text API description"() {
        when:
        Map response = get('/')

        then:
        response.status == 200
        response.contentType.startsWith('text/plain')
        text(response).contains('/application-types')
        text(response).contains('/v3/api-docs')
        text(response).contains('/swagger-ui/index.html')
    }

    void "JSON responses expose HAL _links and nothing of the Groovy objects behind them"() {
        when:
        Map json = json(get('/application-types'))

        then:
        json._links
        json._links.self
        !json.containsKey('links')
        !json.containsKey('metaClass')
        json.types.every { Map type -> !type.containsKey('metaClass') }
    }

    void "POST to a GET-only endpoint is rejected"() {
        expect:
        exchange('POST', '/versions').status == 405
    }

    void 'HEAD #path returns the GET status and content type without a body'() {
        when:
        Map response = exchange('HEAD', path)

        then:
        response.status == status
        response.contentType.startsWith(contentType)
        response.body.length == 0

        where:
        path                                      | status | contentType
        '/versions'                               | 200    | 'application/json'
        '/application-types'                      | 200    | 'application/json'
        '/application-types/web'                  | 200    | 'application/json'
        '/application-types/web/features'         | 200    | 'application/json'
        '/application-types/web/features/default' | 200    | 'application/json'
        '/select-options'                         | 200    | 'application/json'
        '/preview/web/demo'                       | 200    | 'application/json'
        '/diff/web/demo?features=gradle-settings-file' | 200 | 'text/plain'
        '/diff/web/feature/gorm-mongodb'           | 200    | 'text/plain'
        '/'                                       | 200    | 'text/plain'
        '/create/web/demo'                        | 201    | 'application/zip'
        '/demo.zip'                               | 201    | 'application/zip'
    }

    void 'generation endpoint #path rejects invalid #query instead of using a default'() {
        when:
        Map response = get("${path}?${query}")
        Map error = json(response)

        then:
        response.status == 400
        response.contentType.startsWith('application/json')
        error.message.startsWith('Invalid ')
        error._embedded.errors[0].message == error.message

        where:
        [path, query] << [
                ['/create/web/demo', '/demo.zip', '/preview/web/demo', '/diff/web/demo', '/diff/web/feature/gorm-mongodb'],
                ['gorm=invalid', 'gorm=hibernate6', 'gorm=', 'servlet=FOO', 'reloading=FOO', 'javaVersion=JDK_99']
        ].combinations()
    }

    void 'create endpoint #path rejects an invalid build tool'() {
        when:
        Map response = get("${path}?build=invalid")

        then:
        response.status == 400
        json(response).message.contains('Invalid build tool selection')

        where:
        path << ['/create/web/demo', '/demo.zip']
    }

    void 'generation endpoint #path explains conflicting features'() {
        when:
        Map response = get("${path}?features=mysql,postgres")
        Map error = json(response)

        then:
        response.status == 400
        error.message.contains('There can only be one of the following features selected')
        error.message.contains('mysql')
        error.message.contains('postgres')
        error._embedded.errors[0].message == error.message

        where:
        path << ['/create/web/demo', '/demo.zip', '/preview/web/demo', '/diff/web/demo']
    }

    void 'plugin defaults include Mockito and Spock and feature listings hide internal features'() {
        when:
        Map defaults = json(get('/application-types/plugin/features/default'))
        Map available = json(get('/application-types/plugin/features'))

        then:
        defaults.features*.name.containsAll(['mockito', 'spock'])
        !available.features*.name.contains('base')
        !json(get('/application-types/plugin')).features*.name.contains('base')
    }

    void 'Accept-Language selects localized application and feature descriptions'() {
        given:
        String[] originalBasenames = messageSource.basenameSet as String[]
        messageSource.addBasenames('api-test-messages')

        when:
        Map type = json(get('/application-types/web', ['Accept-Language': 'fr']))
        Map options = json(get('/select-options', ['Accept-Language': 'fr']))

        then:
        type.description == 'Application web de test'
        type.features.find { it.name == 'grails-quartz' }.description == 'Planification de test'
        options.type.options.find { it.name == 'web' }.description == 'Application web de test'
        json(get('/application-types/web', ['Accept-Language': 'en'])).description != type.description

        cleanup:
        messageSource.setBasenames(originalBasenames)
    }

    void 'generator pages neither inherit the HTTP request model nor replace its writer'() {
        given:
        RequestAttributes previous = RequestContextHolder.getRequestAttributes()
        MockHttpServletRequest request = new MockHttpServletRequest()
        request.setAttribute('serverURL', 'https://request-only.example')
        request.addParameter('grailsVersion', 'request-only-version')
        request.getSession().setAttribute('serverURL', 'https://session-only.example')
        GrailsWebRequest webRequest = new GrailsWebRequest(request, new MockHttpServletResponse(), new MockServletContext())
        StringWriter writer = new StringWriter()
        webRequest.out = writer
        RequestContextHolder.setRequestAttributes(webRequest)

        when:
        String rendered = GspView.of('/forge/template/api/grailsForgeApi.gsp', [:]).render()

        then:
        !rendered.contains('request-only')
        !rendered.contains('session-only')
        GrailsWebRequest.lookup().out.is(writer)
        writer.toString().empty

        cleanup:
        RequestContextHolder.setRequestAttributes(previous)
    }

    void "CORS allows the Forge UI origin"() {
        expect:
        grailsCorsConfiguration.enabled
        grailsCorsConfiguration.corsConfigurations['/**'].allowedOrigins.contains(origin)
        get('/versions', ['Origin': origin]).accessControlAllowOrigin == origin

        where:
        origin << [
                'https://start.grails.org',
                'https://grails.github.io',
                'https://grails.apache.org'
        ]
    }

    void 'client errors from #path explain the rejected request'() {
        when:
        Map response = get(path)
        Map error = json(response)

        then:
        response.status == 400
        response.contentType.startsWith('application/json')
        error.message.contains(reason)
        error._embedded.errors[0].message == error.message

        where:
        path                                                  | reason
        '/application-types/not-a-type'                        | 'Unknown application type'
        '/application-types/not-a-type/features'               | 'Unknown application type'
        '/application-types/not-a-type/features/default'       | 'Unknown application type'
        '/create/web/bad!'                                     | 'Invalid application name'
        '/preview/web/bad!'                                    | 'Invalid application name'
        '/diff/web/bad!'                                       | 'Invalid application name'
        '/create/web/demo?features=not-a-real-feature'          | 'requested feature does not exist'
        '/preview/web/demo?features=not-a-real-feature'         | 'requested feature does not exist'
        '/diff/web/demo?features=not-a-real-feature'            | 'requested feature does not exist'
        '/create/web/demo?features=com.acme:acme-audit-grails:2.3.0'  | 'requested feature does not exist: com.acme:acme-audit-grails:2.3.0'
        '/create/web/demo?features=acme-audit'                 | 'requested feature does not exist: acme-audit'
        '/create/web/demo?features=com.acme:acme-audit-grails%23acme-audit' | 'requested feature does not exist: com.acme:acme-audit-grails#acme-audit'
        '/create/web/com.aux.shop'                             | 'The package com.aux has the part aux, which is the name of a device on Windows, so the project could not be checked out there'
        '/preview/web/com.aux.shop'                            | 'The package com.aux has the part aux, which is the name of a device on Windows, so the project could not be checked out there'
        '/diff/web/com.aux.shop'                               | 'The package com.aux has the part aux, which is the name of a device on Windows, so the project could not be checked out there'
        '/create/web/con.acme.shop'                            | 'The package con.acme has the part con, which is the name of a device on Windows'
        '/create/web/com.acme.nul'                             | 'The name nul is the name of a device on Windows, so a directory of that name could not be made there'
        '/create/web/com.example.sh%0Aop'                      | 'Invalid application name'
        '/preview/web/com.ex%1B%5B31mample.shop'               | 'Invalid application name'
        '/diff/web/com.example.sh%7Cop'                        | 'Invalid application name'
        '/create/web/com.example.sh:op'                        | 'Invalid application name'
        '/create/web/com.example..shop'                        | 'Cannot create a valid package name'
        "/create/web/${(['p'] * 30).join('.')}.shop"           | 'which is not a path the state of the project can name'
        "/preview/web/${(['p'] * 30).join('.')}.shop"          | 'which is not a path the state of the project can name'
        '/preview/web/demo?features=com.acme:acme-audit-grails:2.3.0' | 'requested feature does not exist: com.acme:acme-audit-grails:2.3.0'
        '/diff/web/demo?features=com.acme:acme-audit-grails:2.3.0'    | 'requested feature does not exist: com.acme:acme-audit-grails:2.3.0'
        '/create/web/micronaut'                                | 'not a valid app name'
        '/preview/web/micronaut'                               | 'not a valid app name'
        '/diff/web/micronaut'                                  | 'not a valid app name'
        '/diff/web/feature/gorm-mongodb?name=micronaut'          | 'not a valid app name'
        '/diff/web/feature/gorm-mongodb?name=bad!'               | 'Invalid application name'
        '/create/not-a-type/demo'                              | 'Unknown application type'
        '/preview/not-a-type/demo'                             | 'Unknown application type'
        '/diff/not-a-type/demo'                                | 'Unknown application type'
        '/demo.zip?type=not-a-type'                            | 'Unknown application type'
    }

    void "generated links honor X-Forwarded-Proto"() {
        when:
        Map json = json(get('/application-types', [
                'X-Forwarded-Host': 'public.example',
                'X-Forwarded-Proto': 'https'
        ]))

        then:
        json._links.self.href.startsWith('https://public.example')
    }

    void "GET / with Accept text/html permanently redirects to the bound UI URL"() {
        when:
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${serverPort}/"))
                .timeout(Duration.ofSeconds(120))
                .header('Accept', 'text/html')
                .GET()
                .build()
        HttpResponse<byte[]> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofByteArray())

        then:
        response.statusCode() == 301
        response.headers().firstValue('Location').orElse('').startsWith('https://start.grails.org')
    }

    void "CORS allowed origins have placeholders resolved"() {
        expect:
        grailsCorsConfiguration.corsConfigurations['/**'].allowedOrigins.every { String origin ->
            !origin.contains('${')
        }
    }

    void "the OpenAPI description is derived from the URL mappings and the controllers"() {
        when:
        Map response = get('/v3/api-docs')
        Map spec = json(response)
        Map create = (spec.paths['/create/{type}/{name}'] as Map).get('get') as Map
        Map features = (spec.paths['/application-types/{type}/features'] as Map).get('get') as Map
        Map typeList = (spec.components as Map).schemas['ApplicationTypeList'] as Map

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        spec.openapi
        spec.info.title == 'Grails Forge API'
        spec.paths.keySet().containsAll([
                '/',
                '/versions',
                '/application-types',
                '/application-types/{type}',
                '/application-types/{type}/features',
                '/application-types/{type}/features/default',
                '/create/{type}/{name}',
                '/{name}.zip',
                '/preview/{type}/{name}',
                '/diff/{type}/{name}',
                '/diff/{type}/feature/{feature}',
                '/select-options'
        ])
        !spec.paths.containsKey('/v3/api-docs')
        create.responses.keySet().containsAll(['201', '400'])
        !create.responses.containsKey('200')
        (create.responses['201'] as Map).content.containsKey('application/zip')
        create.parameters*.name.containsAll(['type', 'name', 'features', 'build', 'reloading', 'gorm', 'servlet', 'javaVersion'])
        features.parameters*.name.containsAll(['type', 'reloading', 'gorm', 'servlet', 'javaVersion'])
        (features.responses['200'] as Map).content['application/json'].schema['$ref'] == '#/components/schemas/FeatureList'
        typeList.get('properties').keySet().containsAll(['types', '_links'])
        !typeList.get('properties').containsKey('links')
        !typeList.get('properties').containsKey('metaClass')
    }

    void "Swagger UI is served beside the description"() {
        expect:
        get('/swagger-ui/index.html').status == 200
        get('/swagger-ui/index.html').contentType.startsWith('text/html')
        json(get('/v3/api-docs/swagger-config')).url == '/v3/api-docs'
    }

    void "application types are accepted hyphenated, underscored or upper case"() {
        expect:
        get('/application-types/rest-api').status == 200
        get('/application-types/rest_api').status == 200
        get('/application-types/REST_API').status == 200
        json(get('/application-types/rest-api')).name == 'rest_api'
        get('/create/rest-api/forge-rest').status == 201
    }

    void "a REST API plugin is a type of application: it is listed, has its features, and is created"() {
        when:
        Map type = json(get('/application-types/rest-api-plugin'))
        Map defaults = json(get('/application-types/rest-api-plugin/features/default'))
        Map available = json(get('/application-types/rest-api-plugin/features'))
        Map created = get('/create/rest-api-plugin/com.example.forge-rest-plugin')
        byte[] zip = created.body as byte[]
        List<String> entries = zipEntries(zip)

        then:
        json(get('/application-types')).types*.name.contains('rest_api_plugin')
        get('/application-types/rest_api_plugin').status == 200
        get('/application-types/REST_API_PLUGIN').status == 200
        type.name == 'rest_api_plugin'
        type.title == 'Rest API Plugin'
        text(get('/')).contains('REST_API_PLUGIN')

        and: 'with the features of a REST API, and none of those a plugin does not impose on its applications'
        defaults.features*.name.contains('views-json')
        !defaults.features*.name.contains('gsp')
        !available.features*.name.any { it in ['asset-pipeline-grails', 'openapi', 'grails-spring-security'] }

        and: 'scaffolding, whose tools generate a REST controller and JSON views, can be selected and is not a default'
        available.features*.name.contains('scaffolding')
        !defaults.features*.name.contains('scaffolding')

        and: 'what is created is a plugin for JSON views, with none of its own'
        created.status == 201
        entries.contains('forge-rest-plugin/src/main/groovy/com/example/ForgeRestPluginGrailsPlugin.groovy')
        !entries.any { it.contains('/grails-app/views/') }
        zipText(zip, 'forge-rest-plugin/build.gradle').contains('org.apache.grails.gradle.grails-gson')
        !entries.any { it.endsWith('.gsp') }
        zipText(zip, 'forge-rest-plugin/build.gradle').contains('apply plugin: "org.apache.grails.gradle.grails-plugin"')
        zipText(zip, 'forge-rest-plugin/.grails/project.yml').contains('applicationType: rest-api-plugin\n')
    }

    void 'generation endpoint #path refuses the chosen option #query that does not agree with the selected features'() {
        when:
        Map response = get("${path}?${query}")
        Map error = json(response)

        then:
        response.status == 400
        response.contentType.startsWith('application/json')
        error.message == message
        error._embedded.errors[0].message == error.message

        where:
        [path, query, message] << [
                ['/create/web/demo', '/demo.zip', '/preview/web/demo', '/diff/web/demo'],
                [
                        ['gorm=hibernate7&features=gorm-hibernate5', 'Only one Grails Data for Hibernate implementation can be selected: gorm-hibernate5 or gorm-hibernate7'],
                        ['gorm=neo4j&features=gorm-mongodb', 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'],
                        ['gorm=mongodb&reloading=DEVTOOLS&servlet=TOMCAT&features=gorm-neo4j', 'There can only be one of the following features selected: [gorm-mongodb, gorm-neo4j]'],
                        ['gorm=hibernate7&features=gorm-neo4j', NEO4J_WITH_HIBERNATE7],
                        ['gorm=neo4j&features=gorm-hibernate7', NEO4J_WITH_HIBERNATE7],
                ]
        ].combinations().collect { List each -> [each[0], each[1][0], each[1][1]] }
    }

    void 'generation endpoint #path takes #query, where an option only repeats its default, as it takes the feature alone'() {
        when: 'what the Forge UI sends when a feature is ticked and the options are left as they are'
        Map response = get("${path}?${query}")

        then:
        response.status == status

        where:
        path                  | query                                                                         || status
        '/create/web/demo'    | 'gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS&features=gorm-hibernate7'  || 201
        '/demo.zip'           | 'gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS&features=gorm-hibernate7'  || 201
        '/preview/web/demo'   | 'gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS&features=gorm-hibernate7'  || 200
        '/diff/web/demo'      | 'gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS&features=gorm-hibernate7'  || 200
        '/diff/web/feature/gorm-hibernate7' | 'gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS'             || 200
        '/create/web/demo'    | 'gorm=HIBERNATE&features=gorm-hibernate7'                                     || 201
        '/create/web/demo'    | 'features=gorm-hibernate7'                                                    || 201
        '/create/web/demo'    | 'gorm=hibernate7&features=gorm-hibernate7'                                    || 201
        '/create/web/demo'    | 'reloading=DEVTOOLS&javaVersion=JDK_25&features=grails-micronaut'             || 201
        '/create/web/demo'    | 'reloading=NONE&javaVersion=JDK_25&features=grails-micronaut'                 || 201
        '/create/plugin/demo' | 'servlet=TOMCAT&gorm=hibernate5&reloading=DEVTOOLS'                           || 201
        '/preview/web/demo'   | 'gorm=mongodb&features=gorm-hibernate7'                                       || 200
    }

    void "a request that repeats the default of an option and selects another feature gets that feature, recorded as what the application has"() {
        when:
        Map contents = json(get('/preview/web/com.example.forge-ui?gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS&features=gorm-hibernate7')).contents as Map

        then: 'a Hibernate 7 application'
        (contents['build.gradle'] as String).contains('org.apache.grails:grails-data-hibernate7')
        !(contents['build.gradle'] as String).contains('org.apache.grails:grails-data-hibernate5"')
        (contents['.grails/project.yml'] as String).contains('  data: hibernate7\n')
        (contents['.grails/forge.lock.yml'] as String).contains('- name: gorm-hibernate7\n')
        !(contents['.grails/forge.lock.yml'] as String).contains('- name: gorm-hibernate5\n')
        !contents.containsKey('grails-forge-cli.yml')

        when: 'a plugin, with every option as the UI sends it'
        Map plugin = json(get('/preview/plugin/com.example.forge-ui?gorm=hibernate5&servlet=TOMCAT&reloading=DEVTOOLS')).contents as Map

        then: 'is recorded without a servlet container, which it has not'
        (plugin['.grails/project.yml'] as String).contains('  servlet: none\n')
        (plugin['.grails/forge.lock.yml'] as String).contains('  servlet: none\n')
    }

    void "the state of a project names the options the application has, where one was not given"() {
        when:
        Map contents = json(get('/preview/web/com.example.forge-options?features=gorm-hibernate7')).contents as Map

        then:
        (contents['.grails/project.yml'] as String).contains('  data: hibernate7\n')
        (contents['.grails/forge.lock.yml'] as String).contains('  data: hibernate7\n')

        when:
        Map micronaut = json(get('/preview/web/com.example.forge-options?javaVersion=JDK_25&features=grails-micronaut')).contents as Map

        then:
        (micronaut['.grails/project.yml'] as String).contains('  reloading: none\n')
        (micronaut['.grails/forge.lock.yml'] as String).contains('  reloading: none\n')
    }

    void 'a feature name that is no feature is shown in one bounded line: #name'() {
        when:
        Map response = get("/preview/web/demo?features=${URLEncoder.encode(name, 'UTF-8')}")

        then:
        response.status == 400
        json(response).message == message

        where:
        name                 || message
        'no-such'            || 'The requested feature does not exist: no-such'
        'zzz-none,aaa-none'  || 'The requested features do not exist: aaa-none, zzz-none'
        'x' * 500            || 'The requested feature does not exist: ' + ('x' * 80) + '...'
        'a\u001b[31mb'        || 'The requested feature does not exist: a?[31mb'
    }

    void "a features parameter may list several features, comma separated"() {
        when:
        Map preview = json(get('/preview/web/forge-features?features=grails-quartz,gorm-mongodb&gorm=mongodb'))
        String buildFile = preview.contents['build.gradle']

        then:
        buildFile.contains('org.apache.grails:grails-quartz')
        buildFile.contains('mongodb')
    }

    private Map get(String path, Map headers = [:]) {
        exchange('GET', path, headers)
    }

    private Map exchange(String method, String path, Map headers = [:]) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:${serverPort}${path}"))
                .timeout(Duration.ofSeconds(120))
        headers.each { String name, String value ->
            request.header(name, value)
        }
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofByteArray())
        [
                status: response.statusCode(),
                contentType: response.headers().firstValue('Content-Type').orElse(''),
                contentDisposition: response.headers().firstValue('Content-Disposition').orElse(''),
                accessControlAllowOrigin: response.headers().firstValue('Access-Control-Allow-Origin').orElse(''),
                body: response.body()
        ]
    }

    private static Map json(Map response) {
        new JsonSlurper().parseText(text(response)) as Map
    }

    private static String text(Map response) {
        new String(response.body as byte[], StandardCharsets.UTF_8)
    }

    private static void unzipTo(byte[] archive, Path target) {
        new ZipInputStream(new ByteArrayInputStream(archive)).withCloseable { ZipInputStream zip ->
            for (ZipEntry entry = zip.nextEntry; entry != null; entry = zip.nextEntry) {
                Path file = target.resolve(entry.name)
                if (entry.directory) {
                    Files.createDirectories(file)
                } else {
                    Files.createDirectories(file.parent)
                    Files.write(file, zip.readAllBytes())
                }
            }
        }
    }

    // applies a unified difference for each file, as patch does
    private static List<String> applyDiff(Path project, String diff) {
        List<List<String>> files = []
        diff.readLines().each { String line ->
            if (line.startsWith('--- ')) {
                files << []
            }
            if (files) {
                files.last() << line
            }
        }
        return files.collect { List<String> lines ->
            String path = lines[0].substring(4)
            File file = project.resolve(path).toFile()
            List<String> before = file.file ? file.getText('UTF-8').split('\n') as List<String> : []
            List<String> after = UnifiedDiffUtils.parseUnifiedDiff(lines.reverse().dropWhile { it == '' }.reverse()).applyTo(before)
            file.parentFile.mkdirs()
            file.setText(after.join('\n') + '\n', 'UTF-8')
            path
        }
    }

    private static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(MessageDigest.getInstance('SHA-256').digest(bytes))
    }

    private static boolean isZip(byte[] body) {
        if (body.length < 4 || body[0] != 0x50 || body[1] != 0x4b) {
            return false
        }
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))
        try {
            zip.nextEntry != null
        }
        finally {
            zip.close()
        }
    }

    private static String zipText(byte[] body, String path) {
        new ZipInputStream(new ByteArrayInputStream(body)).withCloseable { ZipInputStream zip ->
            ZipEntry entry
            while ((entry = zip.nextEntry) != null) {
                if (entry.name == path) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8)
                }
            }
            return null
        }
    }

    private static List<String> zipEntries(byte[] body) {
        new ZipInputStream(new ByteArrayInputStream(body)).withCloseable { ZipInputStream zip ->
            List<String> entries = []
            ZipEntry entry
            while ((entry = zip.nextEntry) != null) {
                entries.add(entry.name)
            }
            entries
        }
    }
}
