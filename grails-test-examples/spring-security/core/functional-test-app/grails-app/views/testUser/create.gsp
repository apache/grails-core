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
	<title>Create TestUser</title>
</head>

<body>

<div class="nav">
	<span class="menuButton"><a class="home" href="${createLink(uri: '/')}">Home</a></span>
	<span class="menuButton"><g:link class="list">TestUser List</g:link></span>
</div>

<div class="body">
	<h1>Create TestUser</h1>

	<g:if test="${flash.message}">
	<div class="message">${flash.message}</div>
	</g:if>

	<g:hasErrors bean="${testUser}">
	<div class="errors">
	<g:renderErrors bean="${testUser}" as="list" />
	</div>
	</g:hasErrors>

	<g:form action="save">

	<div class="dialog">
	<table>
	<tbody>

		<tr class="prop">
			<td valign="top" class="name">
				<label for="username">Username</label>
			</td>
			<td valign="top" class="value ${hasErrors(bean: testUser, field: 'username', 'errors')}">
				<g:textField name="username" value="${testUser?.username}" />
			</td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">
				<label for="password">Password</label>
			</td>
			<td valign="top" class="value ${hasErrors(bean: testUser, field: 'password', 'errors')}">
				<g:passwordField name="password" value="${testUser?.password}" />
			</td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">
				<label for="enabled">Enabled</label>
			</td>
			<td valign="top" class="value ${hasErrors(bean: testUser, field: 'enabled', 'errors')}">
				<g:checkBox name="enabled" value="${testUser?.enabled}" />
			</td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name" align="left">Assign Roles:</td>
		</tr>

		<g:each var='auth' in="${authorityList}">
		<tr>
			<td valign="top" class="name" align="left">${auth.authority.encodeAsHTML()}</td>
			<td align="left"><g:checkBox name="${auth.authority}" id="${auth.authority}"/></td>
		</tr>
		</g:each>

	</tbody>
	</table>
	</div>

	<div class="buttons">
		<span class="button"><g:submitButton name="create" class="save" value='Create' /></span>
	</div>
	</g:form>
</div>
</body>
</html>
