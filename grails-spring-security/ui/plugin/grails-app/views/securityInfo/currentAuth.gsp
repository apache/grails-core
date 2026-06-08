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
	<title><g:message code='spring.security.ui.menu.securityInfo.currentAuth'/></title>
</head>
<body>
<s2ui:securityInfoTable type='currentAuth' headerCodes='name,value'>
	<tr class='even'>
		<td><g:message code='spring.security.ui.info.currentAuth.label.authorities'/></td>
		<td>${auth.authorities}</td>
	</tr>
	<tr class='odd'>
		<td><g:message code='spring.security.ui.info.currentAuth.label.details'/></td>
		<td>${auth.details}</td>
	</tr>
	<tr class='even'>
		<td><g:message code='spring.security.ui.info.currentAuth.label.principal'/></td>
		<td>${auth.principal}</td>
	</tr>
	<tr class='odd'>
		<td><g:message code='spring.security.ui.info.currentAuth.label.name'/></td>
		<td>${auth.name}</td>
	</tr>
</s2ui:securityInfoTable>
</body>
</html>
