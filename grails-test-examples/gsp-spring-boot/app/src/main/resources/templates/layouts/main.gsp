<%@ page import="org.springframework.boot.SpringBootVersion; org.springframework.core.SpringVersion" %>
<%--
  ~  Licensed to the Apache Software Foundation (ASF) under one
  ~  or more contributor license agreements.  See the NOTICE file
  ~  distributed with this work for additional information
  ~  regarding copyright ownership.  The ASF licenses this file
  ~  to you under the Apache License, Version 2.0 (the
  ~  "License"); you may not use this file except in compliance
  ~  with the License.  You may obtain a copy of the License at
  ~
  ~    https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~  Unless required by applicable law or agreed to in writing,
  ~  software distributed under the License is distributed on an
  ~  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~  KIND, either express or implied.  See the License for the
  ~  specific language governing permissions and limitations
  ~  under the License.
  --%>
<!doctype html>
<html lang="en">
    <head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width, initial-scale=1"/>
        <title>Decorated <g:layoutTitle /></title>
        <asset:stylesheet src="application.css"/>
        <%-- In the head, so the colour mode is set before the page paints rather than after it. --%>
        <asset:javascript src="theme.js"/>
        <g:layoutHead />
    </head>
    <body class="bg-body-tertiary d-flex flex-column min-vh-100">
        <nav class="navbar navbar-expand-lg bg-body border-bottom shadow-sm">
            <div class="container">
                <a class="navbar-brand d-flex align-items-center gap-2" href="${request.contextPath}/">
                    <i class="bi bi-filetype-html"></i>GSP in Spring Boot
                </a>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav"
                        aria-controls="mainNav" aria-expanded="false" aria-label="Toggle navigation">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse" id="mainNav">
                    <ul class="navbar-nav me-auto">
                        <%-- The two renderings of the same form, offered only where a JSP can be
                             served: an executable jar packages none for Jasper to compile. --%>
                        <g:if test="${jspAvailable}">
                            <li class="nav-item">
                                <a class="nav-link ${viewType == 'GSP' ? 'active' : ''}" href="${request.contextPath}/gsp">GSP</a>
                            </li>
                            <li class="nav-item">
                                <a class="nav-link ${viewType == 'JSP' ? 'active' : ''}" href="${request.contextPath}/jsp">JSP</a>
                            </li>
                        </g:if>
                    </ul>
                    <ul class="navbar-nav ms-auto">
                        <li class="nav-item dropdown">
                            <a class="nav-link dropdown-toggle d-flex align-items-center" href="#" id="themeDropdown"
                               role="button" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Toggle theme">
                                <i class="bi bi-circle-half theme-icon-active"></i>
                                <span class="d-lg-none ms-2">Theme</span>
                            </a>
                            <ul class="dropdown-menu dropdown-menu-end" aria-labelledby="themeDropdown">
                                <li>
                                    <button type="button" class="dropdown-item d-flex align-items-center"
                                            data-bs-theme-value="light" aria-pressed="false">
                                        <i class="bi bi-sun-fill me-2"></i>Light<i class="bi bi-check ms-auto d-none"></i>
                                    </button>
                                </li>
                                <li>
                                    <button type="button" class="dropdown-item d-flex align-items-center"
                                            data-bs-theme-value="dark" aria-pressed="false">
                                        <i class="bi bi-moon-stars-fill me-2"></i>Dark<i class="bi bi-check ms-auto d-none"></i>
                                    </button>
                                </li>
                                <li>
                                    <button type="button" class="dropdown-item d-flex align-items-center"
                                            data-bs-theme-value="auto" aria-pressed="false">
                                        <i class="bi bi-circle-half me-2"></i>Auto<i class="bi bi-check ms-auto d-none"></i>
                                    </button>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </div>
            </div>
        </nav>

        <main class="container flex-grow-1 py-4">
            <div class="row justify-content-center">
                <div class="col-12 col-lg-7">
                    <div class="d-flex align-items-center justify-content-between mb-3">
                        <h1 class="h3 mb-0">Rendered by ${viewType}</h1>
                        <span class="badge text-bg-secondary">layouts/main.gsp</span>
                    </div>
                    <g:layoutBody />
                </div>
            </div>
        </main>

        <footer class="border-top bg-body py-3">
            <div class="container small text-body-secondary d-flex flex-wrap justify-content-between gap-2">
                <span>Spring ${SpringVersion.getVersion()} &middot; Spring Boot ${SpringBootVersion.getVersion()}</span>
                <g:if test="${!jspAvailable}">
                    <span>The JSP rendering is offered where a JSP can be served - the war, or bootRun</span>
                </g:if>
            </div>
        </footer>
        <asset:javascript src="application.js"/>
    </body>
</html>
