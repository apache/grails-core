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
<g:set var='securityConfig' value='${applicationContext.springSecurityService.securityConfig}'/>
<sec:ifLoggedIn>
	<span class="navbar-text" id="loginLinkContainer">
		<g:message code='spring.security.ui.login.loggedInAs' args='[sec.username()]'/>
	</span>
	<form action="${createLink(controller: 'logout')}" method="post" class="d-flex mb-0">
		<button type="submit" id="logout" class="btn btn-outline-secondary btn-sm"><g:message code='spring.security.ui.login.logout'/></button>
	</form>
</sec:ifLoggedIn>
<sec:ifSwitched>
	<a class="btn btn-outline-warning btn-sm" href="${request.contextPath}${securityConfig.switchUser.exitUserUrl}">
		<g:message code='spring.security.ui.login.resumeAs' args='[sec.switchedUserOriginalUsername()]'/>
	</a>
</sec:ifSwitched>
<sec:ifNotLoggedIn>
	<button type="button" id="loginLink" class="btn btn-primary btn-sm" data-bs-toggle="modal" data-bs-target="#loginModal">
		<g:message code='spring.security.ui.login.login'/>
	</button>
	<div class="modal fade" id="loginModal" tabindex="-1" aria-labelledby="loginModalLabel" aria-hidden="true">
		<div class="modal-dialog modal-dialog-centered">
			<div class="modal-content">
				<div class="modal-header">
					<h1 class="modal-title fs-5" id="loginModalLabel"><g:message code='spring.security.ui.login.signin'/></h1>
					<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="${message(code: 'spring.security.ui.login.cancel')}"></button>
				</div>
				<div class="modal-body">
					<form action="${request.contextPath}${securityConfig.apf.filterProcessesUrl}" method="post" id="ajaxLoginForm" name="ajaxLoginForm" autocomplete="off">
						<div class="mb-3">
							<label class="form-label" for="ajaxUsername"><g:message code='spring.security.ui.login.username'/></label>
							<input type="text" class="form-control" name="${securityConfig.apf.usernameParameter}" id="ajaxUsername" autocapitalize="none"/>
						</div>
						<div class="mb-3">
							<label class="form-label" for="ajaxPassword"><g:message code='spring.security.ui.login.password'/></label>
							<div class="input-group">
								<input type="password" class="form-control" name="${securityConfig.apf.passwordParameter}" id="ajaxPassword"/>
								<button class="btn btn-outline-secondary" type="button" aria-pressed="false"
								        onclick="s2uiTogglePassword(this, 'ajaxPassword')"
								        aria-label="${message(code: 'springSecurity.login.password.toggle', default: 'Show or hide password')}">
									<i class="bi bi-eye" aria-hidden="true"></i>
								</button>
							</div>
						</div>
						<div class="mb-3 form-check">
							<input type="checkbox" class="form-check-input" name="${securityConfig.rememberMe.parameter}" id="remember_me" checked="checked"/>
							<label class="form-check-label" for="remember_me"><g:message code='spring.security.ui.login.rememberme'/></label>
						</div>
						<button type="submit" class="btn btn-primary w-100"><g:message code='spring.security.ui.login.login'/></button>
					</form>
					<div id="loginMessage" class="mt-3"></div>
					<div class="d-flex justify-content-between mt-3 small">
						<g:link controller='register' action='forgotPassword'><g:message code='spring.security.ui.login.forgotPassword'/></g:link>
						<g:link controller='register'><g:message code='spring.security.ui.login.register'/></g:link>
					</div>
				</div>
			</div>
		</div>
	</div>
	<asset:script>
var loggingYouIn = "<g:message code='spring.security.ui.login.loggingYouIn'/>";
	</asset:script>
</sec:ifNotLoggedIn>
