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
<head>
    <meta name="layout" content="main"/>
</head>
<body>

First invocation: <cache:render template="counterTemplate" model="[counter: counter]" key="ttl:1" ttl="$ttl"/><br/>
Second invocation: <cache:render template="counterTemplate" model="[counter: counter + 1]" key="ttl:1" ttl="$ttl"/><br/>

Third invocation: <cache:render template="counterTemplate" model="[counter: counter + 2]" key="ttl:2" ttl="$ttl"/><br/>
Fourth invocation: <cache:render template="counterTemplate" model="[counter: counter + 3]" key="ttl:2" ttl="$ttl"/><br/>

Fifth invocation: <cache:render template="counterTemplate" model="[counter: counter + 4]" key="ttl:1" ttl="$ttl"/><br/>

</body>
</html>