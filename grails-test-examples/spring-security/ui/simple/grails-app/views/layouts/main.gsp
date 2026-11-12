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
<!doctype html>
<html lang="en">
	<head>
		<meta charset="utf-8">
		<meta name="viewport" content="width=device-width, initial-scale=1">
		<title><g:layoutTitle default="Grails"/></title>
		<link rel="shortcut icon" href="${assetPath(src: 'favicon.ico')}" type="image/x-icon">
		<asset:stylesheet src='application'/>
		<g:layoutHead/>
	</head>
	<body>
	<nav class="navbar navbar-expand-lg bg-body-tertiary border-bottom">
		<div class="container-lg">
			<a class="navbar-brand" href="${request.contextPath}/">Home</a>
			<button class="navbar-toggler" type="button" data-bs-toggle="collapse"
					data-bs-target="#mainNav" aria-controls="mainNav" aria-expanded="false" aria-label="Toggle navigation">
				<span class="navbar-toggler-icon"></span>
			</button>
			<div class="collapse navbar-collapse" id="mainNav">
				<ul class="navbar-nav me-auto">
					<%-- Navbar items contributed by the rendered page (e.g. the
					     spring-security-ui screens) through a <content tag="nav"> block. --%>
					<g:pageProperty name="page.nav"/>
				</ul>
				<ul class="navbar-nav ms-auto">
					<g:pageProperty name="page.navActions"/>
				</ul>
			</div>
		</div>
	</nav>
	<div class="container-lg py-4">
		<g:flashMessages/>
		<g:layoutBody/>
	</div>
	<asset:javascript src='application'/>
	</body>
</html>
