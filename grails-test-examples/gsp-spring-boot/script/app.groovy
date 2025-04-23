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

package demo

@GrabResolver(name='grails-repo', root='https://repo.grails.org/grails/core/')
@Grab("org.grails:grails-gsp-spring-boot:7.0.0-SNAPSHOT")
// if you need to clear snapshots, they are saved to ~/.groovy/grapes

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.SpringApplication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Bean
import grails.gsp.boot.GspAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration

import grails.gsp.TagLib
import org.springframework.web.servlet.ModelAndView
import java.text.SimpleDateFormat

@RestController
class GspController {
    @RequestMapping("/")
    ModelAndView home() {
        new ModelAndView('index', 'name', 'world')
    }
}

@Component
@TagLib
class FormatTagLib {
	def dateFormat = { attrs, body ->
		out << new SimpleDateFormat(attrs.format).format(attrs.date)
	}
}

@SpringBootApplication
@ImportAutoConfiguration(GspAutoConfiguration.class)
class Application {
    @Bean GspController rspController() { new GspController() }
    @Bean FormatTagLib formatTagLib() { new FormatTagLib() }
}

System.setProperty("org.springframework.boot.logging.LoggingSystem", "none")

SpringApplication.run(Application)