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
    <title>Join Tag Test</title>
</head>
<body>
    <h1>Join Tag Test</h1>
    <p id="comma-join">Comma: <g:join in="${items}" delimiter=", "/></p>
    <p id="dash-join">Dash: <g:join in="${items}" delimiter=" - "/></p>
    <p id="pipe-join">Pipe: <g:join in="${items}" delimiter=" | "/></p>
</body>
</html>
