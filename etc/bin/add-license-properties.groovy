#!/usr/bin/env groovy
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
import java.nio.file.*

def ASF_MARKER = "Licensed to the Apache Software Foundation (ASF) under one"
def HEADER = '''# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

'''

def root = Paths.get(".").toRealPath()

Files.walk(root)
        .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".properties") }
        .each { path ->
            def text = path.toFile().getText("UTF-8")
            if (!text.contains(ASF_MARKER)) {
                println "📄 Adding ASF header to ${path}"
                // Prepend header + existing content
                path.toFile().withWriter("UTF-8") { writer ->
                    writer << HEADER
                    writer << text
                }
            }
        }

println "✅ Done."
