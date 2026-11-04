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

package hierarchycli

import groovy.transform.CompileStatic

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.springframework.context.ConfigurableApplicationContext

import grails.boot.GrailsAppBuilder
import grails.boot.config.GrailsAutoConfiguration
import hierarchycli.command.ReportingCommand

/**
 * A command-line Grails application. It serves no web requests: the Grails application, with its
 * service and GORM, is the parent of a hierarchy, and the command that does the work is a plain Spring
 * child beneath it. The arguments name the visitors to register; the child reports them and decides
 * the exit code, as any Spring Boot command-line application does.
 */
@CompileStatic
class Application extends GrailsAutoConfiguration {

    static void main(String[] args) {
        ConfigurableApplicationContext command = builder().run(args)
        System.exit(SpringApplication.exit(command))
    }

    /**
     * The hierarchy of this program: the Grails application as the parent, without a web server, and
     * the reporting command as its child. The spec runs the same builder, so what it tests is what
     * {@link #main} runs.
     */
    static GrailsAppBuilder builder() {
        new GrailsAppBuilder(Application)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .child(ReportingCommand)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
    }
}
