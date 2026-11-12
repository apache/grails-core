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
<%@ page import="hyphenated.TourGuide" %>
<!doctype html>
<html>
<head>
    <title>Guide Links</title>
</head>
<body>
<a id="guideShowLink" href="${createLink(resource: guide, action: 'show')}">Guide</a>
<a id="guideDetailsLink" href="${createLink(resource: guide, action: 'showDetails')}">Guide details</a>
<a id="guideDetailsByClassLink" href="${createLink(resource: TourGuide, action: 'showDetails', id: guide.id)}">Guide details, by class</a>
</body>
</html>
