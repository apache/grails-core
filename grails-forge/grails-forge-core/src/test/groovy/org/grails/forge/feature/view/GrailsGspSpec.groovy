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
        !index.contains(">Available Domains<")
        !index.contains(">Available Services<")
        !index.contains(">Available Tag Libraries<")
        !index.contains(">Installed plugins<")
        !index.contains(">Application Listeners<")
        !index.contains(">Data Binding<")
        !index.contains(">Mime Types<")
        !index.contains(">Datastores<")
        !index.contains(">Servlet Filters<")
        !index.contains(">Filter Registrations<")
        !index.contains(">Security Filter Chain<")
        !index.contains(">URL Mappings<")
    }

    void "test default index page carries the brand hero and per-card name filters"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String index = output["grails-app/views/index.gsp"]

        then: "the Grails cups hero opens the page"
        index.contains('class="welcome-hero-cups"')
        index.contains('welcome-hero-body')

        and: "every artefact list and the plugins table are filterable by name, with empty states"
        index.contains('data-filter-list="#controllers-list"')
        index.contains('data-filter-list="#domains-list"')
        index.contains('data-filter-list="#services-list"')
        index.contains('data-filter-list="#taglibs-list"')
        index.contains('data-filter-list="#plugins-table tbody"')
        index.contains('id="controllers-empty"')
        index.contains('id="domains-empty"')
        index.contains('id="services-empty"')
        index.contains('id="taglibs-empty"')
        index.contains('id="plugins-empty"')

        and: "so are the runtime internals lists and the mime types table"
        index.contains('data-filter-list="#listeners-list"')
        index.contains('data-filter-list="#binding-list"')
        index.contains('data-filter-list="#mimeproviders-list"')
        index.contains('data-filter-list="#filters-list"')
        index.contains('data-filter-list="#registrations-list"')
        index.contains('data-filter-list="#securitychain-list"')
        index.contains('data-filter-list="#urlmappings-list"')
        index.contains('data-filter-list="#mime-types-table tbody"')
        index.contains('id="listeners-empty"')
        index.contains('id="binding-empty"')
        index.contains('id="mimeproviders-empty"')
        index.contains('id="filters-empty"')
        index.contains('id="registrations-empty"')
        index.contains('id="securitychain-empty"')
        index.contains('id="urlmappings-empty"')
        index.contains('id="mime-types-empty"')

        and: "truncatable status-card values expose their full text as a hover tooltip"
        index.contains('title="${servletContext.serverInfo}"')
        index.contains('title="${InetAddress.localHost}"')

        and: "count badges are theme-adaptive rather than hardcoded light"
        index.contains('badge bg-body-tertiary text-body border')
        !index.contains('text-bg-light')
    }

    void "test the available artefacts card switches between artefact types"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String index = output["grails-app/views/index.gsp"]

        then: "all four artefact panels are server-rendered, with the active panel computed server-side"
        index.contains('''<g:set var="artefactPanel" value="${params.domainCounts != null ? 'domains' : 'controllers'}"/>''')
        index.contains('''<div data-switch-for="controllers" class="${artefactPanel == 'controllers' ? '' : 'd-none'}">''')
        index.contains('''<div data-switch-for="domains" class="${artefactPanel == 'domains' ? '' : 'd-none'}">''')
        index.contains('<div data-switch-for="services" class="d-none">')
        index.contains('<div data-switch-for="taglibs" class="d-none">')

        and: "the panels list every artefact collection, not just controllers"
        index.contains('grailsApplication.domainClasses')
        index.contains('grailsApplication.serviceClasses')
        index.contains('grailsApplication.tagLibClasses')

        and: "the card title is a dropdown whose items switch the type through localized labels"
        index.contains('data-switch-type="controllers"')
        index.contains('data-switch-type="domains"')
        index.contains('data-switch-type="services"')
        index.contains('data-switch-type="taglibs"')
        index.contains("message(code: 'welcome.artefacts.switch')")
        index.contains('<g:message code="welcome.artefact.domains"/>')

        and: "domains group by providing plugin with opt-in per-request row counts"
        index.contains('pluginManager.getPluginForClass(dc.clazz)')
        index.contains('d.clazz.count()')
        index.contains('bi bi-123')
        index.contains("message(code: 'welcome.domains.counts')")

        and: "framework tag libraries link to their published groovydoc, plugin ones stay plain"
        index.contains('''<g:if test="${t.packageName?.startsWith('org.grails.')}">''')
        index.contains('https://grails.apache.org/docs/latest/api/${t.packageName.replace(\'.\', \'/\')}/${t.shortName}.html')

        and: "the artefact-count rows jump to the card already switched to their type"
        index.contains('id="available-artefacts" data-switch-scope')
        index.contains('href="#available-artefacts" data-switch-jump="controllers"')
        index.contains('href="#available-artefacts" data-switch-jump="domains"')
        index.contains('href="#available-artefacts" data-switch-jump="services"')
        index.contains('href="#available-artefacts" data-switch-jump="taglibs"')

        and: "the page script drives switching purely by toggling visibility within each scope"
        final String welcomeJs = output["grails-app/assets/javascripts/welcome.js"]
        welcomeJs.contains("querySelectorAll('[data-switch-scope]')")
        welcomeJs.contains("querySelectorAll('[data-switch-for]')")
        welcomeJs.contains("querySelectorAll('[data-switch-type]')")
        welcomeJs.contains("querySelectorAll('a[data-switch-jump]')")
    }

    void "test the runtime internals row surfaces listeners, data binding, mime handling and datastores"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String index = output["grails-app/views/index.gsp"]

        then: "a second switchable card lists application listeners, data binding beans and mime type providers"
        index.contains('id="runtime-beans" data-switch-scope')
        index.contains('applicationContext.applicationListeners')
        index.contains('data-switch-type="listeners"')
        index.contains('data-switch-type="binding"')
        index.contains('data-switch-type="mimeproviders"')
        index.contains("message(code: 'welcome.runtime.switch')")

        and: "the data binding panel groups the four binding bean types with localized headers"
        index.contains('grails.databinding.converters.ValueConverter')
        index.contains('grails.databinding.converters.FormattedValueConverter')
        index.contains('grails.databinding.TypedStructuredBindingEditor')
        index.contains('grails.databinding.events.DataBindingListener')
        index.contains("welcome.binding.value")
        index.contains("welcome.binding.structured")

        and: "mime types render as a sortable table defaulting to the extension column"
        index.contains('grails.web.mime.MimeTypeProvider')
        index.contains("applicationContext.containsBean('mimeTypes')")
        index.contains('data-sortable="true" data-sort-default="extension"')
        index.contains('data-sort-key="extension"')
        index.contains('data-sort-key="type"')

        and: "the datastores card renders only when GORM is present, resolved without a hard class reference"
        index.contains("ClassUtils.isPresent('org.grails.datastore.mapping.core.Datastore'")
        index.contains('mappingContext.eventListeners')
        index.contains('<g:message code="welcome.datastores.listeners"/>')

        and: "the request's effective filter pipeline is derived from the rendering call stack"
        index.contains('data-switch-type="filters"')
        index.contains('Thread.currentThread().stackTrace')
        index.contains('jakarta.servlet.Filter.isAssignableFrom')
        index.contains('<g:message code="welcome.filters.request"/>')

        and: "filter registrations render sorted by their order value"
        index.contains('data-switch-type="registrations"')
        index.contains('org.springframework.boot.web.servlet.FilterRegistrationBean')

        and: "the diagnostic internals render only in development"
        index.contains('''<g:if test="${Environment.current == Environment.DEVELOPMENT}">''')

        and: "the security filter chain panel renders only when Spring Security is present"
        index.contains('data-switch-type="securitychain"')
        index.contains("ClassUtils.isPresent('org.springframework.security.web.FilterChainProxy'")
        index.contains('<g:message code="welcome.securitychain.chain"/>')

        and: "the URL mappings panel lists the active mappings and replays matchAll for a pasted URL"
        index.contains('data-switch-type="urlmappings"')
        index.contains("applicationContext.containsBean('grailsUrlMappingsHolder')")
        index.contains('name="resolveUrl"')
        index.contains('name="resolveMethod"')
        index.contains('name="resolveAccept"')
        index.contains('''action="${(request.contextPath ?: '') + '/'}#runtime-beans"''')
        index.contains('urlMappingsHolder.matchAll(resolvePath, resolveMethodParam)')
        index.contains('<g:message code="welcome.urlmappings.resolve.hint"/>')
        index.contains('<g:message code="welcome.urlmappings.resolve.result"/>')
        index.contains('<g:message code="welcome.urlmappings.resolve.none"/>')

        and: "URL mappings is the card's default panel and a resolve lists only the matches in match order"
        index.contains('''<g:set var="runtimePanel" value="${urlMappingsHolder ? 'urlmappings' : 'listeners'}"/>''')
        index.contains('displayedMappingRows')
        index.contains('resolveMatches.withIndex()')
        index.contains("row.rank == 1 ? 'border-start border-3 border-primary'")
        index.contains('responseCode == 404')

        and: "the resolve step accepts POST parameters and Call replays exactly the resolved request"
        index.contains('name="resolveParams"')
        index.contains('''resolve-params ${resolveMethodParam == 'POST' ? '' : 'd-none'}''')
        index.contains('<g:message code="welcome.urlmappings.resolve.call"/>')
        index.contains('''(resolveCallQuery ? '?' + resolveCallQuery : '')''')
    }

    void "test default layout includes the language selector dropdown"() {
        when:
        final def output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String layout = output["grails-app/views/layouts/main.gsp"]

        then: "the navbar reads the i18n plugin's published available locales"
        layout.contains("application.getAttribute('availableLocales')")

        and: "and renders a Bootstrap dropdown that switches language via ?lang="
        layout.contains("dropdown-toggle")
        layout.contains('?lang=${loc.tag}')
        layout.contains('${loc.autonym}')
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
        layout.contains('<g:localeSelect available="true" pinDefault="true" var="loc">')
        layout.contains('loc.index == 1')
        layout.contains('dropdown-divider')

        and: "a sign-in affordance renders whenever Spring Security is on the classpath, plugin or plain starter"
        layout.contains("ClassUtils.isPresent('org.springframework.security.core.context.SecurityContextHolder'")
        layout.contains('AnonymousAuthenticationToken')
        layout.contains('''<g:form url="[uri: '/logout']" method="post">''')
        layout.contains('${request.contextPath}/login')
        layout.contains('<g:message code="layout.login"/>')
        layout.contains('<g:message code="layout.logout"/>')

        and: "a page contributing its own navActions supersedes the built-in sign-in block"
        layout.contains('''!pageProperty(name: 'page.navActions')''')
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
