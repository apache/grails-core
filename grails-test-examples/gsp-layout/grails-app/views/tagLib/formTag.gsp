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
    <title>Form Tag Test</title>
</head>
<body>
    <h1>Form Tag Test</h1>
    <g:form controller="tagLib" action="formTag" method="POST" name="test-form">
        <div class="form-group">
            <label for="username">Username:</label>
            <g:textField name="username" value="${username}" id="username-input"/>
        </div>
        <div class="form-group">
            <label for="email">Email:</label>
            <g:textField name="email" value="${email}" id="email-input"/>
        </div>
        <div class="form-group">
            <label for="password">Password:</label>
            <g:passwordField name="password" id="password-input"/>
        </div>
        <div class="form-group">
            <label for="remember">Remember me:</label>
            <g:checkBox name="remember" id="remember-checkbox"/>
        </div>
        <div class="form-group">
            <label for="comments">Comments:</label>
            <g:textArea name="comments" rows="3" cols="40" id="comments-textarea"/>
        </div>
        <g:submitButton name="submit" value="Submit" id="submit-button"/>
    </g:form>
</body>
</html>
