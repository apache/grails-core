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
<!DOCTYPE html>
<html>
<head>
    <title>File upload</title>
</head>
<body>
    ${flash.message}
    <form id="myForm" action="/upload/upload" method="POST" enctype="multipart/form-data">
        <input type="file" name="myFile" />
        <input id="input1" type="submit" name="submit"/>
    </form>

    <form id="myForm2" action="/upload/upload2" method="POST" enctype="multipart/form-data">
        <input type="file" name="myFile" />
        <input id="input2" type="submit" name="submit"/>
    </form>
</body>
</html>
