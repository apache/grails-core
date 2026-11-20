<%@ page import="grails.plugin.springsecurity.SpringSecurityUtils" %>
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
<head>
    <g:set var="layoutName" value="${SpringSecurityUtils.securityConfig.getProperty('oauth2.view.layout')}"/>
    <meta name="layout" content="${layoutName ?: 'main'}"/>
    <title><g:message code="springSecurity.oauth.registration.title" default="Create or Link Account"/></title>
</head>

<body>
<div class="row justify-content-center">
    <div class="col-11 col-md-8 col-lg-6 col-xl-5">

        <g:if test='${flash.error}'>
            <div class="alert alert-danger" role="alert">${flash.error}</div>
        </g:if>

        <div class="card shadow-sm mb-4">
            <div class="card-header">
                <h1 class="h5 mb-0"><g:message code="springSecurity.oauth.registration.create.legend"
                                               default="Create a new account"/></h1>
            </div>
            <div class="card-body">
                <g:hasErrors bean="${createAccountCommand}">
                    <div class="alert alert-danger" role="alert">
                        <g:renderErrors bean="${createAccountCommand}" as="list"/>
                    </div>
                </g:hasErrors>

                <g:form action="createAccount" method="post" autocomplete="off">
                    <div class="mb-3">
                        <label class="form-label" for="username"><g:message code="OAuthCreateAccountCommand.username.label"
                                                                            default="Username"/></label>
                        <g:textField name='username' class='form-control${hasErrors(bean: createAccountCommand, field: "username", " is-invalid")}'
                                     value='${createAccountCommand?.username}'/>
                    </div>

                    <div class="mb-3">
                        <label class="form-label" for="password1"><g:message code="OAuthCreateAccountCommand.password1.label"
                                                                             default="Password"/></label>
                        <g:passwordField name='password1' class='form-control${hasErrors(bean: createAccountCommand, field: "password1", " is-invalid")}'
                                         value='${createAccountCommand?.password1}'/>
                    </div>

                    <div class="mb-3">
                        <label class="form-label" for="password2"><g:message code="OAuthCreateAccountCommand.password2.label"
                                                                             default="Password re-type"/></label>
                        <g:passwordField name='password2' class='form-control${hasErrors(bean: createAccountCommand, field: "password2", " is-invalid")}'
                                         value='${createAccountCommand?.password2}'/>
                    </div>

                    <g:submitButton class="btn btn-primary w-100"
                            name="${message(code: 'springSecurity.oauth.registration.create.button', default: 'Create')}"/>
                </g:form>
            </div>
        </div>

        <div class="card shadow-sm mb-4">
            <div class="card-header">
                <h1 class="h5 mb-0"><g:message code="springSecurity.oauth.registration.login.legend"
                                               default="Link to an existing account"/></h1>
            </div>
            <div class="card-body">
                <g:hasErrors bean="${linkAccountCommand}">
                    <div class="alert alert-danger" role="alert">
                        <g:renderErrors bean="${linkAccountCommand}" as="list"/>
                    </div>
                </g:hasErrors>

                <g:form action="linkAccount" method="post" autocomplete="off">
                    <div class="mb-3">
                        <label class="form-label" for="linkUsername"><g:message code="OAuthLinkAccountCommand.username.label"
                                                                                default="Username"/></label>
                        <g:textField name='username' id='linkUsername' class='form-control${hasErrors(bean: linkAccountCommand, field: "username", " is-invalid")}'
                                     value='${linkAccountCommand?.username}'/>
                    </div>

                    <div class="mb-3">
                        <label class="form-label" for="linkPassword"><g:message code="OAuthLinkAccountCommand.password.label"
                                                                                default="Password"/></label>
                        <g:passwordField name='password' id='linkPassword' class='form-control${hasErrors(bean: linkAccountCommand, field: "password", " is-invalid")}'
                                         value='${linkAccountCommand?.password}'/>
                    </div>

                    <div class="mb-3 form-check">
                        <input type='checkbox' class='form-check-input' name='${rememberMeParameter}' id='remember_me'/>
                        <label class="form-check-label" for="remember_me"><g:message code="springSecurity.login.remember.me.label"
                                                                                     default="Remember me"/></label>
                    </div>

                    <g:submitButton class="btn btn-primary w-100"
                            name="${message(code: 'springSecurity.oauth.registration.login.button', default: 'Login')}"/>
                </g:form>
            </div>
        </div>

        <p class="text-center">
            <g:link controller="login" action="auth"><g:message code="springSecurity.oauth.registration.back"
                                                                default="Back to login page"/></g:link>
        </p>
    </div>
</div>
</body>
