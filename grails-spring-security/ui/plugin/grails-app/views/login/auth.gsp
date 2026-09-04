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
<html>
<head>
	<meta name="layout" content="${layoutUi}"/>
	<s2ui:title messageCode='spring.security.ui.login.title'/>
</head>
<body>
<div class="row justify-content-center">
	<div class="col-11 col-sm-9 col-md-7 col-lg-5 col-xl-4">
		<div class="card shadow-sm">
			<div class="card-body p-4 p-sm-5">
				<h1 class="h4 card-title text-center mb-4"><g:message code='spring.security.ui.login.signin'/></h1>
				<s2ui:form type='login' focus='username'>
					<div class="mb-3">
						<label class="form-label" for="username"><g:message code='spring.security.ui.login.username'/></label>
						<input type="text" class="form-control" name="${securityConfig.apf.usernameParameter}" id="username"
						       autocapitalize="none" autocomplete="username" autofocus required/>
					</div>
					<div class="mb-3">
						<label class="form-label" for="password"><g:message code='spring.security.ui.login.password'/></label>
						<div class="input-group">
							<input type="password" class="form-control" name="${securityConfig.apf.passwordParameter}" id="password"
							       autocomplete="current-password"/>
							<button class="btn btn-outline-secondary" type="button" aria-pressed="false"
							        onclick="s2uiTogglePassword(this, 'password')"
							        aria-label="${message(code: 'springSecurity.login.password.toggle', default: 'Show or hide password')}">
								<i class="bi bi-eye" aria-hidden="true"></i>
							</button>
						</div>
					</div>
					<div class="mb-3 form-check">
						<input type="checkbox" class="form-check-input" name="${securityConfig.rememberMe.parameter}" id="remember_me" checked="checked"/>
						<label class="form-check-label" for="remember_me"><g:message code='spring.security.ui.login.rememberme'/></label>
					</div>
					<div class="d-grid gap-2">
						<s2ui:submitButton elementId='loginButton' messageCode='spring.security.ui.login.login'/>
						<s2ui:linkButton elementId='register' controller='register' messageCode='spring.security.ui.login.register'/>
					</div>
					<div class="text-center mt-3 small">
						<g:link controller='register' action='forgotPassword'><g:message code='spring.security.ui.login.forgotPassword'/></g:link>
					</div>
				</s2ui:form>
			</div>
		</div>
	</div>
</div>
</body>
</html>
