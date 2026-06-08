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
	<s2ui:title messageCode='spring.security.ui.persistentLogin.search'/>
</head>
<body>
<div>
	<s2ui:formContainer type='search' beanType='persistentLogin' height='350'>
		<s2ui:searchForm colspan='2'>
			<tr>
				<td><g:message code='persistentLogin.username.label' default='Username'/>:</td>
				<td><g:textField name='username' size='50' maxlength='255' autocomplete='off' value='${username}'/></td>
			</tr>
			<tr>
				<td><g:message code='persistentLogin.token.label' default='Token'/>:</td>
				<td><g:textField name='token' size='50' maxlength='255' autocomplete='off' value='${token}'/></td>
			</tr>
			<tr>
				<td><g:message code='persistentLogin.series.label' default='Series'/>:</td>
				<td><g:textField name='series' size='50' maxlength='255' autocomplete='off' value='${series}'/></td>
			</tr>
		</s2ui:searchForm>
	</s2ui:formContainer>
	<g:if test='${searched}'>
	<div class="list">
		<table>
			<thead>
			<tr>
				<s2ui:sortableColumn property='series' titleDefault='Series'/>
				<s2ui:sortableColumn property='username' titleDefault='Username'/>
				<s2ui:sortableColumn property='token' titleDefault='Token'/>
				<s2ui:sortableColumn property='lastUsed' titleDefault='Last Used'/>
			</tr>
			</thead>
			<tbody>
			<g:each in='${results}' status='i' var='persistentLogin'>
				<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
					<td><g:link action='edit' id='${persistentLogin.series}'>${persistentLogin.series}</g:link></td>
					<g:set var='persistentLoginUsername' value='${uiPropertiesStrategy.getProperty(persistentLogin, 'username')}'/>
					<td><g:link controller='user' action='edit' params='[username: persistentLoginUsername]'>${persistentLoginUsername}</g:link></td>
					<td>${uiPropertiesStrategy.getProperty(persistentLogin, 'token')}</td>
					<td><g:formatDate date='${uiPropertiesStrategy.getProperty(persistentLogin, 'lastUsed')}' formatName='spring.security.ui.dateFormatGsp'/></td>
				</tr>
			</g:each>
			</tbody>
		</table>
	</div>
	<s2ui:paginate total='${totalCount}'/>
	</g:if>
</div>
<s2ui:ajaxSearch paramName='username'/>
<s2ui:ajaxSearch paramName='token' focus='false'/>
<s2ui:ajaxSearch paramName='series' focus='false'/>
</body>
</html>
