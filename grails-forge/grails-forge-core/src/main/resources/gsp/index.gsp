<%@ page import="grails.util.Environment"%>
<%@ page import="org.springframework.boot.SpringBootVersion"%>
<%@ page import="org.springframework.core.SpringVersion"%>
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
    <title>Welcome to Grails</title>
    <meta name="layout" content="main"/>
    <asset:stylesheet src="welcome.css"/>
</head>
<body>
<main id="content" role="main" class="pb-4 pb-md-5">
    <div class="container-lg py-2 py-md-3">
        <div class="row align-items-top g-4">

            <%-- WELCOME MESSAGE --%>
            <div class="col-12 col-md-7">
                <h1 class="display-6 fw-semibold mb-2">Welcome to Grails</h1>
                <p class="lead text-body-secondary">
                    Congratulations, you have successfully started a Grails application.
                </p>
                <p class="text-body-secondary">
                    At the moment this is the default page, feel free to modify it to either
                    redirect to a controller or display whatever content you may choose.
                </p>
            </div>

            <%-- RUNTIME VERSIONS --%>
            <div class="col-12 col-md-5">
                <div class="card border-1 shadow-sm">
                    <div class="card-body">
                        <h6 class="card-title mb-3 fw-semibold">Runtime versions</h6>
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
        </div>
    </div>
    <div class="container-lg">
        <div class="row g-4 align-items-stretch">

            <%-- APPLICATION INFO --%>
            <div class="col-12 col-lg-4">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold">Application</h6>
                            <g:if test="${Environment.reloadingAgentEnabled}">
                                <span class="reload-indicator text-success" role="status" aria-label="Reloading active">
                                    <span class="reload-dot ping" aria-hidden="true"></span>
                                    <span class="text-body-secondary">Reloading active</span>
                                </span>
                            </g:if>
                            <g:else>
                                <span class="reload-indicator text-danger" role="status" aria-label="Reloading inactive">
                                    <span class="reload-dot" aria-hidden="true"></span>
                                    <span class="text-body-secondary">Reloading inactive</span>
                                </span>
                            </g:else>
                        </div>
                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Name</span>
                                <span class="fw-medium text-truncate ms-3"><g:meta name="info.app.name"/></span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Version</span>
                                <span class="fw-medium" style="font-variant-numeric: tabular-nums;">
                                    <g:meta name="info.app.version"/>
                                </span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Profile</span>
                                <span class="fw-medium text-truncate ms-3">
                                    ${grailsApplication.config.getProperty('grails.profile')}
                                </span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Environment</span>
                                <span class="fw-medium">${Environment.current.name}</span>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

            <%-- SERVER INFO --%>
            <div class="col-12 col-lg-4">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold">Server</h6>
                        </div>
                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Servlet Container</span>
                                <span class="fw-medium text-truncate ms-3">${servletContext.serverInfo}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Host</span>
                                <span class="fw-medium text-truncate ms-3">${InetAddress.localHost}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">OS</span>
                                <span class="fw-medium text-truncate ms-3">
                                    ${System.getProperty('os.name')} ${System.getProperty('os.version')} (${System.getProperty('os.arch')})
                                </span>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

            <%-- ARTEFACT COUNTS --%>
            <div class="col-12 col-lg-4">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold">Artefact counts</h6>
                        </div>

                        <ul class="list-group list-group-flush small">
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Controllers</span>
                                <span class="fw-medium">${numControllers}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Domains</span>
                                <span class="fw-medium">${grailsApplication.domainClasses.size()}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Services</span>
                                <span class="fw-medium">${grailsApplication.serviceClasses.size()}</span>
                            </li>
                            <li class="list-group-item d-flex justify-content-between align-items-center px-0">
                                <span class="text-body-secondary">Tag Libraries</span>
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
                    <div class="card-body p-4 p-md-5">
                        <div class="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-2">
                            <div>
                                <h2 class="h4 mb-1">Available Controllers</h2>
                                <p class="text-body-secondary mb-0">
                                    ${numControllers} controller${numControllers != 1 ? 's' : ''} detected.
                                </p>
                            </div>
                            <g:if test="${numControllers != 0}">
                                <div class="small text-body-secondary">
                                    Click a controller to execute its default action.
                                </div>
                            </g:if>
                        </div>
                        <hr class="my-3 my-md-4 text-body-tertiary"/>
                        <g:set var="controllersByNamespace"
                               value="${grailsApplication.controllerClasses
                                       .groupBy { cc -> ((cc.namespace ?: '').trim()) ?: 'default' }
                                       .sort { a, b -> a.key.toString().toLowerCase() <=> b.key.toString().toLowerCase() }}"/>

                        <g:each var="nsEntry" in="${controllersByNamespace}" status="nsIndex">
                            <div class="${nsIndex > 0 ? 'mt-4' : ''}">
                                <div class="px-0 py-2 bg-body-tertiary">
                                    <div class="d-flex align-items-center justify-content-between">
                                        <div class="small text-uppercase text-body-secondary fw-semibold"
                                             style="letter-spacing: .04em;">
                                            <g:if test="${nsEntry.key != 'default'}">
                                                ${nsEntry.key}
                                            </g:if>
                                            <g:else>
                                                Default namespace
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

                                        <li class="list-group-item px-0">
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
                </div>
            </div>

            <%-- PLUGINS --%>
            <div class="col-12 col-lg-5">
                <div class="card border-1 shadow-sm h-100">
                    <div class="card-body">
                        <div class="d-flex align-items-center justify-content-between mb-3">
                            <h6 class="card-title mb-0 fw-semibold">Installed plugins</h6>
                            <span class="badge text-bg-light border">
                                ${pluginManager.allPlugins.size()}
                            </span>
                        </div>

                        <div class="table-responsive">
                            <table class="table table-sm table-striped table-hover" data-sortable="true">
                                <thead class="table-light small">
                                <tr>
                                    <th scope="col"
                                        class="text-body-secondary ps-0 fw-semibold sortable"
                                        data-sort-key="name"
                                        role="button"
                                        tabindex="0"
                                        aria-label="Sort by name">
                                        Name <span class="sort-hint" aria-hidden="true"></span>
                                    </th>
                                    <th scope="col"
                                        class="text-body-secondary ps-0 fw-semibold text-end sortable"
                                        data-sort-key="version"
                                        role="button"
                                        tabindex="0"
                                        aria-label="Sort by version">
                                        <span class="sort-hint" aria-hidden="true"></span> Version
                                    </th>
                                    <th scope="col"
                                        class="text-body-secondary text-end pe-0 sortable"
                                        data-sort-key="order"
                                        role="button"
                                        tabindex="0"
                                        aria-label="Sort by load order">
                                        <span class="sort-hint" aria-hidden="true"></span> Load order
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
                    </div>
                </div>
            </div>
        </div>
    </div>
</main>
<asset:javascript src="welcome.js"/>
</body>
</html>
