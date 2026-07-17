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
	<s2ui:title messageCode='spring.security.ui.aclSid.search'/>
</head>
<body>
<div>
	<s2ui:formContainer type='search' beanType='aclSid'>
		<s2ui:searchForm colspan='4'>
			<tr>
				<td><g:message code='aclSid.sid.label' default='SID'/>:</td>
				<td colspan='3'><g:textField name='sid' size='50' maxlength='255' autocomplete='off' value='${sid}'/></td>
			</tr>
			<tr>
				<td>&nbsp;</td>
				<td><g:message code='spring.security.ui.search.true'/></td>
				<td><g:message code='spring.security.ui.search.false'/></td>
				<td><g:message code='spring.security.ui.search.either'/></td>
			</tr>
			<tr>
				<td><g:message code='aclSid.principal.label' default='Principal'/>:</td>
				<g:radioGroup name='principal' labels="['','','']" values='[1,-1,0]' value='${principal ?: 0}'>
					<td><%=it.radio%></td>
				</g:radioGroup>
			</tr>
		</s2ui:searchForm>
	</s2ui:formContainer>
	<g:if test='${searched}'>
	<div class="list">
		<table>
			<thead>
			<tr>
				<s2ui:sortableColumn property='sid' titleDefault='SID'/>
				<s2ui:sortableColumn property='principal' titleDefault='Principal'/>
			</tr>
			</thead>
			<tbody>
			<g:each in='${results}' status='i' var='aclSid'>
				<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
					<td><g:link action='edit' id='${aclSid.id}'>${uiPropertiesStrategy.getProperty(aclSid, 'sid')}</g:link></td>
					<td><s2ui:formatBoolean bean='${aclSid}' name='principal'/></td>
				</tr>
			</g:each>
			</tbody>
		</table>
	</div>
	<s2ui:paginate total='${totalCount}'/>
	</g:if>
</div>
<s2ui:ajaxSearch paramName='sid'/>
</body>
</html>
