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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Controller
public class WebController implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/results").setViewName("results");
    }

    /**
     * Tells every view what rendered it and whether the JSP rendering can be offered - the results
     * view included, which is rendered by the view controller above rather than by a handler method
     * of this class.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                    ModelAndView modelAndView) {
                // not onto a redirect, which would carry the attributes over as query parameters
                if (modelAndView != null && !isRedirect(modelAndView)) {
                    modelAndView.getModel().putIfAbsent("jspAvailable",
                            JspSupport.canServeJsp(request.getServletContext()));
                    modelAndView.getModel().putIfAbsent("viewType", viewType(modelAndView));
                }
            }

            /** What renders this page, which the view name says: only a JSP is asked for by file. */
            private String viewType(ModelAndView modelAndView) {
                String viewName = modelAndView.getViewName();
                return viewName != null && viewName.endsWith(".jsp") ? "JSP" : "GSP";
            }

            private boolean isRedirect(ModelAndView modelAndView) {
                String viewName = modelAndView.getViewName();
                return viewName != null && viewName.startsWith("redirect:");
            }
        });
    }

    @RequestMapping("/gsp") public String gsp() {
        selectJsp(false);
        return "redirect:/";
    }

    private static boolean jsp = false;

    /** Called by {@link JspViewController}, which is mapped only where a JSP can be served. */
    static void selectJsp(boolean selected) {
        jsp = selected;
    }

    private String formView() {
        return String.format("form%s", jsp ? ".jsp" : "");
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String showForm(Person person) {
        return formView();
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public String checkPersonInfo(@Valid Person person, BindingResult result, HttpSession session) throws Exception {
        if (result.hasErrors()) {
            return formView();
        }
        session.setAttribute("person", person);
        return "redirect:results";
    }
}
