<%@ page import="grails.util.Environment"%>
<%@ page import="org.springframework.boot.SpringBootVersion"%>
<%@ page import="org.springframework.core.SpringVersion"%>
<%@ page import="org.springframework.util.ClassUtils"%>
<g:set var="pluginManager" bean="pluginManager"/>
<g:set var="servletContext" bean="servletContext"/>
<g:set var="pluginsWithOrder"
       value="${pluginManager.allPlugins.toList()
               .withIndex()
               .collect { p, i -> [plugin: p, order: i + 1] }
               .sort { a, b -> a.plugin.name.toLowerCase() <=> b.plugin.name.toLowerCase() }}"
/>
<g:set var="numControllers" value="${grailsApplication.controllerClasses.size()}"/>
<g:set var="numDomains" value="${grailsApplication.domainClasses.size()}"/>
<g:set var="numServices" value="${grailsApplication.serviceClasses.size()}"/>
<g:set var="numTagLibs" value="${grailsApplication.tagLibClasses.size()}"/>
<!doctype html>
<html>
<head>
    <title><g:message code="welcome.title"/></title>
    <meta name="layout" content="main"/>
    <asset:stylesheet src="welcome.css"/>
</head>
<body>
<main id="content" role="main" class="pb-4 pb-md-5">
    <div class="container-lg py-2 py-md-3">

        <%-- WELCOME HERO --%>
        <section class="welcome-hero position-relative overflow-hidden rounded-3 p-4 p-md-5 mb-4">
            <svg class="welcome-hero-cups" viewBox="140 0 720 500" xmlns="http://www.w3.org/2000/svg"
                 aria-hidden="true" focusable="false">
                <path fill="currentColor" d="M527.264,491.011 C544.051,488.613 563.236,483.817 572.829,479.021 C582.421,474.224 589.615,467.03 589.615,462.234 C589.615,462.234 587.217,457.438 584.819,452.641 C580.023,445.447 575.227,435.854 563.236,409.475 C558.44,397.484 547.589,366.072 544.051,351.92 C540.386,330.773 540.051,308.254 544.051,287.171 C547.531,274.839 552.314,262.919 560.838,253.597 C570.402,240.945 581.622,228.467 596.81,222.422 C644.094,203.599 699.929,162.469 728.707,116.904 C738.299,100.117 742.876,92.923 746.372,83.3305 C755.023,59.5988 762.66,34.3876 762.28,8.98871 L762.28,6.59059 L498.487,6.59059 L232.295,6.59059 L232.295,11.3868 C231.901,74.2274 269.048,130.868 313.831,172.061 C337.813,193.644 366.59,210.431 400.164,222.422 C412.154,227.218 416.951,229.616 426.543,239.208 C438.534,253.597 448.126,270.384 452.923,289.569 C455.827,317.286 453.654,346.577 445.728,373.503 L440.932,387.892 C438.534,397.484 431.339,411.873 419.349,435.854 C407.358,459.836 407.358,462.234 407.358,464.632 C412.154,479.021 440.932,488.613 484.098,493.409 C493.691,493.409 508.079,493.409 527.264,491.011 M325.822,409.475 C342.609,407.077 356.998,402.281 361.794,395.086 L361.794,392.688 L359.396,385.494 C342.609,354.318 333.016,327.939 333.016,301.56 C333.016,287.171 335.415,279.977 340.211,267.986 C347.405,255.995 349.803,252.125 361.794,247.329 C366.59,244.876 372.313,243.95 374.711,242.478 C380.979,240.625 388.173,236.81 388.173,236.81 C388.173,236.81 383.868,235.884 379.016,233.486 C364.628,228.69 359.396,224.82 347.405,217.625 C309.035,196.042 285.054,174.459 261.073,143.284 C253.878,131.293 250.156,125.996 246.684,121.163 L244.286,116.904 C241.888,114.506 145.963,114.506 143.565,116.904 C141.939,150.478 158.03,180.057 179.536,205.635 C204.661,235.514 225.101,244.005 244.286,248.801 C261.073,253.597 263.471,255.995 270.665,265.588 C275.462,277.578 277.86,284.773 277.86,299.161 C280.258,320.745 273.063,342.328 258.675,373.503 C253.878,383.096 249.082,392.688 249.082,392.688 C249.082,395.086 253.878,399.883 258.675,402.281 C270.665,409.475 304.239,414.271 325.822,409.475 M716.716,409.475 C735.901,407.077 747.892,402.281 750.29,395.086 C750.29,392.688 750.29,390.29 743.095,375.901 C728.008,346.118 717.597,310.72 726.308,277.578 C731.287,264.162 737.689,250.182 752.688,247.852 C776.669,240.658 795.854,229.616 819.835,205.635 C834.224,191.246 847.61,166.971 851.369,152.876 C854.382,141.577 858.172,128.066 855.807,116.904 C853.409,114.506 755.086,114.506 752.688,116.904 C752.688,116.904 750.29,119.302 747.892,121.7 C745.493,128.895 735.901,143.284 728.707,150.478 C719.114,162.469 690.337,191.246 680.744,198.44 C663.057,216.559 629.114,228.768 611.199,236.81 C613.597,239.208 625.587,246.403 635.18,248.801 C654.365,255.995 654.365,255.995 661.559,267.986 C666.355,279.977 668.754,287.171 668.754,301.56 C670.08,334.844 653.109,365.67 639.976,392.688 C657.022,411.883 692.824,411.394 716.716,409.475 Z"/>
            </svg>
            <div class="welcome-hero-body position-relative">
                <h1 class="display-5 fw-bold mb-2"><g:message code="welcome.title"/></h1>
                <p class="lead mb-2">
                    <g:message code="welcome.congratulations"/>
                </p>
                <p class="welcome-hero-note mb-0">
                    <g:message code="welcome.default.page"/>
                </p>
            </div>
        </section>

        <div class="row g-4 align-items-stretch">

            <%-- RUNTIME VERSIONS --%>
            <div class="col-12 col-md-6 col-xxl-3">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <h6 class="card-title mb-3 fw-semibold"><g:message code="welcome.runtime.versions"/></h6>
                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="d-inline-flex align-items-center text-body-secondary">
                                    <asset:image src="grails.svg" alt="Grails" width="18" height="18" class="me-2"/>
                                    Grails
                                </span>
                                <g:meta name="info.app.grailsVersion"/>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="d-inline-flex align-items-center text-body-secondary">
                                    <asset:image src="spring-boot.svg" alt="Spring Boot" width="18" height="18" class="me-2"/>
                                    Spring Boot
                                </span>
                                ${SpringBootVersion.getVersion()}
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="d-inline-flex align-items-center text-body-secondary">
                                    <asset:image src="spring.svg" alt="Spring" width="18" height="18" class="me-2"/>
                                    Spring
                                </span>
                                ${SpringVersion.getVersion()}
                            </li>
                            <%-- Spring Security: only when the dependency is present --%>
                            <g:set var="springSecurityVersion"
                                   value="${ClassUtils.isPresent('org.springframework.security.core.SpringSecurityCoreVersion', null) ? ClassUtils.forName('org.springframework.security.core.SpringSecurityCoreVersion', null).getMethod('getVersion').invoke(null) : null}"/>
                            <g:if test="${springSecurityVersion}">
                                <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                    <span class="d-inline-flex align-items-center text-body-secondary">
                                        <asset:image src="spring.svg" alt="Spring Security" width="18" height="18" class="me-2"/>
                                        Spring Security
                                    </span>
                                    ${springSecurityVersion}
                                </li>
                            </g:if>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="d-inline-flex align-items-center text-body-secondary">
                                    <asset:image src="groovy.svg" alt="Groovy" width="18" height="18" class="me-2"/>
                                    Groovy
                                </span>
                                ${GroovySystem.getVersion()}
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="d-inline-flex align-items-center text-body-secondary">
                                    <asset:image src="java.svg" alt="Java" width="18" height="18" class="me-2"/>
                                    JVM (${System.getProperty('java.vendor')})
                                </span>
                                ${System.getProperty('java.version')}
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

            <%-- APPLICATION INFO --%>
            <div class="col-12 col-md-6 col-xxl-3">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.application"/></h6>
                            <g:if test="${Environment.reloadingAgentEnabled}">
                                <span class="reload-indicator text-success" role="status" aria-label="${message(code: 'welcome.reloading.active')}">
                                    <span class="reload-dot ping" aria-hidden="true"></span>
                                    <span class="text-body-secondary"><g:message code="welcome.reloading.active"/></span>
                                </span>
                            </g:if>
                            <g:else>
                                <span class="reload-indicator text-danger" role="status" aria-label="${message(code: 'welcome.reloading.inactive')}">
                                    <span class="reload-dot" aria-hidden="true"></span>
                                    <span class="text-body-secondary"><g:message code="welcome.reloading.inactive"/></span>
                                </span>
                            </g:else>
                        </div>
                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.app.name"/></span>
                                <span class="fw-medium text-truncate ms-3" title="${meta(name: 'info.app.name')}"><g:meta name="info.app.name"/></span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.app.version"/></span>
                                <span class="fw-medium" style="font-variant-numeric: tabular-nums;">
                                    <g:meta name="info.app.version"/>
                                </span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.app.profile"/></span>
                                <span class="fw-medium text-truncate ms-3" title="${grailsApplication.config.getProperty('grails.profile')}">
                                    ${grailsApplication.config.getProperty('grails.profile')}
                                </span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.app.environment"/></span>
                                <span class="fw-medium">${Environment.current.name}</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

            <%-- SERVER INFO --%>
            <div class="col-12 col-md-6 col-xxl-3">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.server"/></h6>
                        </div>
                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.server.servlet.container"/></span>
                                <span class="fw-medium text-truncate ms-3" title="${servletContext.serverInfo}">${servletContext.serverInfo}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.server.host"/></span>
                                <span class="fw-medium text-truncate ms-3" title="${InetAddress.localHost}">${InetAddress.localHost}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.server.os"/></span>
                                <span class="fw-medium text-truncate ms-3"
                                      title="${System.getProperty('os.name')} ${System.getProperty('os.version')} (${System.getProperty('os.arch')})">
                                    ${System.getProperty('os.name')} ${System.getProperty('os.version')} (${System.getProperty('os.arch')})
                                </span>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

            <%-- ARTEFACT COUNTS --%>
            <div class="col-12 col-md-6 col-xxl-3">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.artefact.counts"/></h6>
                        </div>

                        <%-- Each row jumps to the available-artefacts card already switched to
                             its type; the plain anchor is the no-JS fallback (jump only). --%>
                        <div class="list-group list-group-flush small">
                            <a href="#available-artefacts" data-switch-jump="controllers"
                               class="list-group-item list-group-item-action d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.controllers"/></span>
                                <span class="fw-medium">${numControllers}</span>
                            </a>
                            <a href="#available-artefacts" data-switch-jump="domains"
                               class="list-group-item list-group-item-action d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.domains"/></span>
                                <span class="fw-medium">${numDomains}</span>
                            </a>
                            <a href="#available-artefacts" data-switch-jump="services"
                               class="list-group-item list-group-item-action d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.services"/></span>
                                <span class="fw-medium">${numServices}</span>
                            </a>
                            <a href="#available-artefacts" data-switch-jump="taglibs"
                               class="list-group-item list-group-item-action d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.taglibs"/></span>
                                <span class="fw-medium">${numTagLibs}</span>
                            </a>
                        </div>
                    </div>
                </div>
            </div>

        </div>
    </div>

    <%-- AVAILABLE ARTEFACTS: one server-rendered panel per artefact type. The card
         title is a dropdown that switches which panel is visible; every type-scoped
         element (title span, count badge, filter input, panel) carries
         data-switch-for, and welcome.js only toggles visibility, so no localized
         text lives in the script. --%>
    <div class="container-lg mt-4">
        <div class="row g-4 align-items-start">
            <div class="col-12 col-lg-7">
                <%-- Row counts are opt-in per request (a plain GET round-trip carrying
                     domainCounts), so no datastore query ever runs unless explicitly
                     asked for; the round-trip reopens the domains panel server-side. --%>
                <g:set var="showDomainCounts" value="${params.domainCounts != null}"/>
                <g:set var="artefactPanel" value="${params.domainCounts != null ? 'domains' : 'controllers'}"/>
                <div id="available-artefacts" data-switch-scope class="card border-1 shadow-sm">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-1">
                            <div class="dropdown">
                                <%-- Bootstrap resolves the menu as the toggle's next sibling, so the
                                     button carries the heading typography itself instead of nesting
                                     inside an h6. --%>
                                <button type="button" class="btn btn-sm p-0 border-0 h6 card-title mb-0 fw-semibold dropdown-toggle"
                                        data-bs-toggle="dropdown" aria-expanded="false"
                                        aria-label="${message(code: 'welcome.artefacts.switch')}">
                                    <span data-switch-for="controllers" class="${artefactPanel == 'controllers' ? '' : 'd-none'}"><g:message code="welcome.controllers.title"/></span><span data-switch-for="domains" class="${artefactPanel == 'domains' ? '' : 'd-none'}"><g:message code="welcome.domains.title"/></span><span data-switch-for="services" class="d-none"><g:message code="welcome.services.title"/></span><span data-switch-for="taglibs" class="d-none"><g:message code="welcome.taglibs.title"/></span>
                                </button>
                                <ul class="dropdown-menu">
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center ${artefactPanel == 'controllers' ? 'active' : ''}"
                                                data-switch-type="controllers" aria-pressed="${artefactPanel == 'controllers'}">
                                            <g:message code="welcome.artefact.controllers"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${numControllers}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center ${artefactPanel == 'domains' ? 'active' : ''}"
                                                data-switch-type="domains" aria-pressed="${artefactPanel == 'domains'}">
                                            <g:message code="welcome.artefact.domains"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${numDomains}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                data-switch-type="services" aria-pressed="false">
                                            <g:message code="welcome.artefact.services"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${numServices}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                data-switch-type="taglibs" aria-pressed="false">
                                            <g:message code="welcome.artefact.taglibs"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${numTagLibs}</span>
                                        </button>
                                    </li>
                                </ul>
                            </div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-body-tertiary text-body border ${artefactPanel == 'controllers' ? '' : 'd-none'}" data-switch-for="controllers">${numControllers}</span>
                                <span class="badge bg-body-tertiary text-body border ${artefactPanel == 'domains' ? '' : 'd-none'}" data-switch-for="domains">${numDomains}</span>
                                <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="services">${numServices}</span>
                                <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="taglibs">${numTagLibs}</span>
                                <g:if test="${numDomains != 0}">
                                    <a href="${(request.contextPath ?: '') + '/'}${showDomainCounts ? '' : '?domainCounts=true'}#available-artefacts"
                                       class="btn btn-sm btn-outline-secondary ${showDomainCounts ? 'active' : ''} ${artefactPanel == 'domains' ? '' : 'd-none'}"
                                       data-switch-for="domains"
                                       title="${message(code: 'welcome.domains.counts')}"
                                       aria-label="${message(code: 'welcome.domains.counts')}">
                                        <i class="bi bi-123" aria-hidden="true"></i>
                                    </a>
                                </g:if>
                                <g:if test="${numControllers + numDomains + numServices + numTagLibs != 0}">
                                    <div class="dropdown">
                                        <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="dropdown"
                                                data-bs-auto-close="outside" aria-expanded="false"
                                                aria-label="${message(code: 'welcome.filter.name')}">
                                            <i class="bi bi-filter" aria-hidden="true"></i>
                                        </button>
                                        <%-- One input per artefact type, toggled with its panel. All four share
                                             the focus-on-open listener; focus() is a no-op on hidden inputs, so
                                             only the visible one takes focus. --%>
                                        <div class="dropdown-menu dropdown-menu-end p-2">
                                            <input type="search" class="form-control form-control-sm filter-input ${artefactPanel == 'controllers' ? '' : 'd-none'}"
                                                   data-switch-for="controllers"
                                                   data-filter-list="#controllers-list" data-filter-empty="#controllers-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input ${artefactPanel == 'domains' ? '' : 'd-none'}"
                                                   data-switch-for="domains"
                                                   data-filter-list="#domains-list" data-filter-empty="#domains-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input d-none"
                                                   data-switch-for="services"
                                                   data-filter-list="#services-list" data-filter-empty="#services-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input d-none"
                                                   data-switch-for="taglibs"
                                                   data-filter-list="#taglibs-list" data-filter-empty="#taglibs-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                        </div>
                                    </div>
                                </g:if>
                            </div>
                        </div>
                        <div data-switch-for="controllers" class="${artefactPanel == 'controllers' ? '' : 'd-none'}">
                        <g:if test="${numControllers != 0}">
                            <p class="small text-body-secondary mb-3">
                                <g:message code="welcome.controllers.click"/>
                            </p>
                        </g:if>
                        <g:set var="controllersByNamespace"
                               value="${grailsApplication.controllerClasses
                                       .groupBy { cc -> ((cc.namespace ?: '').trim()) ?: 'default' }
                                       .sort { a, b -> a.key.toString().toLowerCase() <=> b.key.toString().toLowerCase() }}"/>

                        <div id="controllers-list">
                        <g:each var="nsEntry" in="${controllersByNamespace}" status="nsIndex">
                            <div class="${nsIndex > 0 ? 'mt-4' : ''}" data-filter-group>
                                <div class="px-2 py-2 bg-body-tertiary">
                                    <div class="d-flex align-items-center justify-content-between">
                                        <div class="small text-uppercase text-body-secondary fw-semibold"
                                             style="letter-spacing: .04em;">
                                            <g:if test="${nsEntry.key != 'default'}">
                                                ${nsEntry.key}
                                            </g:if>
                                            <g:else>
                                                <g:message code="welcome.namespace.default"/>
                                            </g:else>
                                        </div>
                                    </div>
                                </div>

                                <ul class="list-group list-group-flush">
                                    <g:each var="c" in="${nsEntry.value.sort { it.fullName }}">
                                        <g:set var="simpleName" value="${(c.fullName ?: '')
                                                .tokenize('.')
                                                .last()
                                                .replaceFirst(/Controller$/, '')}"/>

                                        <g:set var="controllerUrl"
                                               value="${createLink(controller: c.logicalPropertyName, namespace: c.namespace)}"/>

                                        <%-- A controller exposing a `show` action gets an inline
                                             "jump to id" control: type an id, hit Show, land on
                                             /controller/show/{id}. --%>
                                        <g:set var="hasShow" value="${c.actions?.contains('show')}"/>

                                        <%-- A default action restricted to non-GET methods (per
                                             allowedMethods, e.g. a POST-only logout) is invoked
                                             through a form using the method it allows, with the
                                             methods shown as a badge in place of the URL link. --%>
                                        <g:set var="ctrlMethods"
                                               value="${c.getPropertyValue('allowedMethods') instanceof Map ? c.getPropertyValue('allowedMethods')[c.defaultAction ?: 'index'] : null}"/>
                                        <g:set var="ctrlGetOk"
                                               value="${ctrlMethods == null || 'GET' in [ctrlMethods].flatten()*.toString()*.toUpperCase()}"/>
                                        <g:set var="showBase"
                                               value="${hasShow ? createLink(controller: c.logicalPropertyName, namespace: c.namespace, action: 'show') : ''}"/>

                                        <li class="list-group-item list-group-item-action px-2" data-name="${simpleName}">
                                            <div class="controller-row d-flex align-items-center gap-2 flex-wrap">
                                                <g:if test="${ctrlGetOk}">
                                                <g:link controller="${c.logicalPropertyName}"
                                                        namespace="${c.namespace}"
                                                        class="d-flex align-items-center gap-3 text-decoration-none min-w-0 flex-grow-1">
                                                    <div class="min-w-0">
                                                        <div class="fw-semibold text-body text-truncate">
                                                            ${simpleName}
                                                        </div>
                                                    </div>
                                                </g:link>
                                                </g:if>
                                                <g:else>
                                                <form action="${controllerUrl}" method="post" class="m-0 d-flex align-items-center gap-3 min-w-0 flex-grow-1">
                                                    <button type="submit" class="btn p-0 border-0 d-flex align-items-center gap-3 text-start min-w-0 w-100">
                                                        <span class="min-w-0">
                                                            <span class="fw-semibold text-body text-truncate d-block">
                                                                ${simpleName}
                                                            </span>
                                                        </span>
                                                    </button>
                                                </form>
                                                </g:else>

                                                <div class="d-flex align-items-center gap-2 flex-shrink-0 ms-auto">
                                                    <g:if test="${hasShow}">
                                                        <%-- Falls back to GET /controller/show?id=… when JS is
                                                             off; welcome.js upgrades it to the /show/{id} path. --%>
                                                        <form class="show-jump d-lg-none" action="${showBase}" method="get" data-show-base="${showBase}">
                                                            <%-- No inputmode: ids may be Long, String, or ObjectId, and a
                                                                 numeric keypad would lock out non-digit ids on mobile. --%>
                                                            <input type="text" name="id" autocomplete="off"
                                                                   class="form-control form-control-sm show-jump-input"
                                                                   placeholder="${message(code: 'welcome.show.placeholder')}"
                                                                   data-focus-placeholder="${message(code: 'welcome.show.hint')}"
                                                                   aria-label="${message(code: 'welcome.show.aria', args: [simpleName])}">
                                                            <button type="submit" class="btn btn-sm btn-primary show-jump-go">
                                                                <g:message code="welcome.show.label"/>
                                                            </button>
                                                        </form>
                                                    </g:if>

                                                    <g:if test="${ctrlGetOk}">
                                                    <a href="${controllerUrl}"
                                                       class="small link-primary link-offset-2 link-underline-opacity-0 link-underline-opacity-75-hover">
                                                        ${controllerUrl}
                                                    </a>
                                                    </g:if>
                                                    <g:else>
                                                    <span class="badge bg-body-tertiary text-body-secondary border">
                                                        ${[ctrlMethods].flatten()*.toString()*.toUpperCase().join(' / ')}
                                                    </span>
                                                    <span class="small text-body-secondary">${controllerUrl}</span>
                                                    </g:else>
                                                </div>
                                            </div>
                                        </li>
                                    </g:each>
                                </ul>
                            </div>
                        </g:each>
                        </div>
                        <g:if test="${numControllers != 0}">
                            <p id="controllers-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.artefacts.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="domains" class="${artefactPanel == 'domains' ? '' : 'd-none'}">
                        <%-- Grouped by providing plugin (application-owned classes first),
                             mirroring the controllers' namespace grouping. --%>
                        <g:set var="domainsByPlugin"
                               value="${grailsApplication.domainClasses.toList()
                                       .groupBy { dc ->
                                           def plugin = null
                                           try { plugin = pluginManager.getPluginForClass(dc.clazz) } catch (Throwable ignored) { }
                                           plugin?.name ?: ''
                                       }
                                       .sort { a, b -> a.key.toLowerCase() <=> b.key.toLowerCase() }}"/>
                        <div id="domains-list">
                            <g:each var="pEntry" in="${domainsByPlugin}" status="pIndex">
                                <div class="${pIndex > 0 ? 'mt-4' : ''}" data-filter-group>
                                    <div class="px-2 py-2 bg-body-tertiary">
                                        <div class="small text-uppercase text-body-secondary fw-semibold"
                                             style="letter-spacing: .04em;">
                                            <g:if test="${pEntry.key}">
                                                ${pEntry.key
                                                        .replaceAll(/([A-Z]+)([A-Z][a-z])/, '$1 $2')
                                                        .replaceAll(/([a-z0-9])([A-Z])/, '$1 $2')
                                                        .replaceAll(/[_-]+/, ' ')
                                                        .trim()
                                                        .capitalize()}
                                            </g:if>
                                            <g:else>
                                                <g:message code="welcome.application"/>
                                            </g:else>
                                        </div>
                                    </div>
                                    <ul class="list-group list-group-flush">
                                        <g:each var="d" in="${pEntry.value.sort { it.shortName }}">
                                            <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2" data-name="${d.shortName}">
                                                <span class="d-flex align-items-center gap-2 min-w-0">
                                                    <span class="fw-semibold text-body text-truncate">${d.shortName}</span>
                                                    <g:if test="${showDomainCounts}">
                                                        <span class="badge bg-body-tertiary text-body border" style="font-variant-numeric: tabular-nums;">${ { ->
                                                            try { g.formatNumber(number: d.clazz.count(), type: 'number') }
                                                            catch (Throwable ignored) { '?' }
                                                        }() }</span>
                                                    </g:if>
                                                </span>
                                                <span class="small text-body-secondary text-truncate">${d.packageName}</span>
                                            </li>
                                        </g:each>
                                    </ul>
                                </div>
                            </g:each>
                        </div>
                        <g:if test="${numDomains != 0}">
                            <p id="domains-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.artefacts.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="services" class="d-none">
                        <div id="services-list">
                            <ul class="list-group list-group-flush">
                                <g:each var="s" in="${grailsApplication.serviceClasses.toList().sort { it.shortName }}">
                                    <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2" data-name="${s.shortName}">
                                        <span class="fw-semibold text-body text-truncate">${s.shortName}</span>
                                        <span class="small text-body-secondary text-truncate">${s.packageName}</span>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                        <g:if test="${numServices != 0}">
                            <p id="services-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.artefacts.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="taglibs" class="d-none">
                        <div id="taglibs-list">
                            <ul class="list-group list-group-flush">
                                <g:each var="t" in="${grailsApplication.tagLibClasses.toList().sort { it.shortName }}">
                                    <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2" data-name="${t.shortName}">
                                        <%-- Framework tag libraries (org.grails.*) link to their published
                                             groovydoc; plugin-provided ones are not in those docs. --%>
                                        <g:if test="${t.packageName?.startsWith('org.grails.')}">
                                            <a href="https://grails.apache.org/docs/latest/api/${t.packageName.replace('.', '/')}/${t.shortName}.html"
                                               target="_blank" rel="noopener"
                                               class="fw-semibold text-truncate link-primary link-offset-2 link-underline-opacity-0 link-underline-opacity-75-hover">${t.shortName}</a>
                                        </g:if>
                                        <g:else>
                                            <span class="fw-semibold text-body text-truncate">${t.shortName}</span>
                                        </g:else>
                                        <span class="small text-truncate"><code>${t.namespace}</code> <span class="text-body-secondary">${t.packageName}</span></span>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                        <g:if test="${numTagLibs != 0}">
                            <p id="taglibs-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.artefacts.none"/></p>
                        </g:else>
                        </div>
                    </div>
                </div>

                <%-- RUNTIME INTERNALS: listeners, data binding, mime handling and
                     the request filter pipeline resolved from the running application
                     context; reuses the artefacts card's data-switch-scope pattern.
                     Development-only: this card enumerates filter pipelines, security
                     chains and bean internals that a production home page should not expose. --%>
                <g:if test="${Environment.current == Environment.DEVELOPMENT}">
                <g:set var="appListeners"
                       value="${applicationContext.applicationListeners.toList()
                               .collect { l -> [name: (l.getClass().simpleName ?: l.getClass().name.tokenize('.').last()),
                                                packageName: (l.getClass().package?.name ?: ''),
                                                detail: l.toString()] }
                               .sort { a, b -> (a.name.toLowerCase() <=> b.name.toLowerCase()) ?: (a.detail <=> b.detail) }}"/>
                <g:set var="bindingGroups"
                       value="${[[code: 'welcome.binding.value', beans: applicationContext.getBeansOfType(grails.databinding.converters.ValueConverter)],
                                 [code: 'welcome.binding.formatted', beans: applicationContext.getBeansOfType(grails.databinding.converters.FormattedValueConverter)],
                                 [code: 'welcome.binding.structured', beans: applicationContext.getBeansOfType(grails.databinding.TypedStructuredBindingEditor)],
                                 [code: 'welcome.binding.listeners', beans: applicationContext.getBeansOfType(grails.databinding.events.DataBindingListener)]]}"/>
                <g:set var="numBindingBeans" value="${bindingGroups.sum { g -> g.beans.size() } ?: 0}"/>
                <g:set var="mimeTypeProviders"
                       value="${applicationContext.getBeansOfType(grails.web.mime.MimeTypeProvider)
                               .entrySet().toList().sort { it.key.toLowerCase() }}"/>
                <%-- The filters still on the call stack ARE this request's pipeline, in
                     execution order: walk the reversed stack, keep Filter classes, collapse
                     the extra frames a filter contributes through its abstract bases, and
                     number what remains. No registry can report this actual order. --%>
                <g:set var="requestFilters"
                       value="${Thread.currentThread().stackTrace.toList().reverse()
                               .findResults { ste ->
                                   def cls = null
                                   try { cls = Class.forName(ste.className, false, Thread.currentThread().contextClassLoader) } catch (Throwable ignored) { }
                                   (cls != null && jakarta.servlet.Filter.isAssignableFrom(cls)) ? cls : null
                               }
                               .inject([]) { acc, cls ->
                                   def prev = acc ? acc[-1] : null
                                   if (prev == cls) { return acc }
                                   if (prev != null && prev.isAssignableFrom(cls)) { acc[-1] = cls; return acc }
                                   if (prev != null && cls.isAssignableFrom(prev)) { return acc }
                                   acc << cls
                               }
                               .unique()}"/>
                <g:set var="filterRegistrations"
                       value="${applicationContext.getBeansOfType(org.springframework.boot.web.servlet.FilterRegistrationBean)
                               .entrySet().toList().sort { it.value.order }}"/>
                <g:set var="filterChainProxyType"
                       value="${ClassUtils.isPresent('org.springframework.security.web.FilterChainProxy', null) ? ClassUtils.forName('org.springframework.security.web.FilterChainProxy', null) : null}"/>
                <g:set var="securityFilterChains"
                       value="${filterChainProxyType ? applicationContext.getBeansOfType(filterChainProxyType).values().toList()
                               .collectMany { proxy -> proxy.filterChains }
                               .withIndex()
                               .collect { chain, i ->
                                   def matcher = ''
                                   try { matcher = chain.requestMatcher?.toString() ?: '' } catch (Throwable ignored) { }
                                   [index: i + 1, matcher: matcher, filters: chain.filters]
                               } : []}"/>
                <g:set var="numSecurityFilters" value="${securityFilterChains.sum { c -> c.filters.size() } ?: 0}"/>

                <%-- URL MAPPINGS: the active mappings in evaluation order, plus a resolver
                     that replays UrlMappingsHolder.matchAll for a pasted URL and method.
                     Patterns are rebuilt the way the url-mappings-report command prints
                     them: captured wildcards become their constraint's parameter name. --%>
                <g:set var="urlMappingsHolder"
                       value="${applicationContext.containsBean('grailsUrlMappingsHolder') ? applicationContext.getBean('grailsUrlMappingsHolder') : null}"/>
                <g:set var="urlMappingDynamicLabel" value="${message(code: 'welcome.urlmappings.dynamic')}"/>
                <g:set var="urlMappingDefaultActionLabel" value="${message(code: 'welcome.urlmappings.default.action')}"/>
                <g:set var="urlMappingPattern"
                       value="${ { mapping ->
                           try {
                               def data = mapping.urlData
                               if (data.hasProperty('responseCode')) {
                                   return 'ERROR: ' + data.responseCode
                               }
                               def constraints = (mapping.constraints ?: []) as List
                               int ci = 0
                               StringBuilder pattern = new StringBuilder('/')
                               def tokens = data.tokens
                               tokens.eachWithIndex { token, i ->
                                   String finalToken = token
                                   while (finalToken.contains('(*)') || finalToken.contains('(**)')) {
                                       String name = ci < constraints.size() ? constraints[ci++].propertyName?.toString() : 'param'
                                       if (finalToken.contains('(*)')) {
                                           finalToken = finalToken.replaceFirst(java.util.regex.Pattern.quote('(*)'),
                                                   java.util.regex.Matcher.quoteReplacement('${' + name + '}'))
                                       }
                                       else {
                                           finalToken = finalToken.replaceFirst(java.util.regex.Pattern.quote('(**)'),
                                                   java.util.regex.Matcher.quoteReplacement('${' + name + '}**'))
                                       }
                                   }
                                   pattern << finalToken
                                   if (i < tokens.length - 1) { pattern << '/' }
                               }
                               if (data.hasOptionalExtension()) {
                                   pattern << ('(.' + '${' + (constraints ? constraints[-1].propertyName : 'format') + '}' + ')?')
                               }
                               pattern.toString()
                           } catch (Throwable ignored) { String.valueOf(mapping) }
                       } }"/>
                <%-- controllerName/actionName are lazy evaluator objects (not Strings) on
                     implicit-variable mappings like /$controller/$action - only concrete
                     names are printable; everything else is the localized (dynamic). --%>
                <g:set var="urlMappingTarget"
                       value="${ { m ->
                           try {
                               if (m.viewName) { return 'view: ' + m.viewName }
                               if (m.redirectInfo != null) { return 'redirect: ' + m.redirectInfo }
                               if (!(m.controllerName instanceof CharSequence)) { return urlMappingDynamicLabel }
                               def action = m.actionName instanceof CharSequence ? m.actionName.toString() :
                                       (m.actionName == null ? urlMappingDefaultActionLabel : urlMappingDynamicLabel)
                               def target = m.controllerName.toString() + ' · ' + action
                               m.pluginName ? target + ' (' + m.pluginName + ')' : target
                           } catch (Throwable ignored) { String.valueOf(m) }
                       } }"/>
                <g:set var="urlMappingRows"
                       value="${urlMappingsHolder ? urlMappingsHolder.urlMappings.toList().collect { m ->
                               [mapping: m, pattern: urlMappingPattern(m), method: (m.httpMethod ?: '*'), target: urlMappingTarget(m)]
                           } : []}"/>
                <g:set var="urlMappingExcludes" value="${urlMappingsHolder?.excludePatterns ?: []}"/>
                <g:set var="resolveMethods" value="${['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']}"/>
                <g:set var="resolveUrlParam" value="${params.resolveUrl?.toString()?.trim() ?: ''}"/>
                <g:set var="resolveMethodParam"
                       value="${resolveMethods.contains(params.resolveMethod?.toString()?.toUpperCase()) ? params.resolveMethod.toString().toUpperCase() : 'GET'}"/>
                <g:set var="resolveAcceptParam" value="${params.resolveAccept?.toString() ?: ''}"/>
                <%-- Request parameters to send when the resolved request is called. They
                     never influence which mapping matches - matching is URI + method
                     (+ Accept-Version) only - but Call replays them faithfully. The field
                     applies to POST only; GET carries parameters in the URL itself, so any
                     query string pasted with the URL is preserved for the call. --%>
                <g:set var="resolveParamsParam" value="${params.resolveParams?.toString()?.trim() ?: ''}"/>
                <g:set var="resolveQueryParam"
                       value="${ { ->
                           String raw = resolveUrlParam
                           int h = raw.indexOf('#')
                           if (h != -1) { raw = raw.substring(0, h) }
                           int q = raw.indexOf('?')
                           q != -1 ? raw.substring(q + 1) : ''
                       }() }"/>
                <g:set var="resolveAcceptChoices"
                       value="${(applicationContext.containsBean('mimeTypes') ? applicationContext.getBean('mimeTypes').toList()*.name.unique() : []) ?: ['text/html', 'application/json', 'application/xml']}"/>
                <%-- Accept absolute URLs and app-relative paths alike: strip scheme and
                     host, query string, fragment and this app's context path, because
                     matchAll sees only the context-relative path at dispatch time. --%>
                <g:set var="resolvePath"
                       value="${ { ->
                           String path = resolveUrlParam
                           if (!path) { return '' }
                           try {
                               if (path ==~ '(?i)[a-z][a-z0-9+.-]*://.*') { path = new java.net.URI(path).rawPath ?: '/' }
                           } catch (Throwable ignored) { }
                           int q = path.indexOf('?')
                           if (q != -1) { path = path.substring(0, q) }
                           int h = path.indexOf('#')
                           if (h != -1) { path = path.substring(0, h) }
                           if (request.contextPath && path.startsWith(request.contextPath + '/')) { path = path.substring(request.contextPath.length()) }
                           path.startsWith('/') ? path : '/' + path
                       }() }"/>
                <g:set var="resolveMatches"
                       value="${ { ->
                           if (!urlMappingsHolder || !resolvePath) { return [] }
                           try { (urlMappingsHolder.matchAll(resolvePath, resolveMethodParam) ?: []).toList() }
                           catch (Throwable ignored) { [] }
                       }() }"/>
                <g:set var="resolveInfoParams"
                       value="${ { info ->
                           try { (info.parameters ?: [:]).entrySet().toList().sort { it.key.toString() } }
                           catch (Throwable ignored) { [] }
                       } }"/>
                <g:set var="resolveInfoPattern"
                       value="${ { info ->
                           try { urlMappingRows.find { r -> r.mapping.urlData.is(info.urlData) }?.pattern }
                           catch (Throwable ignored) { null }
                       } }"/>
                <%-- Read controller/action from the extracted parameters first: the
                     info's name getters evaluate lazily against the dispatching request
                     and are unreliable before the info is configured for dispatch. --%>
                <g:set var="resolveInfoTarget"
                       value="${ { info ->
                           try {
                               if (info.viewName) { return 'view: ' + info.viewName }
                               if (info.redirectInfo != null) { return 'redirect: ' + info.redirectInfo }
                               def p = [:]
                               try { p = info.parameters ?: [:] } catch (Throwable ignored) { }
                               def cn = p.controller ?: info.controllerName
                               def an = p.action ?: info.actionName
                               if (cn == null) { return urlMappingDynamicLabel }
                               def target = cn.toString() + ' · ' + (an != null ? an.toString() : urlMappingDefaultActionLabel)
                               info.pluginName ? target + ' (' + info.pluginName + ')' : target
                           } catch (Throwable ignored) { String.valueOf(info) }
                       } }"/>
                <%-- The response format the winning request would negotiate: a format
                     bound from the URL extension beats the Accept selection, exactly as
                     dispatch treats a format request parameter. --%>
                <g:set var="resolveFormat"
                       value="${ { ->
                           def winner = resolveMatches ? resolveMatches[0] : null
                           if (!winner) { return '' }
                           def fromUrl = null
                           try { fromUrl = winner.parameters?.format } catch (Throwable ignored) { }
                           if (fromUrl) { return fromUrl.toString() }
                           if (resolveAcceptParam && applicationContext.containsBean('mimeTypes')) {
                               return applicationContext.getBean('mimeTypes').find { it.name == resolveAcceptParam }?.extension ?: ''
                           }
                           ''
                       }() }"/>
                <%-- Without a resolve the list shows every mapping. With one it shows ONLY
                     the matching mappings, in match order: rank 1 is the mapping that
                     dispatches, rank 2 is what would win if rank 1 did not exist, and so
                     on - matchAll returns matches in the holder's evaluation order. When
                     nothing matches, the 404 status mappings are shown instead, because
                     that is what actually handles the request. --%>
                <g:set var="resolveActive" value="${urlMappingsHolder && resolvePath ? true : false}"/>
                <g:set var="displayedMappingRows"
                       value="${ { ->
                           if (!resolveActive) { return urlMappingRows }
                           if (resolveMatches) {
                               return resolveMatches.withIndex().collect { info, i ->
                                   def row = urlMappingRows.find { r -> r.mapping.urlData.is(info.urlData) }
                                   [pattern: row != null ? row.pattern : resolvePath,
                                    method: row != null ? row.method : resolveMethodParam,
                                    target: resolveInfoTarget(info),
                                    rank: i + 1]
                               }
                           }
                           urlMappingRows.findAll { r ->
                               try { r.mapping.urlData.hasProperty('responseCode') && r.mapping.urlData.responseCode == 404 }
                               catch (Throwable ignored) { false }
                           }.withIndex().collect { r, i -> r + [rank: i + 1] }
                       }() }"/>
                <%-- URL Mappings is the card's default panel whenever the holder exists;
                     it is computed server-side so resolver round-trips (plain GETs back
                     to this page) land on the open panel without any JS. --%>
                <g:set var="runtimePanel" value="${urlMappingsHolder ? 'urlmappings' : 'listeners'}"/>

                <div id="runtime-beans" data-switch-scope class="card border-1 shadow-sm mt-4">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-1">
                            <div class="dropdown">
                                <%-- Bootstrap resolves the menu as the toggle's next sibling, so the
                                     button carries the heading typography itself instead of nesting
                                     inside an h6. --%>
                                <button type="button" class="btn btn-sm p-0 border-0 h6 card-title mb-0 fw-semibold dropdown-toggle"
                                        data-bs-toggle="dropdown" aria-expanded="false"
                                        aria-label="${message(code: 'welcome.runtime.switch')}">
                                    <span data-switch-for="listeners" class="${runtimePanel == 'listeners' ? '' : 'd-none'}"><g:message code="welcome.listeners.title"/></span><span data-switch-for="binding" class="d-none"><g:message code="welcome.binding.title"/></span><span data-switch-for="mimeproviders" class="d-none"><g:message code="welcome.mime.providers.title"/></span><span data-switch-for="filters" class="d-none"><g:message code="welcome.filters.title"/></span><span data-switch-for="registrations" class="d-none"><g:message code="welcome.registrations.title"/></span><g:if test="${filterChainProxyType}"><span data-switch-for="securitychain" class="d-none"><g:message code="welcome.securitychain.title"/></span></g:if><g:if test="${urlMappingsHolder}"><span data-switch-for="urlmappings" class="${runtimePanel == 'urlmappings' ? '' : 'd-none'}"><g:message code="welcome.urlmappings.title"/></span></g:if>
                                </button>
                                <ul class="dropdown-menu">
                                    <g:if test="${urlMappingsHolder}">
                                        <li>
                                            <button type="button" class="dropdown-item d-flex justify-content-between align-items-center ${runtimePanel == 'urlmappings' ? 'active' : ''}"
                                                    data-switch-type="urlmappings" aria-pressed="${runtimePanel == 'urlmappings'}">
                                                <g:message code="welcome.urlmappings.title"/>
                                                <span class="badge bg-body-tertiary text-body border ms-3">${urlMappingRows.size()}</span>
                                            </button>
                                        </li>
                                    </g:if>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center ${runtimePanel == 'listeners' ? 'active' : ''}"
                                                data-switch-type="listeners" aria-pressed="${runtimePanel == 'listeners'}">
                                            <g:message code="welcome.listeners.title"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${appListeners.size()}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                data-switch-type="binding" aria-pressed="false">
                                            <g:message code="welcome.binding.title"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${numBindingBeans}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                data-switch-type="mimeproviders" aria-pressed="false">
                                            <g:message code="welcome.mime.providers.title"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${mimeTypeProviders.size()}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                data-switch-type="filters" aria-pressed="false">
                                            <g:message code="welcome.filters.title"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${requestFilters.size()}</span>
                                        </button>
                                    </li>
                                    <li>
                                        <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                data-switch-type="registrations" aria-pressed="false">
                                            <g:message code="welcome.registrations.title"/>
                                            <span class="badge bg-body-tertiary text-body border ms-3">${filterRegistrations.size()}</span>
                                        </button>
                                    </li>
                                    <g:if test="${filterChainProxyType}">
                                        <li>
                                            <button type="button" class="dropdown-item d-flex justify-content-between align-items-center"
                                                    data-switch-type="securitychain" aria-pressed="false">
                                                <g:message code="welcome.securitychain.title"/>
                                                <span class="badge bg-body-tertiary text-body border ms-3">${numSecurityFilters}</span>
                                            </button>
                                        </li>
                                    </g:if>
                                </ul>
                            </div>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-body-tertiary text-body border ${runtimePanel == 'listeners' ? '' : 'd-none'}" data-switch-for="listeners">${appListeners.size()}</span>
                                <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="binding">${numBindingBeans}</span>
                                <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="mimeproviders">${mimeTypeProviders.size()}</span>
                                <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="filters">${requestFilters.size()}</span>
                                <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="registrations">${filterRegistrations.size()}</span>
                                <g:if test="${filterChainProxyType}">
                                    <span class="badge bg-body-tertiary text-body border d-none" data-switch-for="securitychain">${numSecurityFilters}</span>
                                </g:if>
                                <g:if test="${urlMappingsHolder}">
                                    <span class="badge bg-body-tertiary text-body border ${runtimePanel == 'urlmappings' ? '' : 'd-none'}" data-switch-for="urlmappings">${urlMappingRows.size()}</span>
                                </g:if>
                                <g:if test="${appListeners.size() + numBindingBeans + mimeTypeProviders.size() + requestFilters.size() + filterRegistrations.size() + numSecurityFilters + urlMappingRows.size() != 0}">
                                    <div class="dropdown">
                                        <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="dropdown"
                                                data-bs-auto-close="outside" aria-expanded="false"
                                                aria-label="${message(code: 'welcome.filter.name')}">
                                            <i class="bi bi-filter" aria-hidden="true"></i>
                                        </button>
                                        <%-- One input per bean type, toggled with its panel. All three share
                                             the focus-on-open listener; focus() is a no-op on hidden inputs, so
                                             only the visible one takes focus. --%>
                                        <div class="dropdown-menu dropdown-menu-end p-2">
                                            <input type="search" class="form-control form-control-sm filter-input ${runtimePanel == 'listeners' ? '' : 'd-none'}"
                                                   data-switch-for="listeners"
                                                   data-filter-list="#listeners-list" data-filter-empty="#listeners-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input d-none"
                                                   data-switch-for="binding"
                                                   data-filter-list="#binding-list" data-filter-empty="#binding-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input d-none"
                                                   data-switch-for="mimeproviders"
                                                   data-filter-list="#mimeproviders-list" data-filter-empty="#mimeproviders-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input d-none"
                                                   data-switch-for="filters"
                                                   data-filter-list="#filters-list" data-filter-empty="#filters-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <input type="search" class="form-control form-control-sm filter-input d-none"
                                                   data-switch-for="registrations"
                                                   data-filter-list="#registrations-list" data-filter-empty="#registrations-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                            <g:if test="${filterChainProxyType}">
                                                <input type="search" class="form-control form-control-sm filter-input d-none"
                                                       data-switch-for="securitychain"
                                                       data-filter-list="#securitychain-list" data-filter-empty="#securitychain-empty"
                                                       placeholder="${message(code: 'welcome.filter.name')}"
                                                       aria-label="${message(code: 'welcome.filter.name')}">
                                            </g:if>
                                            <g:if test="${urlMappingsHolder}">
                                                <input type="search" class="form-control form-control-sm filter-input ${runtimePanel == 'urlmappings' ? '' : 'd-none'}"
                                                       data-switch-for="urlmappings"
                                                       data-filter-list="#urlmappings-list" data-filter-empty="#urlmappings-empty"
                                                       placeholder="${message(code: 'welcome.filter.name')}"
                                                       aria-label="${message(code: 'welcome.filter.name')}">
                                            </g:if>
                                        </div>
                                    </div>
                                </g:if>
                            </div>
                        </div>

                        <div data-switch-for="listeners" class="${runtimePanel == 'listeners' ? '' : 'd-none'}">
                        <div id="listeners-list">
                            <ul class="list-group list-group-flush">
                                <g:each var="l" in="${appListeners}">
                                    <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                        data-name="${l.name} ${l.packageName}" title="${l.detail}">
                                        <span class="fw-semibold text-body text-truncate">${l.name}</span>
                                        <span class="small text-body-secondary text-truncate">${l.packageName}</span>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                        <g:if test="${appListeners}">
                            <p id="listeners-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="binding" class="d-none">
                        <div id="binding-list">
                            <g:each var="group" in="${bindingGroups}" status="gIndex">
                                <div class="${gIndex > 0 ? 'mt-4' : ''}" data-filter-group>
                                    <div class="px-2 py-2 bg-body-tertiary">
                                        <div class="small text-uppercase text-body-secondary fw-semibold"
                                             style="letter-spacing: .04em;">
                                            <g:message code="${group.code}"/> (${group.beans.size()})
                                        </div>
                                    </div>
                                    <ul class="list-group list-group-flush">
                                        <g:each var="entry" in="${group.beans.entrySet().toList().sort { it.key.toLowerCase() }}">
                                            <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                                data-name="${entry.key} ${entry.value.getClass().simpleName}" title="${entry.value.getClass().name}">
                                                <span class="fw-semibold text-body text-truncate">${entry.key}</span>
                                                <span class="small text-body-secondary text-truncate">${entry.value.getClass().name}</span>
                                            </li>
                                        </g:each>
                                    </ul>
                                </div>
                            </g:each>
                        </div>
                        <g:if test="${numBindingBeans != 0}">
                            <p id="binding-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="mimeproviders" class="d-none">
                        <div id="mimeproviders-list">
                            <ul class="list-group list-group-flush">
                                <g:each var="p" in="${mimeTypeProviders}">
                                    <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                        data-name="${p.key} ${p.value.mimeTypes*.name.join(' ')}">
                                        <span class="fw-semibold text-body text-truncate">${p.key}</span>
                                        <span class="small text-body-secondary text-truncate">${p.value.mimeTypes*.name.join(', ')}</span>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                        <g:if test="${mimeTypeProviders}">
                            <p id="mimeproviders-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="filters" class="d-none">
                        <g:if test="${requestFilters}">
                            <p class="small text-body-secondary mb-3">
                                <g:message code="welcome.filters.request"/>
                            </p>
                        </g:if>
                        <div id="filters-list">
                            <ul class="list-group list-group-flush">
                                <g:each var="f" in="${requestFilters}" status="fi">
                                    <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                        data-name="${f.simpleName ?: f.name.tokenize('.').last()} ${f.package?.name ?: ''}" title="${f.name}">
                                        <span class="d-flex align-items-center gap-2 min-w-0">
                                            <span class="small text-body-secondary" style="font-variant-numeric: tabular-nums;">${(fi + 1).toString().padLeft(2, '0')}</span>
                                            <span class="fw-semibold text-body text-truncate">${f.simpleName ?: f.name.tokenize('.').last()}</span>
                                        </span>
                                        <span class="small text-body-secondary text-truncate">${f.package?.name ?: ''}</span>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                        <g:if test="${requestFilters}">
                            <p id="filters-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                        </g:else>
                        </div>

                        <div data-switch-for="registrations" class="d-none">
                        <div id="registrations-list">
                            <ul class="list-group list-group-flush">
                                <g:each var="fr" in="${filterRegistrations}">
                                    <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                        data-name="${fr.key} ${fr.value.filter?.getClass()?.name ?: ''}" title="${fr.value.filter?.getClass()?.name ?: ''}">
                                        <span class="d-flex align-items-center gap-2 min-w-0">
                                            <span class="small text-body-secondary" style="font-variant-numeric: tabular-nums;">${fr.value.order}</span>
                                            <span class="fw-semibold text-body text-truncate">${fr.key}</span>
                                        </span>
                                        <span class="small text-body-secondary text-truncate">${fr.value.filter?.getClass()?.name ?: ''}</span>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                        <g:if test="${filterRegistrations}">
                            <p id="registrations-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                        </g:if>
                        <g:else>
                            <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                        </g:else>
                        </div>

                        <g:if test="${filterChainProxyType}">
                            <div data-switch-for="securitychain" class="d-none">
                            <div id="securitychain-list">
                                <g:each var="chain" in="${securityFilterChains}" status="cIndex">
                                    <div class="${cIndex > 0 ? 'mt-4' : ''}" data-filter-group>
                                        <div class="px-2 py-2 bg-body-tertiary">
                                            <div class="d-flex align-items-center justify-content-between">
                                                <div class="small text-uppercase text-body-secondary fw-semibold"
                                                     style="letter-spacing: .04em;">
                                                    <g:message code="welcome.securitychain.chain"/> ${chain.index}
                                                </div>
                                                <g:if test="${chain.matcher}">
                                                    <div class="small text-body-secondary text-truncate">${chain.matcher}</div>
                                                </g:if>
                                            </div>
                                        </div>
                                        <ul class="list-group list-group-flush">
                                            <g:each var="cf" in="${chain.filters}" status="cfi">
                                                <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                                    data-name="${cf.getClass().simpleName} ${cf.getClass().package?.name ?: ''}" title="${cf.getClass().name}">
                                                    <span class="d-flex align-items-center gap-2 min-w-0">
                                                        <span class="small text-body-secondary" style="font-variant-numeric: tabular-nums;">${(cfi + 1).toString().padLeft(2, '0')}</span>
                                                        <span class="fw-semibold text-body text-truncate">${cf.getClass().simpleName}</span>
                                                    </span>
                                                    <span class="small text-body-secondary text-truncate">${cf.getClass().package?.name ?: ''}</span>
                                                </li>
                                            </g:each>
                                        </ul>
                                    </div>
                                </g:each>
                            </div>
                            <g:if test="${numSecurityFilters != 0}">
                                <p id="securitychain-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                            </g:if>
                            <g:else>
                                <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                            </g:else>
                            </div>
                        </g:if>

                        <g:if test="${urlMappingsHolder}">
                            <div data-switch-for="urlmappings" class="${runtimePanel == 'urlmappings' ? '' : 'd-none'}">
                            <p class="small text-body-secondary mb-2"><g:message code="welcome.urlmappings.resolve.hint"/></p>
                            <%-- Plain GET back to this page: the resolver works without any JS
                                 and the reload reopens this panel with the result rendered. The
                                 action's fragment survives GET submission, so the reload lands
                                 on this card instead of the top of the page. --%>
                            <form method="get" action="${(request.contextPath ?: '') + '/'}#runtime-beans" class="mb-3 mapping-resolve">
                                <div class="input-group input-group-sm mb-2">
                                    <input type="text" name="resolveUrl" value="${resolveUrlParam}" class="form-control"
                                           placeholder="/book/show/1"
                                           aria-label="${message(code: 'welcome.urlmappings.resolve.url')}">
                                    <g:if test="${resolveActive}">
                                        <a href="${(request.contextPath ?: '') + '/'}#runtime-beans" class="btn btn-outline-secondary d-flex align-items-center"
                                           aria-label="${message(code: 'welcome.urlmappings.resolve.clear')}">
                                            <i class="bi bi-x-lg" aria-hidden="true"></i>
                                        </a>
                                    </g:if>
                                </div>
                                <input type="text" name="resolveParams" value="${resolveParamsParam}"
                                       class="form-control form-control-sm mb-2 resolve-params ${resolveMethodParam == 'POST' ? '' : 'd-none'}"
                                       placeholder="name=value&amp;other=value"
                                       aria-label="${message(code: 'welcome.urlmappings.resolve.params')}">
                                <div class="input-group input-group-sm">
                                    <select name="resolveMethod" class="form-select"
                                            aria-label="${message(code: 'welcome.urlmappings.resolve.method')}">
                                        <g:each var="m" in="${resolveMethods}">
                                            <option value="${m}" ${m == resolveMethodParam ? 'selected' : ''}>${m}</option>
                                        </g:each>
                                    </select>
                                    <select name="resolveAccept" class="form-select"
                                            aria-label="${message(code: 'welcome.urlmappings.resolve.accept')}">
                                        <g:each var="mt" in="${resolveAcceptChoices}">
                                            <option value="${mt}" ${mt == resolveAcceptParam ? 'selected' : ''}>${mt}</option>
                                        </g:each>
                                    </select>
                                    <button type="submit" class="btn btn-outline-secondary"><g:message code="welcome.urlmappings.resolve.button"/></button>
                                </div>
                            </form>
                            <g:if test="${resolvePath}">
                                <g:if test="${resolveMatches}">
                                    <div class="border rounded p-2 mb-3">
                                        <div class="small text-uppercase text-body-secondary fw-semibold mb-1" style="letter-spacing: .04em;">
                                            <g:message code="welcome.urlmappings.resolve.result"/>
                                        </div>
                                        <div class="d-flex align-items-center gap-2 flex-wrap">
                                            <span class="badge text-bg-primary">${resolveMatches[0].httpMethod && resolveMatches[0].httpMethod != '*' ? resolveMatches[0].httpMethod : resolveMethodParam}</span>
                                            <code class="text-break">${resolveInfoPattern(resolveMatches[0]) ?: resolvePath}</code>
                                            <span class="small text-body-secondary text-break">${resolveInfoTarget(resolveMatches[0])}</span>
                                        </div>
                                        <g:set var="winnerParams" value="${resolveInfoParams(resolveMatches[0])}"/>
                                        <g:if test="${winnerParams}">
                                            <div class="mt-1 d-flex align-items-center gap-1 flex-wrap small">
                                                <g:each var="entry" in="${winnerParams}">
                                                    <span class="badge bg-body-tertiary text-body border fw-normal">${entry.key} = ${entry.value}</span>
                                                </g:each>
                                            </div>
                                        </g:if>
                                        <div class="small text-body-secondary mt-1">
                                            <g:message code="welcome.urlmappings.resolve.candidates"/>: ${resolveMatches.size()}<g:if test="${resolveFormat}"> &middot; <g:message code="welcome.urlmappings.resolve.format"/>: <code>${resolveFormat}</code></g:if>
                                        </div>
                                        <%-- Issue exactly the request that was resolved - same URL, same
                                             method, same parameters (carried in the query string, which
                                             Grails reads into params on a POST just like form fields). A
                                             plain link for GET, a plain form for POST; other verbs cannot
                                             be sent from static HTML, so no control. --%>
                                        <g:set var="resolveCallQuery"
                                               value="${[resolveQueryParam, resolveMethodParam == 'POST' ? resolveParamsParam : ''].findAll { it }.join('&')}"/>
                                        <g:set var="resolveCallUrl"
                                               value="${(request.contextPath ?: '') + resolvePath + (resolveCallQuery ? '?' + resolveCallQuery : '')}"/>
                                        <g:if test="${resolveMethodParam == 'GET'}">
                                            <div class="mt-2">
                                                <a href="${resolveCallUrl}" target="_blank" rel="noopener"
                                                   class="btn btn-sm btn-outline-primary">
                                                    <i class="bi bi-box-arrow-up-right me-1" aria-hidden="true"></i><g:message code="welcome.urlmappings.resolve.call"/>
                                                </a>
                                            </div>
                                        </g:if>
                                        <g:elseif test="${resolveMethodParam == 'POST'}">
                                            <form method="post" action="${resolveCallUrl}" target="_blank" class="mt-2 mb-0">
                                                <button type="submit" class="btn btn-sm btn-outline-primary">
                                                    <i class="bi bi-box-arrow-up-right me-1" aria-hidden="true"></i><g:message code="welcome.urlmappings.resolve.call"/>
                                                </button>
                                            </form>
                                        </g:elseif>
                                    </div>
                                </g:if>
                                <g:else>
                                    <div class="alert alert-warning py-2 px-3 small mb-3">
                                        <g:message code="welcome.urlmappings.resolve.none"/> <code>${resolveMethodParam} ${resolvePath}</code>
                                    </div>
                                </g:else>
                            </g:if>
                            <g:if test="${displayedMappingRows}">
                                <div id="urlmappings-list">
                                    <ul class="list-group list-group-flush">
                                        <%-- Long patterns and targets wrap instead of truncating, so the
                                             full mapping is always readable - including on touch devices,
                                             where a hover tooltip would hide it. --%>
                                        <g:each var="row" in="${displayedMappingRows}">
                                            <li class="list-group-item px-2 d-flex align-items-center flex-wrap justify-content-between column-gap-2 row-gap-1 ${row.rank == 1 ? 'border-start border-3 border-primary' : ''}"
                                                data-name="${row.pattern} ${row.method} ${row.target}">
                                                <span class="d-flex align-items-center gap-2 min-w-0">
                                                    <g:if test="${row.rank}">
                                                        <span class="badge ${row.rank == 1 ? 'text-bg-primary' : 'bg-body-tertiary text-body border'}" style="font-variant-numeric: tabular-nums;">#${row.rank}</span>
                                                    </g:if>
                                                    <span class="badge bg-body-tertiary text-body border">${row.method}</span>
                                                    <code class="text-break">${row.pattern}</code>
                                                </span>
                                                <span class="small text-body-secondary ms-auto text-end text-break">${row.target}</span>
                                            </li>
                                        </g:each>
                                    </ul>
                                </div>
                                <p id="urlmappings-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                            </g:if>
                            <g:elseif test="${!resolveActive}">
                                <p class="small text-body-secondary mb-0"><g:message code="welcome.beans.none"/></p>
                            </g:elseif>
                            <g:if test="${urlMappingExcludes}">
                                <p class="small text-body-secondary mt-2 mb-0">
                                    <g:message code="welcome.urlmappings.excludes"/>:
                                    <g:each var="ex" in="${urlMappingExcludes}"><code class="me-1">${ex}</code></g:each>
                                </p>
                            </g:if>
                            </div>
                        </g:if>
                    </div>
                </div>
                </g:if>
            </div>

            <%-- PLUGINS --%>
            <div class="col-12 col-lg-5">
                <div class="card border-1 shadow-sm">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.plugins.title"/></h6>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-body-tertiary text-body border">
                                    ${pluginManager.allPlugins.size()}
                                </span>
                                <div class="dropdown">
                                    <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="dropdown"
                                            data-bs-auto-close="outside" aria-expanded="false"
                                            aria-label="${message(code: 'welcome.filter.name')}">
                                        <i class="bi bi-filter" aria-hidden="true"></i>
                                    </button>
                                    <div class="dropdown-menu dropdown-menu-end p-2">
                                        <input type="search" class="form-control form-control-sm filter-input"
                                               data-filter-list="#plugins-table tbody" data-filter-empty="#plugins-empty"
                                               placeholder="${message(code: 'welcome.filter.name')}"
                                               aria-label="${message(code: 'welcome.filter.name')}">
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="table-responsive">
                            <table id="plugins-table" class="table table-sm table-striped table-hover" data-sortable="true">
                                <thead class="small">
                                <tr>
                                    <th scope="col"
                                        class="text-body-secondary ps-0 fw-semibold sortable"
                                        data-sort-key="name"
                                        role="button"
                                        tabindex="0"
                                        aria-label="${message(code: 'welcome.plugins.sort.name')}">
                                        <g:message code="welcome.plugins.name"/> <span class="sort-hint" aria-hidden="true"></span>
                                    </th>
                                    <th scope="col"
                                        class="text-body-secondary ps-0 fw-semibold text-end sortable"
                                        data-sort-key="version"
                                        role="button"
                                        tabindex="0"
                                        aria-label="${message(code: 'welcome.plugins.sort.version')}">
                                        <span class="sort-hint" aria-hidden="true"></span> <g:message code="welcome.plugins.version"/>
                                    </th>
                                    <th scope="col"
                                        class="text-body-secondary text-end pe-0 sortable"
                                        data-sort-key="order"
                                        role="button"
                                        tabindex="0"
                                        aria-label="${message(code: 'welcome.plugins.sort.order')}">
                                        <span class="sort-hint" aria-hidden="true"></span> <g:message code="welcome.plugins.load.order"/>
                                    </th>
                                </tr>
                                </thead>
                                <tbody class="small">
                                <g:each var="row" in="${pluginsWithOrder}">
                                    <g:set var="pluginName"
                                           value="${row.plugin.name
                                                   .replaceAll(/([A-Z]+)([A-Z][a-z])/, '$1 $2')
                                                   .replaceAll(/([a-z0-9])([A-Z])/, '$1 $2')
                                                   .replaceAll(/[_-]+/, ' ')
                                                   .trim()
                                                   .capitalize()}"
                                    />
                                    <tr data-name="${pluginName}" data-version="${row.plugin.version}" data-order="${row.order}">
                                        <td class="text-truncate">
                                            ${pluginName}
                                        </td>
                                        <td class="text-end" style="font-variant-numeric: tabular-nums;">
                                            ${row.plugin.version}
                                        </td>
                                        <td class="text-end text-body-secondary" style="font-variant-numeric: tabular-nums;">
                                            ${row.order}
                                        </td>
                                    </tr>
                                </g:each>
                                </tbody>
                            </table>
                        </div>
                        <p id="plugins-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                    </div>
                </div>

                <%-- ACTUATORS: shown only when Spring Boot Actuator is present and exposes web endpoints --%>
                <g:set var="actuatorSupplierType"
                       value="${ClassUtils.isPresent('org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier', null) ? ClassUtils.forName('org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier', null) : null}"/>
                <g:set var="actuatorEndpoints"
                       value="${actuatorSupplierType && applicationContext.getBeanNamesForType(actuatorSupplierType) ? applicationContext.getBean(actuatorSupplierType).endpoints.toList().sort { it.endpointId.toString() } : []}"/>
                <g:if test="${actuatorEndpoints}">
                    <g:set var="actuatorBasePath"
                           value="${grailsApplication.config.getProperty('management.endpoints.web.base-path') ?: '/actuator'}"/>
                    <%-- With management.server.port the endpoints are not served on this
                         app's port: link against the management port and its base path. --%>
                    <g:set var="managementPort" value="${grailsApplication.config.getProperty('management.server.port')}"/>
                    <g:set var="actuatorUrlBase"
                           value="${managementPort && managementPort.toString() != request.serverPort.toString() ? '//' + request.serverName + ':' + managementPort + (grailsApplication.config.getProperty('management.server.base-path') ?: '') : request.contextPath}"/>
                    <div class="card border-1 shadow-sm mt-4">
                        <div class="card-body">
                            <div class="d-flex align-items-center justify-content-between mb-3">
                                <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.actuators"/></h6>
                                <span class="badge bg-body-tertiary text-body border">
                                    ${actuatorEndpoints.size()}
                                </span>
                            </div>
                            <ul class="list-group list-group-flush small">
                                <g:each var="endpoint" in="${actuatorEndpoints}">
                                    <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                        <span class="fw-medium">${endpoint.endpointId}</span>
                                        <a href="${actuatorUrlBase}${actuatorBasePath}/${endpoint.rootPath}" target="_blank" rel="noopener"
                                           class="small link-primary link-offset-2 link-underline-opacity-0 link-underline-opacity-75-hover">
                                            ${actuatorBasePath}/${endpoint.rootPath}
                                        </a>
                                    </li>
                                </g:each>
                            </ul>
                        </div>
                    </div>
                </g:if>
                <%-- Development-only diagnostics, matching the runtime internals card. --%>
                <g:if test="${Environment.current == Environment.DEVELOPMENT}">
                <g:set var="mimeTypes"
                       value="${applicationContext.containsBean('mimeTypes') ?
                               applicationContext.getBean('mimeTypes').toList()
                                       .sort { a, b -> ((a.extension ?: '').toLowerCase() <=> (b.extension ?: '').toLowerCase()) ?: (a.name <=> b.name) } : []}"/>
                <div class="card border-1 shadow-sm mt-4">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.mime.types.title"/></h6>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-body-tertiary text-body border">
                                    ${mimeTypes.size()}
                                </span>
                                <g:if test="${mimeTypes}">
                                    <div class="dropdown">
                                        <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="dropdown"
                                                data-bs-auto-close="outside" aria-expanded="false"
                                                aria-label="${message(code: 'welcome.filter.name')}">
                                            <i class="bi bi-filter" aria-hidden="true"></i>
                                        </button>
                                        <div class="dropdown-menu dropdown-menu-end p-2">
                                            <input type="search" class="form-control form-control-sm filter-input"
                                                   data-filter-list="#mime-types-table tbody" data-filter-empty="#mime-types-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                        </div>
                                    </div>
                                </g:if>
                            </div>
                        </div>

                        <div class="table-responsive">
                            <table id="mime-types-table" class="table table-sm table-striped table-hover"
                                   data-sortable="true" data-sort-default="extension">
                                <thead class="small">
                                <tr>
                                    <th scope="col"
                                        class="text-body-secondary ps-0 fw-semibold sortable"
                                        data-sort-key="extension"
                                        role="button"
                                        tabindex="0"
                                        aria-label="${message(code: 'welcome.mime.sort.extension')}">
                                        <g:message code="welcome.mime.extension"/> <span class="sort-hint" aria-hidden="true"></span>
                                    </th>
                                    <th scope="col"
                                        class="text-body-secondary text-end pe-0 fw-semibold sortable"
                                        data-sort-key="type"
                                        role="button"
                                        tabindex="0"
                                        aria-label="${message(code: 'welcome.mime.sort.type')}">
                                        <span class="sort-hint" aria-hidden="true"></span> <g:message code="welcome.mime.type"/>
                                    </th>
                                </tr>
                                </thead>
                                <tbody class="small">
                                <g:each var="mime" in="${mimeTypes}">
                                    <tr data-name="${mime.extension} ${mime.name}" data-extension="${mime.extension}" data-type="${mime.name}">
                                        <td class="text-truncate">
                                            ${mime.extension}
                                        </td>
                                        <td class="text-end text-body-secondary">
                                            ${mime.name}
                                        </td>
                                    </tr>
                                </g:each>
                                </tbody>
                            </table>
                        </div>
                        <p id="mime-types-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                    </div>
                </div>

                <%-- DATASTORES: shown only when GORM is on the classpath and at least one
                     datastore bean is registered, resolved without a hard class reference. --%>
                <g:set var="datastoreType"
                       value="${ClassUtils.isPresent('org.grails.datastore.mapping.core.Datastore', null) ? ClassUtils.forName('org.grails.datastore.mapping.core.Datastore', null) : null}"/>
                <g:set var="datastores"
                       value="${datastoreType ? applicationContext.getBeansOfType(datastoreType).entrySet().toList().sort { it.key.toLowerCase() } : []}"/>
                <g:if test="${datastores}">
                    <div class="card border-1 shadow-sm mt-4">
                        <div class="card-body">
                            <div class="d-flex align-items-center justify-content-between mb-3">
                                <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.datastores.title"/></h6>
                                <span class="badge bg-body-tertiary text-body border">
                                    ${datastores.size()}
                                </span>
                            </div>
                            <g:each var="ds" in="${datastores}" status="dsIndex">
                                <g:set var="datastoreListeners"
                                       value="${ds.value.mappingContext.eventListeners.toList()
                                               .sort { (it.getClass().simpleName ?: it.getClass().name.tokenize('.').last()).toLowerCase() }}"/>
                                <div class="${dsIndex > 0 ? 'mt-4' : ''}">
                                    <div class="px-2 py-2 bg-body-tertiary">
                                        <div class="d-flex align-items-center justify-content-between">
                                            <div class="small text-uppercase text-body-secondary fw-semibold"
                                                 style="letter-spacing: .04em;">
                                                ${ds.key}
                                            </div>
                                            <div class="small text-body-secondary">
                                                <g:message code="welcome.datastores.listeners"/> (${datastoreListeners.size()})
                                            </div>
                                        </div>
                                    </div>
                                    <ul class="list-group list-group-flush small">
                                        <g:each var="dsListener" in="${datastoreListeners}">
                                            <li class="list-group-item px-2 d-flex align-items-center justify-content-between gap-2"
                                                title="${dsListener}">
                                                <span class="fw-medium text-truncate">${dsListener.getClass().simpleName ?: dsListener.getClass().name.tokenize('.').last()}</span>
                                                <span class="small text-body-secondary text-truncate">${dsListener.getClass().package?.name ?: ''}</span>
                                            </li>
                                        </g:each>
                                    </ul>
                                </div>
                            </g:each>
                        </div>
                    </div>
                </g:if>
                </g:if>
            </div>
        </div>
    </div>
</main>
<asset:javascript src="welcome.js"/>
</body>
</html>
