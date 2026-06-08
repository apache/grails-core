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
		<title>Show TestRequestmap</title>
	</head>
	<body>
		<div class="nav" role="navigation">
			<ul>
				<li><a class="home" href="${createLink(uri: '/')}">Home</a></li>
				<li><g:link class="list">TestRequestmap List</g:link></li>
				<li><g:link class="create" action="create">New TestRequestmap</g:link></li>
			</ul>
		</div>
		<div id="show-testRequestmap" class="content scaffold-show" role="main">
			<h1>Show TestRequestmap</h1>
			<g:if test="${flash.message}">
			<div class="message" role="status">${flash.message}</div>
			</g:if>
			<ol class="property-list testRequestmap">
				<g:if test="${testRequestmap?.url}">
				<li class="fieldcontain">
					<span id="url-label" class="property-label">URL</span>
					<span class="property-value" aria-labelledby="url-label"><g:fieldValue bean="${testRequestmap}" field="url"/></span>
				</li>
				</g:if>
				<g:if test="${testRequestmap?.configAttribute}">
				<li class="fieldcontain">
					<span id="configAttribute-label" class="property-label">Config Attribute</span>
					<span class="property-value" aria-labelledby="configAttribute-label"><g:fieldValue bean="${testRequestmap}" field="configAttribute"/></span>
				</li>
				</g:if>
				<g:if test="${testRequestmap?.httpMethod}">
				<li class="fieldcontain">
					<span id="httpMethod-label" class="property-label">HTTP Method</span>
					<span class="property-value" aria-labelledby="httpMethod-label"><g:fieldValue bean="${testRequestmap}" field="httpMethod"/></span>
				</li>
				</g:if>
			</ol>
			<g:form url="[resource:testRequestmap, action:'delete']" method="DELETE">
				<fieldset class="buttons">
					<g:link class="edit" action="edit" resource="${testRequestmap}">Edit</g:link>
					<g:actionSubmit class="delete" action="delete" value='Delete' onclick="return confirm('Are you sure?');" />
				</fieldset>
			</g:form>
		</div>
	</body>
</html>
