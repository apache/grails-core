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
package aot

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

import org.springframework.context.ConfigurableApplicationContext

class Application extends GrailsAutoConfiguration {

    /**
     * With {@code --aot-startup-check} the application starts, asks itself for the one page it has,
     * and exits. The build runs it that way against the packaged jar with
     * {@code spring.aot.enabled=true}.
     *
     * <p>The page is the point. Almost everything an application generated ahead of time gets wrong
     * is something that starts perfectly well: a tag library holding null where a collaborator wired
     * by name should be, a link generator whose own collaborator was injected into a field of an
     * implementation the container only knows as an interface, a page looked up in a manifest an
     * image did not carry. None of it is visible until something renders, so a check that only reads
     * the bean names would pass while every one of those was broken.</p>
     */
    static void main(String[] args) {
        if (!args.contains('--aot-startup-check')) {
            GrailsApp.run(Application, args)
            return
        }

        ConfigurableApplicationContext context = (ConfigurableApplicationContext) GrailsApp.run(Application, args)
        try {
            assertBeanPresent(context, 'grailsApplication')
            assertBeanPresent(context, 'filteringCodecsByContentTypeSettings')
            assertBeanPresent(context, 'groovyPagesServlet')
            assertBeanPresent(context, 'jspViewResolver')
            assertBeanPresent(context, 'grailsUrlMappingsHolder')
            // contributed by a plugin's @Configuration class, which is parsed by the processor the
            // core plugin stands down in favour of once the definitions are generated
            assertBeanPresent(context, 'propertySourcesPlaceholderConfigurer')
            assertPageRenders(context)
        }
        finally {
            context.close()
        }
        System.exit(0)
    }

    private static void assertBeanPresent(ConfigurableApplicationContext context, String name) {
        if (!context.containsBean(name)) {
            throw new IllegalStateException("AOT-processed context is missing the '${name}' bean")
        }
    }

    /** Asks the running application for its page and checks what came back rendered. */
    private static void assertPageRenders(ConfigurableApplicationContext context) {
        Integer port = context.environment.getProperty('local.server.port', Integer)
        if (port == null) {
            throw new IllegalStateException('The application reported no port, so it is not serving')
        }
        String page = get(port, '/greeting/index')
        assertRendered(page, 'hello from a service',
                'the tag library rendered without the collaborator it takes by name')
        assertRendered(page, 'ahead of time', 'the action did not reach the page it returned')

        // Followed rather than matched against a path written here. What the generator produces is
        // whatever the mappings reverse to -- this application maps the action to the root -- so the
        // question worth asking is whether the link it built leads back to the page it was built on.
        String link = between(page, '<p id="link">', '</p>')
        if (!link) {
            throw new IllegalStateException("AOT-processed application: the link generator built no " +
                    "link. Page was:\n${page}")
        }
        assertRendered(get(port, link), 'hello from a service',
                "the link the generator built (${link}) does not lead back to the page")
    }

    private static String get(int port, String path) {
        new URI("http://localhost:${port}${path}").toURL().getText('UTF-8')
    }

    private static String between(String page, String start, String end) {
        int from = page.indexOf(start)
        if (from < 0) {
            return null
        }
        int to = page.indexOf(end, from + start.length())
        to < 0 ? null : page.substring(from + start.length(), to).trim()
    }

    private static void assertRendered(String page, String expected, String whatItMeans) {
        if (!page.contains(expected)) {
            throw new IllegalStateException(
                    "AOT-processed application: ${whatItMeans}. Expected '${expected}' in:\n${page}")
        }
    }
}
