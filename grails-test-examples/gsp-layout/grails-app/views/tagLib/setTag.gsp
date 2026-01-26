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
<!DOCTYPE html>
<html>
<head>
    <title>Set Tag Test</title>
</head>
<body>
    <h1>Set Tag Test</h1>
    <g:set var="localVar" value="Hello from g:set"/>
    <p id="set-value">${localVar}</p>
    
    <g:set var="computed" value="${2 + 3}"/>
    <p id="computed-value">Computed: ${computed}</p>
    
    <g:set var="listVar" value="${['A', 'B', 'C']}"/>
    <p id="list-value">List size: ${listVar.size()}</p>
</body>
</html>
