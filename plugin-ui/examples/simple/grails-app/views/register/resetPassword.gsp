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
	<meta name="layout" content="${layoutRegister}"/>
	<s2ui:title messageCode='spring.security.ui.resetPassword.title'/>
</head>
<body>
<s2ui:formContainer type='resetPassword' focus='password' width='475px'>
	<s2ui:form beanName='resetPasswordCommand'>
		<g:hiddenField name='t' value='${token}'/>
		<div class="sign_in">
			<br/>
			<h3><g:message code='spring.security.ui.resetPassword.description'/></h3>
			<table>
				<s2ui:passwordFieldRow name='password' labelCodeDefault='Password'/>
				<s2ui:passwordFieldRow name='password2' labelCodeDefault='Password (again)'/>
			</table>
			<s2ui:submitButton elementId='submit' messageCode='spring.security.ui.resetPassword.submit'/>
		</div>
	</s2ui:form>
</s2ui:formContainer>
</body>
</html>
