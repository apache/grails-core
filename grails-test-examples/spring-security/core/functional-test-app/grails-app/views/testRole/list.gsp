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
		<title>TestRole List</title>
	</head>
	<body>
		<div class="nav" role="navigation">
			<ul>
				<li><a class="home" href="${createLink(uri: '/')}">Home</a></li>
				<li><g:link class="create" action="create">New TestRole</g:link></li>
			</ul>
		</div>
		<div id="list-testRole" class="content scaffold-list" role="main">
			<h1>TestRole List</h1>
			<g:if test="${flash.message}">
			<div class="message" role="status">${flash.message}</div>
			</g:if>
			<table>
				<thead>
					<tr>
						<g:sortableColumn property="authority" title='Authority' />
					</tr>
				</thead>
				<tbody>
    				<g:each in="${testRoles}" status="i" var="testRole">
					<tr class="${(i % 2) == 0 ? 'even' : 'odd'}">
						<td><g:link action="show" id="${testRole.id}">${fieldValue(bean: testRole, field: "authority")}</g:link></td>
					</tr>
	    			</g:each>
				</tbody>
			</table>
			<div class="pagination">
				<g:paginate total="${testRoleCount ?: 0}" />
			</div>
		</div>
	</body>
</html>
