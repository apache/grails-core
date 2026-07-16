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

package org.grails.forge.feature.view


import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.feature.Features
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.Options
import spock.lang.Unroll

class GrailsGspSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test gsp feature"() {
        when:
        final Features features = getFeatures(["gsp"])

        then:
        features.contains("grails-web")
        features.contains("gsp")
    }

    void "test dependencies are present for Gradle"() {
        when:
        final String template = new BuildBuilder(beanContext)
            .features(["gsp"])
            .render()

        then:
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-web\"")
        template.contains("apply plugin: \"org.apache.grails.gradle.grails-gsp\"")
        template.contains("implementation \"org.apache.grails:grails-gsp\"")
    }

    void "test gsp configuration"() {
        when:
        final GeneratorContext ctx = buildGeneratorContext(["gsp"])

        then:
        ctx.getConfiguration().containsKey("grails.views.gsp.encoding")
        ctx.getConfiguration().containsKey("grails.views.gsp.htmlcodec")
        ctx.getConfiguration().containsKey("grails.views.gsp.codecs.scriptlet")
    }

    void "test mime types are not written to config as they are now framework defaults"() {
        when:
        final GeneratorContext ctx = buildGeneratorContext(["gsp"])

        then: "the generated application relies on the framework provided MimeType defaults"
        ctx.getConfiguration().keySet().every { !it.toString().startsWith("grails.mime.types") }
    }

    void "test default views are present"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        
        then:
        output.containsKey("grails-app/views/index.gsp")
        output.containsKey("grails-app/views/error.gsp")
        output.containsKey("grails-app/views/notFound.gsp")
    }

    void "test default index page is internationalized"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String index = output["grails-app/views/index.gsp"]

        then: "user-facing text renders through message codes, so the language selector actually translates the page"
        index.contains('<title><g:message code="welcome.title"/></title>')
        index.contains('<g:message code="welcome.congratulations"/>')
        index.contains("message(code: 'welcome.reloading.active')")
        index.contains("message(code: 'welcome.filter.name')")

        and: "no user-facing English remains hardcoded (product names like Grails/Spring stay literal)"
        !index.contains("Congratulations, you have successfully started")
        !index.contains(">Available Controllers<")
        !index.contains(">Installed plugins<")
    }

    void "test default index page carries the brand hero and per-card name filters"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String index = output["grails-app/views/index.gsp"]

        then: "the Grails cups hero opens the page"
        index.contains('class="welcome-hero-cups"')
        index.contains('welcome-hero-body')

        and: "controllers and plugins are filterable by name, with empty states"
        index.contains('data-filter-list="#controllers-list"')
        index.contains('data-filter-list="#plugins-table tbody"')
        index.contains('id="controllers-empty"')
        index.contains('id="plugins-empty"')

        and: "count badges are theme-adaptive rather than hardcoded light"
        index.contains('badge bg-body-tertiary text-body border')
        !index.contains('text-bg-light')
    }

    void "test default layout includes the language selector dropdown"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String layout = output["grails-app/views/layouts/main.gsp"]

        then: "the navbar reads the i18n plugin's published available locales"
        layout.contains("application.getAttribute('availableLocales')")

        and: "and renders a Bootstrap dropdown that switches language via ?lang="
        layout.contains("dropdown-toggle")
        layout.contains('?lang=${availableLocale.toLanguageTag()}')
    }

    void "test default layout includes a controllers dropdown on every page"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String layout = output["grails-app/views/layouts/main.gsp"]

        then: "the navbar lists every controller and links to its default action"
        layout.contains('id="controllersDropdown"')
        layout.contains('<g:message code="welcome.artefact.controllers"/>')
        layout.contains('grailsApplication.controllerClasses')
        layout.contains('<g:link controller="${c.logicalPropertyName}" namespace="${c.namespace}"')
    }

    void "test the profile skeleton mirrors the forge welcome templates"() {
        given: "the two app generators ship the same default UI from different trees"
        final File forgeResources = new File("src/main/resources").canonicalFile
        final File profiles = new File("../../grails-profiles").canonicalFile
        final Map<String, String> mirrored = [
                "gsp/index.gsp"                     : "web/skeleton/grails-app/views/index.gsp",
                "gsp/main.gsp"                      : "web/skeleton/grails-app/views/layouts/main.gsp",
                "assets/stylesheets/welcome.css"    : "web/skeleton/grails-app/assets/stylesheets/welcome.css",
                "assets/javascripts/welcome.js"     : "web/skeleton/grails-app/assets/javascripts/welcome.js",
        ]
        new File(forgeResources, "i18n").listFiles({ File f -> f.name.startsWith("messages") } as FileFilter).each {
            mirrored["i18n/" + it.name] = "base/skeleton/grails-app/i18n/" + it.name
        }

        expect: "every mirrored file is byte-identical, so create-app and forge generate the same application"
        mirrored.every { forgePath, profilePath ->
            final File forgeFile = new File(forgeResources, forgePath)
            final File profileFile = new File(profiles, profilePath)
            assert forgeFile.file, "missing forge template ${forgeFile}"
            assert profileFile.file, "missing profile skeleton ${profileFile}"
            assert forgeFile.bytes == profileFile.bytes, "${forgePath} has drifted from ${profilePath}"
            true
        }
    }

    void "test default layout keeps the navbar minimal and pins the default language"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String layout = output["grails-app/views/layouts/main.gsp"]

        then: "only controllers, language and theme live in the navbar"
        layout.contains('id="controllersDropdown"')
        layout.contains('id="localeDropdown"')
        layout.contains('id="themeDropdown"')
        !layout.contains('id="statusDropdown"')
        !layout.contains('id="pluginsDropdown"')
        !layout.contains('id="actuatorsDropdown"')

        and: "the language menu pins the default language on top so users can always navigate back"
        layout.contains("getProperty('spring.web.locale', 'en')")
        layout.contains('java.util.Locale.ENGLISH')
        layout.indexOf('defaultLocale.getDisplayName(defaultLocale)') < layout.indexOf('availableLocale.getDisplayName(availableLocale)')
    }

    void "test default index page lists exposed actuator endpoints when actuator is present"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String index = output["grails-app/views/index.gsp"]

        then: "the actuators card renders only when actuator exposes web endpoints, resolved without a hard class reference"
        index.contains('<g:message code="welcome.actuators"/>')
        index.contains("ClassUtils.isPresent('org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier'")
        index.contains('management.endpoints.web.base-path')
        index.contains('${actuatorUrlBase}${actuatorBasePath}/${endpoint.rootPath}')

        and: "a separate management port redirects the links off the application port"
        index.contains("getProperty('management.server.port')")

        and: "the runtime versions card reports Spring Security only when the dependency is present"
        index.contains("ClassUtils.isPresent('org.springframework.security.core.SpringSecurityCoreVersion'")
        index.contains('Spring Security')
    }

    void "test default layout chrome is internationalized"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String layout = output["grails-app/views/layouts/main.gsp"]

        then: "footer cards, theme selector and spinner render through message codes"
        layout.contains('<g:message code="layout.guides.text"/>')
        layout.contains('<g:message code="layout.docs.text"/>')
        layout.contains('<g:message code="layout.community.text"/>')
        layout.contains('<g:message code="layout.theme.light"/>')
        layout.contains("message(code: 'layout.theme.toggle')")
        layout.contains('<g:message code="layout.loading"/>')

        and: "no user-facing English remains hardcoded in the layout"
        !layout.contains("Building your first Grails app")
        !layout.contains("Ready to dig in?")
        !layout.contains("Get feedback and share your experience")
        !layout.contains(">Toggle theme<")
        !layout.contains(">Loading...<")
    }

    void "test default layout includes the light/dark/auto theme selector"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String layout = output["grails-app/views/layouts/main.gsp"]

        then: "the theme switcher script is shipped and loaded in the head to avoid a flash of the wrong theme"
        output.containsKey("grails-app/assets/javascripts/theme.js")
        layout.contains('<asset:javascript src="theme.js"/>')

        and: "the navbar offers light, dark and auto options"
        layout.contains('data-bs-theme-value="light"')
        layout.contains('data-bs-theme-value="dark"')
        layout.contains('data-bs-theme-value="auto"')

        and: "the plugins table header does not force a fixed light background (stays theme-adaptive)"
        final String index = output["grails-app/views/index.gsp"]
        !index.contains('table-light')
        index.contains('<thead class="small">')
    }

    @Unroll
    void "test grails-gsp gradle plugins and dependencies are present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))
        final String build = output['build.gradle']

        then:
        build.contains('apply plugin: "org.apache.grails.gradle.grails-web"')
        build.contains('apply plugin: "org.apache.grails.gradle.grails-gsp"')
        build.contains("implementation \"org.apache.grails:grails-gsp\"")

        where:
        applicationType << [ApplicationType.WEB]
    }

    @Unroll
    void "test grails-plugin gradle plugins and dependencies are present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))
        final String build = output['build.gradle']

        then:
        build.contains('apply plugin: "org.apache.grails.gradle.grails-plugin"')
        build.contains('apply plugin: "org.apache.grails.gradle.grails-gsp"')
        build.contains("implementation \"org.apache.grails:grails-gsp\"")

        where:
        applicationType << [ApplicationType.WEB_PLUGIN]
    }

    @Unroll
    void "test grails-gsp gradle plugins and dependencies are NOT present for #applicationType application"() {
        when:
        final def output = generate(applicationType, new Options(DevelopmentReloading.DEVTOOLS))
        final String build = output['build.gradle']

        then:
        !build.contains('id "org.apache.grails.gradle.grails-gsp"')
        !build.contains("implementation \"org.apache.grails:grails-gsp\"")

        where:
        applicationType << [ApplicationType.PLUGIN, ApplicationType.REST_API]
    }

}
