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
<title>Grant Permission</title>
</head>

<body>

<div class="body">

<h1>Grant permission for ${report.name}</h1>

<g:if test="${flash.message}">
<div class="message">${flash.message}</div>
</g:if>

<g:form action='grant'>
	<g:hiddenField name='number' value="${report.number}" />
	<div class="dialog">
	<table>
	<tbody>
		<tr class="prop">
			<td valign="top" class="name">Recipient</td>
			<td valign="top" class="value"><g:textField name='recipient' /></td>
		</tr>
		<tr class="prop">
			<td valign="top" class="name">Permission</td>
			<td valign="top" class="value"><g:textField name='permission' /></td>
		</tr>
	</tbody>
	</table>
	</div>

	<div class="buttons">
	<span class="button"><g:submitButton name='create' class='save' value='Grant' /></span>
	</div>

</g:form>

</div>
</body>
</html>
