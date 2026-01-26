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
    <title>Link Tag Test</title>
</head>
<body>
    <h1>Link Tag Test</h1>
    <span id="index-link"><g:link controller="tagLib" action="index">Home Link</g:link></span>
    <g:link controller="tagLib" action="eachTag" class="styled-link" elementId="each-link">Each Tag Link</g:link>
    <g:link controller="tagLib" action="ifTag" params="[show: 'true']" elementId="param-link">With Params</g:link>
</body>
</html>
