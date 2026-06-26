<%--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<div id="if-gt-5"><g:if test="${value > 5}">Greater than 5</g:if></div>
<div id="if-lt-5"><g:if test="${value < 5}">Less than 5</g:if></div>
<div id="if-eq-5"><g:if test="${value == 5}">Equal to 5</g:if></div>
<div id="else-result"><g:if test="${value > 100}">Over 100</g:if><g:else>Under or equal to 100</g:else></div>
<div id="elseif-result"><g:if test="${value > 50}">Over 50</g:if><g:elseif test="${value > 20}">Over 20</g:elseif><g:else>20 or less</g:else></div>
