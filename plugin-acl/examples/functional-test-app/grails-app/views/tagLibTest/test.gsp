<%@ page import="org.springframework.security.acls.domain.BasePermission" %>
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

<g:each in='${reportIdsAndNumbers}' var='entry'>

<%-- single String --%>
	<sec:permitted className='com.testacl.Report' id='${entry.id}' permission='read'>
		test 1 true ${entry.number}<br/>
	</sec:permitted>
	<sec:notPermitted className='com.testacl.Report' id='${entry.id}' permission='read'>
		test 1 false ${entry.number}<br/>
	</sec:notPermitted>

<%-- multiple String --%>
	<sec:permitted className='com.testacl.Report' id='${entry.id}' permission='write,read'>
		test 2 true ${entry.number}<br/>
	</sec:permitted>
	<sec:notPermitted className='com.testacl.Report' id='${entry.id}' permission='read,write'>
		test 2 false ${entry.number}<br/>
	</sec:notPermitted>

<%-- single Permission --%>
	<sec:permitted className='com.testacl.Report' id='${entry.id}' permission='${BasePermission.READ}'>
		test 3 true ${entry.number}<br/>
	</sec:permitted>
	<sec:notPermitted className='com.testacl.Report' id='${entry.id}' permission='${BasePermission.READ}'>
		test 3 false ${entry.number}<br/>
	</sec:notPermitted>

<%-- List of Permission --%>
	<sec:permitted className='com.testacl.Report' id='${entry.id}' permission='${[BasePermission.WRITE,BasePermission.READ]}'>
		test 4 true ${entry.number}<br/>
	</sec:permitted>
	<sec:notPermitted className='com.testacl.Report' id='${entry.id}' permission='${[BasePermission.WRITE,BasePermission.READ]}'>
		test 4 false ${entry.number}<br/>
	</sec:notPermitted>

<%-- single mask int --%>
	<sec:permitted className='com.testacl.Report' id='${entry.id}' permission='${1}'>
		test 5 true ${entry.number}<br/>
	</sec:permitted>
	<sec:notPermitted className='com.testacl.Report' id='${entry.id}' permission='${1}'>
		test 5 false ${entry.number}<br/>
	</sec:notPermitted>

<%-- multiple mask int --%>
	<sec:permitted className='com.testacl.Report' id='${entry.id}' permission='2,1'>
		test 6 true ${entry.number}<br/>
	</sec:permitted>
	<sec:notPermitted className='com.testacl.Report' id='${entry.id}' permission='2,1'>
		test 6 false ${entry.number}<br/>
	</sec:notPermitted>

	<br/>

</g:each>

</body>
</html>
