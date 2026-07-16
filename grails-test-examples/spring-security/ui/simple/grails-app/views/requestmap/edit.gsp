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
	<s2ui:title messageCode='default.edit.label' entityNameMessageCode='requestmap.label' entityNameDefault='Requestmap'/>
</head>
<body>
<div class="body">
	<s2ui:formContainer type='update' beanType='requestmap' focus='url' height='350'>
		<s2ui:form useToken="true">
			<div class="dialog">
				<br/>
				<table>
					<tbody>
					<s2ui:textFieldRow name='url' size='50' labelCodeDefault='URL'/>
					<s2ui:textFieldRow name='configAttribute' size='50' labelCodeDefault='Config Attribute'/>
					<g:if test='${hasHttpMethod}'>
					<s2ui:selectRow name='httpMethod' noSelection="['': '']" optionKey='${{it}}'
					                labelCodeDefault='HttpMethod' from='${org.springframework.http.HttpMethod.values()}'/>
					</g:if>
					</tbody>
				</table>
			</div>
			<div style='float:left; margin-top: 10px;'>
				<s2ui:submitButton/>
				<g:if test='${requestmap}'>
					<s2ui:deleteButton/>
				</g:if>
			</div>
		</s2ui:form>
	</s2ui:formContainer>
	<g:if test='${requestmap}'>
		<s2ui:deleteButtonForm instanceId='${requestmap.id}' useToken="true"/>
	</g:if>
</div>
</body>
</html>
