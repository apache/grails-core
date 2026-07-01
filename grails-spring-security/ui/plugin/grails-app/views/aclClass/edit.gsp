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
	<s2ui:title messageCode='default.edit.label' entityNameMessageCode='aclClass.label' entityNameDefault='AclClass'/>
</head>
<body>
<div class="body">
	<s2ui:formContainer type='update' beanType='aclClass' focus='className' height='300'>
		<s2ui:form useToken="true">
			<div class="dialog">
				<br/>
				<table>
					<tbody>
					<s2ui:textFieldRow name='className' size='60' labelCodeDefault='Class Name'/>
					<tr>
						<td colspan='2'><g:link action='search' controller='aclObjectIdentity' params='["aclClass.id": aclClass.id]'><g:message code='spring.security.ui.aclClass.edit.viewOids'/></g:link></td>
					</tr>
					<tr>
						<td colspan='2'><g:link action='search' controller='aclEntry' params='["aclClass.id": aclClass.id]'><g:message code='spring.security.ui.aclClass.edit.viewEntries'/></g:link></td>
					</tr>
					</tbody>
				</table>
			</div>
			<div style='float:left; margin-top: 10px;'>
				<s2ui:submitButton/>
				<g:if test='${aclClass}'><s2ui:deleteButton/></g:if>
			</div>
		</s2ui:form>
	</s2ui:formContainer>
	<g:if test='${aclClass}'><s2ui:deleteButtonForm instanceId='${aclClass.id}' useToken="true"/></g:if>
</div>
</body>
</html>
