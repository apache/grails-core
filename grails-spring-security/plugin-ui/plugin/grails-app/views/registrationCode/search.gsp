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
	<s2ui:title messageCode='spring.security.ui.registrationCode.search'/>
</head>
<body>
<div>
	<s2ui:formContainer type='search' beanType='registrationCode' height='350'>
		<s2ui:searchForm colspan='2'>
			<tr>
				<td><g:message code='registrationCode.username.label' default='Username'/>:</td>
				<td><g:textField name='username' size='50' maxlength='255' autocomplete='off' value='${username}'/></td>
			</tr>
			<tr>
				<td><g:message code='registrationCode.token.label' default='Token'/>:</td>
				<td><g:textField name='token' size='50' maxlength='255' autocomplete='off' value='${token}'/></td>
			</tr>
		</s2ui:searchForm>
	</s2ui:formContainer>
	<g:if test='${searched}'>
	<div class="list">
		<table>
			<thead>
			<tr>
				<s2ui:sortableColumn property='token' titleDefault='Token'/>
				<s2ui:sortableColumn property='username' titleDefault='Username'/>
				<s2ui:sortableColumn property='dateCreated' titleDefault='Date Created'/>
			</tr>
			</thead>
			<tbody>
			<g:each in='${results}' status='i' var='registrationCode'>
				<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
					<td><g:link action='edit' id='${registrationCode.id}'>${uiPropertiesStrategy.getProperty(registrationCode, 'token')}</g:link></td>
					<g:set var='regCodeUsername' value='${uiPropertiesStrategy.getProperty(registrationCode, 'username')}'/>
					<td><g:link controller='user' action='edit' params='[username: regCodeUsername]'>${regCodeUsername}</g:link></td>
					<td><g:formatDate date='${uiPropertiesStrategy.getProperty(registrationCode, 'dateCreated')}' formatName='spring.security.ui.dateFormatGsp'/></td>
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
</body>
</html>
