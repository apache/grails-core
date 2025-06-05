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
		<title>Show TestRole</title>
	</head>
	<body>
		<div class="nav" role="navigation">
			<ul>
				<li><a class="home" href="${createLink(uri: '/')}">Home</a></li>
				<li><g:link class="list">TestRole List</g:link></li>
				<li><g:link class="create" action="create">New TestRole</g:link></li>
			</ul>
		</div>
		<div id="show-testRole" class="content scaffold-show" role="main">
			<h1>Show TestRole</h1>
			<g:if test="${flash.message}">
			<div class="message" role="status">${flash.message}</div>
			</g:if>
			<ol class="property-list testRole">
				<g:if test="${testRole?.authority}">
				<li class="fieldcontain">
					<span id="authority-label" class="property-label">Authority</span>
					<span class="property-value" aria-labelledby="authority-label"><g:fieldValue bean="${testRole}" field="authority"/></span>
				</li>
				</g:if>
			</ol>
			<g:form url="[resource:testRole, action:'delete']" method="DELETE">
				<fieldset class="buttons">
					<g:link class="edit" action="edit" resource="${testRole}">Edit</g:link>
					<g:actionSubmit class="delete" action="delete" value='Delete' onclick="return confirm('Are you sure?');" />
				</fieldset>
			</g:form>
		</div>
	</body>
</html>
