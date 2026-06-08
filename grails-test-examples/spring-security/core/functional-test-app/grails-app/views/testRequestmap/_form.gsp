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
<div class="fieldcontain ${hasErrors(bean: testRequestmap, field: 'url', 'error')} required">
	<label for="url">Url <span class="required-indicator">*</span></label>
	<g:textField name="url" required="" value="${testRequestmap?.url}"/>
</div>

<div class="fieldcontain ${hasErrors(bean: testRequestmap, field: 'configAttribute', 'error')} required">
	<label for="configAttribute">Config Attribute <span class="required-indicator">*</span></label>
	<g:textField name="configAttribute" required="" value="${testRequestmap?.configAttribute}"/>
</div>

<div class="fieldcontain ${hasErrors(bean: testRequestmap, field: 'httpMethod', 'error')} ">
	<label for="httpMethod">HTTP Method</label>
	<g:select name="httpMethod" from="${org.springframework.http.HttpMethod.values()}"
	          keys="${org.springframework.http.HttpMethod.values()*.name()}"
	          value="${testRequestmap?.httpMethod?.name()}" noSelection="['': '']"/>
</div>
