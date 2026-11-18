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

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/admin/$controller/$action?/$id?(.$format)?"{
            namespace = "admin"
        }

        // Exercises URL paths that dispatch to controllers with static namespace declarations. Link
        // generation inference is based on controller namespace metadata; request-time mapping
        // conditions such as headers still require explicit namespace selection when ambiguous.
        "/frontend/$controller/$action?/$id?(.$format)?"{
            namespace = "frontend"
        }

        "/manage/$controller/$action?/$id?(.$format)?"{
            namespace = "manage"
        }

        "/archive/$controller/$action?/$id?(.$format)?"{
            namespace = "archive"
        }

        "/home-submit"(controller: "home", action: "target", method: "POST")
        "/home-list/$page"(controller: "home", action: "list")
        "/home-by/$category"(controller: "home", action: "list", method: "GET")

        "/"(view:"/index")
        "500"(view:'/error')
    }
}
