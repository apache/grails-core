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
	<title>Edit TestUser</title>
</head>

<body>

<div class="nav">
	<span class="menuButton"><a class="home" href="${createLink(uri: '/')}">Home</a></span>
	<span class="menuButton"><g:link class="list">TestUser List</g:link></span>
	<span class="menuButton"><g:link class="create" action="create">New TestUser</g:link></span>
</div>

<div class="body">

	<h1>Edit TestUser</h1>
	<g:if test="${flash.message}">
	<div class="message">${flash.message}</div>
	</g:if>

	<g:hasErrors bean="${person}">
	<div class="errors">
	<g:renderErrors bean="${person}" as="list" />
	</div>
	</g:hasErrors>

	<g:form>
		<g:hiddenField name="id" value="${person?.id}" />
		<g:hiddenField name="version" value="${person?.version}" />
		<div class="dialog">
		<table>
		<tbody>

			<tr class="prop">
				<td valign="top" class="name">
					<label for="username">Username</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: person, field: 'username', 'errors')}">
					<g:textField name="username" value="${person?.username}" />
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name">
					<label for="password">Password</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: person, field: 'password', 'errors')}">
					<g:passwordField name="password" value="${person?.password}" />
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name">
					<label for="enabled">Enabled</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: person, field: 'enabled', 'errors')}">
					<g:checkBox name="enabled" value="${person?.enabled}" />
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="authorities">Roles:</label></td>
				<td valign="top" class="value ${hasErrors(bean:person,field:'authorities','errors')}">
					<ul>
    					<g:each var="entry" in="${roleMap}">
						<li>${entry.key.authority.encodeAsHTML()}
							<g:checkBox name="${entry.key.authority}" value="${entry.value}"/>
						</li>
	    				</g:each>
					</ul>
				</td>
			</tr>

		</tbody>
		</table>
		</div>

		<div class="buttons">
			<span class="button"><g:actionSubmit class="save" action="update" value='Update' /></span>
			<span class="button"><g:actionSubmit class="delete" action="delete" value='Delete' onclick="return confirm('Are you sure?');" /></span>
		</div>
	</g:form>
</div>
</body>
</html>
