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
	<s2ui:title messageCode='default.edit.label' entityNameMessageCode='registrationCode.label' entityNameDefault='RegistrationCode'/>
</head>
<body>
<div class="body">
	<s2ui:formContainer type='update' beanType='registrationCode' height='350'>
		<s2ui:form useToken="true">
			<div class="dialog">
				<br/>
				<table>
					<tbody>
					<s2ui:textFieldRow name='username' size='50' labelCodeDefault='Username'/>
					<s2ui:textFieldRow name='token' size='50' labelCodeDefault='Token'/>
					<tr class="prop">
						<td valign="top" class="name">
							<label for="dateCreated">${message(code: 'registrationCode.dateCreated.label', default: 'Date Created')}</label>
						</td>
						<td valign="top" class="value">${formatDate(date: uiPropertiesStrategy.getProperty(registrationCode, 'dateCreated'), formatName: 'spring.security.ui.dateFormatGsp')}</td>
					</tr>
					</tbody>
				</table>
			</div>
			<div style='float:left; margin-top: 10px;'>
				<s2ui:submitButton/>
				<g:if test='${registrationCode}'><s2ui:deleteButton/></g:if>
			</div>
		</s2ui:form>
	</s2ui:formContainer>
	<g:if test='${registrationCode}'><s2ui:deleteButtonForm instanceId='${registrationCode.id}' useToken="true"/></g:if>
</div>
<s2ui:ajaxSearch paramName='username'/>
</body>
</html>
