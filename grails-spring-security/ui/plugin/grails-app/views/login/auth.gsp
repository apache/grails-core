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
	<asset:stylesheet src='spring-security-ui-auth.css'/>
</head>
<body>
<p/>
<div class="login s2ui_center ui-corner-all" style='text-align:center;'>
	<div class="login-inner">
		<s2ui:form type='login' focus='username'>
			<div class="sign-in">
				<h2><g:message code='spring.security.ui.login.signin'/></h2>
				<table>
					<tr>
						<td><label for="username"><g:message code='spring.security.ui.login.username'/></label></td>
						<td><input type="text" name="${securityConfig.apf.usernameParameter}" id="username" class='formLogin' size="20" autocapitalize="off" autofocus required/></td>
					</tr>
					<tr>
						<td><label for="password"><g:message code='spring.security.ui.login.password'/></label></td>
						<td><input type="password" name="${securityConfig.apf.passwordParameter}" id="password" class="formLogin" size="20"/></td>
					</tr>
					<tr>
						<td colspan='2'>
							<input type="checkbox" class="checkbox" name="${securityConfig.rememberMe.parameter}" id="remember_me" checked="checked"/>
							<label for='remember_me'><g:message code='spring.security.ui.login.rememberme'/></label> |
							<span class="forgot-link">
								<g:link controller='register' action='forgotPassword'><g:message code='spring.security.ui.login.forgotPassword'/></g:link>
							</span>
						</td>
					</tr>
					<tr>
						<td colspan='2'>
							<s2ui:linkButton elementId='register' controller='register' messageCode='spring.security.ui.login.register'/>
							<s2ui:submitButton elementId='loginButton' messageCode='spring.security.ui.login.login'/>
						</td>
					</tr>
				</table>
			</div>
		</s2ui:form>
	</div>
</div>
</body>
</html>
