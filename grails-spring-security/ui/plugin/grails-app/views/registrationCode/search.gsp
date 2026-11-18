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
<s2ui:formContainer type='search' beanType='registrationCode' width='40rem'>
	<s2ui:searchForm>
		<div class="mb-3">
			<label class="form-label" for="username"><g:message code='registrationCode.username.label' default='Username'/></label>
			<g:textField name='username' class='form-control' maxlength='255' autocomplete='off' value='${username}'/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="token"><g:message code='registrationCode.token.label' default='Token'/></label>
			<g:textField name='token' class='form-control' maxlength='255' autocomplete='off' value='${token}'/>
		</div>
	</s2ui:searchForm>
</s2ui:formContainer>
<g:if test='${searched}'>
<div class="table-responsive">
	<table class="table table-striped table-hover align-middle">
		<thead>
		<tr>
			<s2ui:sortableColumn property='token' titleDefault='Token'/>
			<s2ui:sortableColumn property='username' titleDefault='Username'/>
			<s2ui:sortableColumn property='dateCreated' titleDefault='Date Created'/>
		</tr>
		</thead>
		<tbody>
		<g:each in='${results}' var='registrationCode'>
			<tr>
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
<s2ui:ajaxSearch paramName='username'/>
<s2ui:ajaxSearch paramName='token' focus='false'/>
</body>
</html>
