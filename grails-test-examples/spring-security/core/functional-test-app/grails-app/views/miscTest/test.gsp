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
<body>

	<sec:ifAllGranted roles='ROLE_USER,ROLE_ADMIN'>user and admin</sec:ifAllGranted><br/>
	<sec:ifAllGranted roles='ROLE_USER,ROLE_ADMIN,ROLE_FOO'>user and admin and foo</sec:ifAllGranted><br/>

	<sec:ifNotGranted roles='ROLE_USER,ROLE_ADMIN'>not user and not admin</sec:ifNotGranted><br/>

	<sec:ifAnyGranted roles='ROLE_USER,ROLE_ADMIN'>user or admin</sec:ifAnyGranted><br/>

	accountNonExpired: "<sec:loggedInUserInfo field='accountNonExpired'>not logged in</sec:loggedInUserInfo>"<br/>
	id: "<sec:loggedInUserInfo field='id'>not logged in</sec:loggedInUserInfo>"<br/>

	Username is "<sec:username/>"<br/>

	<sec:ifLoggedIn>logged in true</sec:ifLoggedIn><br/>
	<sec:ifNotLoggedIn>logged in false</sec:ifNotLoggedIn><br/>

	<sec:ifSwitched>switched true<br />
		<form id="exitUserForm" action='${request.contextPath}/logout/impersonate' method='POST'>
			<input id="exitUserFormSubmitButton" type='submit' value="Resume as original user"/>
		</form>
	</sec:ifSwitched><br/>
	<sec:ifNotSwitched>switched false <br />
		<form class="switchUserForm" action='${request.contextPath}/login/impersonate' method='POST'>
			Switch to user: <input type='text' id="username" name='username'/><br/>
			<input id="switchUserFormSubmitButton" type='submit' value='Switch'/>
		</form>
	</sec:ifNotSwitched><br/>
	switched original username "<sec:switchedUserOriginalUsername/>"<br/>

	<sec:access   expression="hasRole('ROLE_USER')">access with role user: true</sec:access><br/>
	<sec:noAccess expression="hasRole('ROLE_USER')">access with role user: false</sec:noAccess><br/>

	<sec:access   expression="hasRole('ROLE_ADMIN')">access with role admin: true</sec:access><br/>
	<sec:noAccess expression="hasRole('ROLE_ADMIN')">access with role admin: false</sec:noAccess><br/>

	<sec:access   expression="hasRole('ROLE_ANONYMOUS')">anonymous access: true</sec:access><br/>
	<sec:noAccess expression="hasRole('ROLE_ANONYMOUS')">anonymous access: false</sec:noAccess><br/>

	<sec:access   url='/miscTest/test'>Can access /miscTest/test</sec:access><br/>
	<sec:noAccess url='/miscTest/test'>Cannot access /miscTest/test</sec:noAccess><br/>
	<sec:access   url='/misc-test/test'>Can access /misc-test/test</sec:access><br/>
	<sec:noAccess url='/misc-test/test'>Cannot access /misc-test/test</sec:noAccess><br/>

	<sec:access   url='/login/auth'>Can access /login/auth</sec:access><br/>
	<sec:noAccess url='/login/auth'>Cannot access /login/auth</sec:noAccess><br/>

	<sec:access   url='/secureAnnotated'>Can access /secureAnnotated</sec:access><br/>
	<sec:noAccess url='/secureAnnotated'>Cannot access /secureAnnotated</sec:noAccess><br/>
	<sec:access   url='/secure-annotated'>Can access /secure-annotated</sec:access><br/>
	<sec:noAccess url='/secure-annotated'>Cannot access /secure-annotated</sec:noAccess><br/>

</body>
</html>
