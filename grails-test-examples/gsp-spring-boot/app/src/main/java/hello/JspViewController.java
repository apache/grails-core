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
package hello;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Switches the form over to its JSP rendering, where the deployment can serve one. Where it cannot
 * the form stays on GSP: an executable jar offers no link here (see {@code layouts/main.gsp}), and
 * a request typed in by hand returns the page it can render rather than failing to render a JSP.
 */
@Controller
public class JspViewController {

    @RequestMapping("/jsp")
    public String jsp(HttpServletRequest request) {
        WebController.selectJsp(JspSupport.canServeJsp(request.getServletContext()));
        return "redirect:/";
    }
}
