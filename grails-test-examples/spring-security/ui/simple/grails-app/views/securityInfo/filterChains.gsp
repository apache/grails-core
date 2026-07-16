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
	<title><g:message code='spring.security.ui.menu.securityInfo.filterChains'/></title>
</head>
<body>
<s2ui:securityInfoTable type='filterChains' items='${securityFilterChains}' headerCodes='pattern,filters'>
	<td>${it.matcherPattern}</td>
	<td>
		<g:if test='${it.filters}'>
		<ul>
		<g:each var='filter' in='${it.filters}'>
			<li>${filter.getClass().name}</li>
		</g:each>
		</ul>
		</g:if>
		<g:else>
		<i><g:message code='spring.security.ui.info.filterChains.none'/></i>
		</g:else>
	</td>
</s2ui:securityInfoTable>
</body>
</html>
