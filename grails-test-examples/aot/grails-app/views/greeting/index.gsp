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
<%--
  Every line here is one of the things that only fails once a page is rendered.

  The page itself is found through the manifest of pages compiled at build time, which an image
  reads because it cannot compile one. The tag reaches a collaborator wired by name. The link is
  built by the link generator, whose own collaborator is injected into a field of an implementation
  the container only knows as an interface. The model value proves the action ran.
--%>
<!doctype html>
<html>
<head><title>ahead of time</title></head>
<body>
<p id="greeting"><aot:greeting/></p>
<p id="model">${name}</p>
<p id="link"><g:createLink controller="greeting" action="index"/></p>
</body>
</html>
