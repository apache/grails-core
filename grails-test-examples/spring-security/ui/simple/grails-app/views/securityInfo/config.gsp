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
	<title><g:message code='spring.security.ui.menu.securityInfo.config'/></title>
	<s2ui:stylesheet src='jquery.dataTables.css'/>
</head>
<body>
<div id="configHolder">
	<table id="config" cellpadding="0" cellspacing="0" border="0" class="display">
		<caption><g:message code='spring.security.ui.menu.securityInfo.config'/></caption>
		<thead>
		<tr>
			<th><g:message code='spring.security.ui.info.config.header.name'/></th>
			<th><g:message code='spring.security.ui.info.config.header.value'/></th>
		</tr>
		</thead>
		<tbody>
		<g:each var='entry' in='${conf}'>
<%
def key = entry.key
if (key.startsWith('failureHandler.exceptionMappings.')) {
	key = key - 'failureHandler.exceptionMappings.'
	key = 'failureHandler.exceptionMappings. ' + key.replaceAll('\\.', '\\. ')
}
def value = entry.value
if (value instanceof Class) {
	value = value.name.replaceAll('\\.', '\\. ')
}
%>
			<tr>
				<td>${key}</td>
				<td>${value}</td>
			</tr>
		</g:each>
		</tbody>
	</table>
</div>
<s2ui:deferredScript src='webjars/datatables/1.10.25/js/jquery.dataTables.js'/>
<s2ui:documentReady>
$('#config').DataTable();
</s2ui:documentReady>
</body>
</html>
