/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package functionaltests

import org.springframework.web.multipart.MultipartFile

class UploadController {

    def index() {
        render view:"index"
    }

    def upload() {
        def contents = new String( request.getFile("myFile").bytes, 'UTF-8' )

        render text: "<p>$contents</p>", contentType:"text/html"
    }

    def upload2() {
        def myFile = params.myFile

        assert myFile instanceof MultipartFile

        render text: "<p>ok</p>", contentType:"text/html"
    }
}
