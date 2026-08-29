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
<%@ page model="String title; java.util.List<gspstatic.Book> books" %>
<!doctype html>
<html>
<head><title>${title}</title></head>
<body>
<h1>${title}</h1>
<ul>
<g:each in="${books.findAll { it.pages > 0 }}" var="book" status="i">
    <li id="book-${i}">${book.title} has ${book.pages} pages</li>
</g:each>
</ul>
<p id="count">${books.size()}</p>
<p id="longest">${books.max { it.pages }.title}</p>
<p id="shout"><demo:shout text="quiet"/></p>
</body>
</html>
