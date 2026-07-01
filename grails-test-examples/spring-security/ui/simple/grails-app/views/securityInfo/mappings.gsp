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
	<title><g:message code='spring.security.ui.menu.securityInfo.mappings'/></title>
</head>
<body>
<p/>
<g:message code='spring.security.ui.info.mappings.type' args='[securityConfigType]'/>
<s2ui:securityInfoTable type='mappings' items='${configAttributes}' headerCodes='pattern,attribute,method'>
	<g:set var='closure' value='${it.configAttributes.any { ca -> ca.getClass().name.contains("ClosureConfigAttribute") }}'/>
	<td>${it.pattern}</td>
	<td>${closure ? '&lt;closure&gt;' : it.configAttributes.toString()[1..-2]}</td>
	<td>${it.httpMethod ?: 'all'}</td>
</s2ui:securityInfoTable>
</body>
</html>
