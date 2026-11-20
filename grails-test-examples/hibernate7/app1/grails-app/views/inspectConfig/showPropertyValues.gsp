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
<html>
<body>
    <h1> Nested </h1>
    <div>${prop1}</div>
    <div>${prop2}</div>
    <div>${prop3}</div>
    <h1> Flat </h1>
    <div>${prop1Flat}</div>
    <div>${prop2Flat}</div>
    <div>${prop3Flat}</div>
    <h1> Environment </h1>
    <div>${env.getProperty('grails11951.prop1')}</div>
    <div>${env.getProperty('grails11951.prop2')}</div>
    <div>${env.getProperty('grails11951.prop3')}</div>

</body>
</html>
