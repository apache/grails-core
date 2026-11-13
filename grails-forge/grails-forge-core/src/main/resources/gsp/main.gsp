<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="Grails"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>
    <asset:stylesheet src="application.css"/>
    <asset:javascript src="theme.js"/>
    <g:layoutHead/>
</head>

<body>

<nav class="navbar navbar-expand-lg bg-body border-bottom shadow-sm">
    <div class="container-lg">
        <a class="navbar-brand d-flex align-items-center" href="${request.contextPath}/">
            <asset:image class="w-75" src="grails.svg" alt="Grails Logo"/>
        </a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse"
                data-bs-target="#mainNav" aria-controls="mainNav" aria-expanded="false"
                aria-label="${message(code: 'layout.nav.toggle', default: 'Toggle navigation')}">
            <span class="navbar-toggler-icon"></span>
        </button>
        <g:set var="navControllers"
               value="${grailsApplication.controllerClasses.toList().sort { it.fullName }}"/>
        <div class="collapse navbar-collapse" id="mainNav">
        <ul class="navbar-nav me-auto">
        <g:if test="${navControllers}">
            <%-- A filter earns its place only once the list is long enough to be a
                 chore to scan; below the threshold the plain list is quicker. --%>
            <g:set var="showNavFilter" value="${navControllers.size() > 8}"/>
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="controllersDropdown" role="button"
                       data-bs-toggle="dropdown" aria-expanded="false">
                        <g:message code="welcome.artefact.controllers"/>
                    </a>
                    <ul class="dropdown-menu" id="controllersMenu" aria-labelledby="controllersDropdown"
                        style="max-height: 60vh; overflow-y: auto;">
                        <g:if test="${showNavFilter}">
                            <li class="position-sticky top-0 bg-body border-bottom px-2 pt-1 pb-2" style="z-index: 2;">
                                <input type="search" class="form-control form-control-sm nav-filter-input"
                                       data-filter-scope="#controllersMenu"
                                       placeholder="${message(code: 'welcome.filter.name')}"
                                       aria-label="${message(code: 'welcome.filter.name')}">
                            </li>
                        </g:if>
                        <g:each var="c" in="${navControllers}">
                            <%-- A controller whose default action cannot be requested with
                                 GET (per allowedMethods, e.g. a POST-only logout) is invoked
                                 through a form using the method it allows, with a badge
                                 showing the verb: a plain link would only produce a 405. --%>
                            <g:set var="navMethods"
                                   value="${c.getPropertyValue('allowedMethods') instanceof Map ? ((Map) c.getPropertyValue('allowedMethods')).get(c.defaultAction ?: 'index') : null}"/>
                            <g:set var="navGetOk"
                                   value="${navMethods == null || 'GET' in [navMethods].flatten()*.toString()*.toUpperCase()}"/>
                            <g:set var="navControllerName" value="${(c.fullName ?: '')
                                    .tokenize('.')
                                    .last()
                                    .replaceFirst(/Controller$/, '')}"/>
                            <g:set var="navControllerLabel"
                                   value="${((c.namespace ?: '').trim()) ? "${c.namespace} / ${navControllerName}" : navControllerName}"/>
                            <li data-name="${navControllerLabel}">
                                <g:if test="${navGetOk}">
                                <g:link controller="${c.logicalPropertyName}" namespace="${c.namespace}"
                                        class="dropdown-item">${navControllerLabel}</g:link>
                                </g:if>
                                <g:else>
                                <form action="${createLink(controller: c.logicalPropertyName, namespace: c.namespace)}" method="post" class="m-0">
                                    <button type="submit" class="dropdown-item d-flex align-items-center justify-content-between gap-3">
                                        ${navControllerLabel}
                                        <span class="badge bg-body-tertiary text-body-secondary border">${[navMethods].flatten()*.toString()*.toUpperCase().join(' / ')}</span>
                                    </button>
                                </form>
                                </g:else>
                            </li>
                        </g:each>
                        <g:if test="${showNavFilter}">
                            <li class="nav-filter-empty dropdown-item-text small text-body-secondary d-none">
                                <g:message code="welcome.filter.none"/>
                            </li>
                        </g:if>
                    </ul>
                </li>
        </g:if>
            <%-- Navbar items contributed by the rendered page (e.g. plugin screens)
                 through a <content tag="nav"> block of <li> elements. --%>
            <g:pageProperty name="page.nav"/>
        </ul>
        <ul class="navbar-nav ms-auto">
            <%-- The whole language menu: only the locales this app is translated into, the
                 configured default pinned above a divider so a visitor who switched to a
                 language they cannot read has a way back, the current one marked active, and
                 each name titlecased for standalone display. Renders nothing when the app has
                 a single locale. Every class is overridable, and supplying a body instead
                 gives full control of the markup for non-Bootstrap layouts. --%>
            <g:localeSelect available="true" pinDefault="true" type="dropdown"/>
            <li class="nav-item dropdown">
                <a class="nav-link dropdown-toggle d-flex align-items-center" href="#" id="themeDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="${message(code: 'layout.theme.toggle')}">
                    <i class="bi bi-circle-half theme-icon-active"></i>
                    <span class="d-lg-none ms-2"><g:message code="layout.theme.toggle"/></span>
                </a>
                <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="themeDropdown">
                    <li>
                        <button type="button" class="dropdown-item d-flex align-items-center" data-bs-theme-value="light" aria-pressed="false">
                            <i class="bi bi-sun-fill me-2"></i><g:message code="layout.theme.light"/><i class="bi bi-check ms-auto d-none"></i>
                        </button>
                    </li>
                    <li>
                        <button type="button" class="dropdown-item d-flex align-items-center" data-bs-theme-value="dark" aria-pressed="false">
                            <i class="bi bi-moon-stars-fill me-2"></i><g:message code="layout.theme.dark"/><i class="bi bi-check ms-auto d-none"></i>
                        </button>
                    </li>
                    <li>
                        <button type="button" class="dropdown-item d-flex align-items-center" data-bs-theme-value="auto" aria-pressed="false">
                            <i class="bi bi-circle-half me-2"></i><g:message code="layout.theme.auto"/><i class="bi bi-check ms-auto d-none"></i>
                        </button>
                    </li>
                </ul>
            </li>
            <%-- Right-aligned navbar items contributed by the rendered page through a
                 <content tag="navActions"> block of <li> elements (e.g. a security
                 plugin's account menu). Rendered after language and theme so account
                 controls sit rightmost, matching the built-in sign-in block below. --%>
            <g:pageProperty name="page.navActions"/>
            <%-- Sign-in affordance, rendered whenever Spring Security is on the classpath -
                 the plain starter or the security plugin alike, resolved without a hard class
                 reference. POST /logout is Spring Security's default LogoutFilter URL and the
                 plugin's POST-only LogoutController route; /login is the starter's form-login
                 page, which the plugin's LoginController also answers. g:form carries the
                 CSRF token through the registered RequestDataValueProcessor. A page that
                 contributes its own navActions (e.g. a security plugin's account menu)
                 supersedes this block. --%>
            <g:if test="${!pageProperty(name: 'page.navActions') && org.springframework.util.ClassUtils.isPresent('org.springframework.security.core.context.SecurityContextHolder', null)}">
                <g:set var="securityContextHolderClass"
                       value="${org.springframework.util.ClassUtils.forName('org.springframework.security.core.context.SecurityContextHolder', null)}"/>
                <g:set var="securityAuthentication"
                       value="${securityContextHolderClass.context?.authentication}"/>
                <g:set var="securityLoggedIn"
                       value="${securityAuthentication?.authenticated &&
                               !org.springframework.util.ClassUtils.forName('org.springframework.security.authentication.AnonymousAuthenticationToken', null).isInstance(securityAuthentication)}"/>
                <g:if test="${securityLoggedIn}">
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle d-flex align-items-center" href="#" id="userDropdown" role="button"
                           data-bs-toggle="dropdown" aria-expanded="false">
                            <i class="bi bi-person-circle me-1"></i>${securityAuthentication.name}
                        </a>
                        <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="userDropdown">
                            <li>
                                <g:form url="[uri: '/logout']" method="post">
                                    <button type="submit" class="dropdown-item">
                                        <i class="bi bi-box-arrow-right me-2"></i><g:message code="layout.logout"/>
                                    </button>
                                </g:form>
                            </li>
                        </ul>
                    </li>
                </g:if>
                <g:else>
                    <li class="nav-item d-flex align-items-center ms-lg-2">
                        <a class="btn btn-primary btn-sm" href="${request.contextPath}/login">
                            <g:message code="layout.login"/>
                        </a>
                    </li>
                </g:else>
            </g:if>
        </ul>
        </div>
    </div>
</nav>

<div class="bg-body-tertiary">
    <div class="container-lg py-4">
        <g:flashMessages />
        <g:layoutBody/>
    </div>
</div>

<footer class="border-top py-5" role="contentinfo">
    <div class="container-lg">
        <div class="row g-4">
            <div class="col-12 col-md-4">
                <a class="card h-100 text-decoration-none shadow-sm border-1"
                   href="https://guides.grails.org" target="_blank" rel="noopener">
                    <div class="card-body p-4">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="layout.guides.title"/></h6>
                            <asset:image src="advancedgrails.svg" alt="${message(code: 'layout.guides.title')}" width="34" height="34"/>
                        </div>
                        <p class="card-text text-body-secondary mb-0">
                            <g:message code="layout.guides.text"/>
                        </p>
                    </div>
                </a>
            </div>
            <div class="col-12 col-md-4">
                <a class="card h-100 text-decoration-none shadow-sm border-1"
                   href="https://grails.apache.org/docs/" target="_blank" rel="noopener">
                    <div class="card-body p-4">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="layout.docs.title"/></h6>
                            <asset:image src="documentation.svg" alt="${message(code: 'layout.docs.title')}" width="34" height="34"/>
                        </div>
                        <p class="card-text text-body-secondary mb-0">
                            <g:message code="layout.docs.text"/>
                        </p>
                    </div>
                </a>
            </div>
            <div class="col-12 col-md-4">
                <a class="card h-100 text-decoration-none shadow-sm border-1"
                   href="https://grails.apache.org/community.html" target="_blank" rel="noopener">
                    <div class="card-body p-4">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <h6 class="card-title mb-0 fw-semibold"><g:message code="layout.community.title"/></h6>
                            <asset:image src="community.svg" alt="${message(code: 'layout.community.title')}" width="34" height="34"/>
                        </div>
                        <p class="card-text text-body-secondary mb-0">
                            <g:message code="layout.community.text"/>
                        </p>
                    </div>
                </a>
            </div>
        </div>
    </div>
</footer>
<div id="spinner" class="position-absolute top-0 end-0 p-1" style="display:none;">
    <div class="spinner-border spinner-border-sm" role="status">
        <span class="visually-hidden"><g:message code="layout.loading"/></span>
    </div>
</div>
<asset:javascript src="application.js"/>
</body>
</html>
