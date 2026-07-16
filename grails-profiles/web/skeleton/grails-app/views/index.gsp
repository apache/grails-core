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
                                <span class="fw-medium text-truncate ms-3"><g:meta name="info.app.name"/></span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.app.version"/></span>
                                <span class="fw-medium" style="font-variant-numeric: tabular-nums;">
                                    <g:meta name="info.app.version"/>
                                </span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.app.profile"/></span>
                                <span class="fw-medium text-truncate ms-3">
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
                                <span class="fw-medium text-truncate ms-3">${servletContext.serverInfo}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.server.host"/></span>
                                <span class="fw-medium text-truncate ms-3">${InetAddress.localHost}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.server.os"/></span>
                                <span class="fw-medium text-truncate ms-3">
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

                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.controllers"/></span>
                                <span class="fw-medium">${numControllers}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.domains"/></span>
                                <span class="fw-medium">${grailsApplication.domainClasses.size()}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.services"/></span>
                                <span class="fw-medium">${grailsApplication.serviceClasses.size()}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary"><g:message code="welcome.artefact.taglibs"/></span>
                                <span class="fw-medium">${grailsApplication.tagLibClasses.size()}</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

        </div>
    </div>

    <%-- AVAILABLE CONTROLLERS --%>
    <div class="container-lg mt-4">
        <div class="row g-4 align-items-start">
            <div class="col-12 col-lg-7">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-1">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="welcome.controllers.title"/></h6>
                            <div class="d-flex align-items-center gap-2">
                                <span class="badge bg-body-tertiary text-body border">
                                    ${numControllers}
                                </span>
                                <g:if test="${numControllers != 0}">
                                    <div class="dropdown">
                                        <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="dropdown"
                                                data-bs-auto-close="outside" aria-expanded="false"
                                                aria-label="${message(code: 'welcome.filter.name')}">
                                            <i class="bi bi-filter" aria-hidden="true"></i>
                                        </button>
                                        <div class="dropdown-menu dropdown-menu-end p-2">
                                            <input type="search" class="form-control form-control-sm filter-input"
                                                   data-filter-list="#controllers-list" data-filter-empty="#controllers-empty"
                                                   placeholder="${message(code: 'welcome.filter.name')}"
                                                   aria-label="${message(code: 'welcome.filter.name')}">
                                        </div>
                                    </div>
                                </g:if>
                            </div>
                        </div>
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

                                        <li class="list-group-item list-group-item-action px-2" data-name="${simpleName}">
                                            <div class="d-flex align-items-center justify-content-between gap-3">
                                                <g:link controller="${c.logicalPropertyName}"
                                                        namespace="${c.namespace}"
                                                        class="d-flex align-items-center gap-3 text-decoration-none min-w-0 flex-grow-1">
                                                    <div class="min-w-0">
                                                        <div class="fw-semibold text-body text-truncate">
                                                            ${simpleName}
                                                        </div>
                                                    </div>
                                                </g:link>

                                                <a href="${controllerUrl}"
                                                   class="small link-primary link-offset-2 link-underline-opacity-0 link-underline-opacity-75-hover flex-shrink-0">
                                                    ${controllerUrl}
                                                </a>
                                            </div>
                                        </li>
                                    </g:each>
                                </ul>
                            </div>
                        </g:each>
                        </div>
                        <p id="controllers-empty" class="small text-body-secondary d-none mb-0"><g:message code="welcome.filter.none"/></p>
                    </div>
                </div>
            </div>

            <%-- PLUGINS --%>
            <div class="col-12 col-lg-5">
                <div class="card border-1 shadow-sm h-100">
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
            </div>
        </div>
    </div>
</main>
<asset:javascript src="welcome.js"/>
</body>
</html>
