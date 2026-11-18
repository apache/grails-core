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
<html>
<head>
	<meta name="layout" content="${layoutUi}"/>
	<s2ui:title messageCode='spring.security.ui.user.search'/>
</head>
<body>
<s2ui:formContainer type='search' beanType='user' width='40rem'>
	<s2ui:searchForm>
		<div class="mb-3">
			<label class="form-label" for="username"><g:message code='user.username.label' default='Username'/></label>
			<g:textField name='username' class='form-control' maxlength='255' autocomplete='off' value='${username}'/>
		</div>
		<g:each in='${[[name: 'enabled', code: 'user.enabled.label', label: 'Enabled'],
		               [name: 'accountExpired', code: 'user.accountExpired.label', label: 'Account Expired'],
		               [name: 'accountLocked', code: 'user.accountLocked.label', label: 'Account Locked'],
		               [name: 'passwordExpired', code: 'user.passwordExpired.label', label: 'Password Expired']]}' var='entry'>
		<div class="mb-3">
			<span class="form-label d-block"><g:message code='${entry.code}' default='${entry.label}'/></span>
			<g:radioGroup name='${entry.name}'
			              labels='${[message(code: "spring.security.ui.search.true"), message(code: "spring.security.ui.search.false"), message(code: "spring.security.ui.search.either")]}'
			              values='[1,-1,0]' value='${pageScope[entry.name] ?: 0}'>
				<div class="form-check form-check-inline">
					<%= it.radio.toString().replace('<input ', '<input class="form-check-input" ') %>
					<span class="form-check-label">${it.label}</span>
				</div>
			</g:radioGroup>
		</div>
		</g:each>
	</s2ui:searchForm>
</s2ui:formContainer>
<g:if test='${searched}'>
<div class="table-responsive">
	<table class="table table-striped table-hover align-middle">
		<thead>
		<tr>
			<s2ui:sortableColumn property='username' titleDefault='Username'/>
			<s2ui:sortableColumn property='enabled' titleDefault='Enabled'/>
			<s2ui:sortableColumn property='accountExpired' titleDefault='Account Expired'/>
			<s2ui:sortableColumn property='accountLocked' titleDefault='Account Locked'/>
			<s2ui:sortableColumn property='passwordExpired' titleDefault='Password Expired'/>
		</tr>
		</thead>
		<tbody>
		<g:each in='${results}' var='user'>
			<tr>
				<td><g:link action='edit' id='${user.id}'>${uiPropertiesStrategy.getProperty(user, 'username')}</g:link></td>
				<td><s2ui:formatBoolean bean='${user}' name='enabled'/></td>
				<td><s2ui:formatBoolean bean='${user}' name='accountExpired'/></td>
				<td><s2ui:formatBoolean bean='${user}' name='accountLocked'/></td>
				<td><s2ui:formatBoolean bean='${user}' name='passwordExpired'/></td>
			</tr>
		</g:each>
		</tbody>
	</table>
</div>
<s2ui:paginate total='${totalCount}'/>
</g:if>
<s2ui:ajaxSearch paramName='username'/>
</body>
</html>
