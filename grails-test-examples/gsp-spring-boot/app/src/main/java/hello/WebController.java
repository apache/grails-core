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

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Controller
public class WebController implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/results").setViewName("results");
    }

    @RequestMapping("/jsp") public String jsp() { return setJsp(true); }
    @RequestMapping("/gsp") public String gsp() { return setJsp(false); }

    private static boolean jsp = false;
    private String setJsp(boolean jsp) {
        this.jsp = jsp;
        return "redirect:/";
    }

    private String formView(Model model) {
        model.addAttribute("viewType", jsp? "JSP":"GSP");
        return String.format("form%s", jsp? ".jsp":"");
    }

    @RequestMapping(value="/", method=RequestMethod.GET)
    public String showForm(Person person, Model model) {
        return formView(model);
    }

    @RequestMapping(value="/", method=RequestMethod.POST)
    public String checkPersonInfo(@Valid Person person, BindingResult result, Model model, HttpSession session) throws Exception {
        if (result.hasErrors()) {
            return formView(model);
        }
        session.setAttribute("person", person);
        return "redirect:results";
    }
}
