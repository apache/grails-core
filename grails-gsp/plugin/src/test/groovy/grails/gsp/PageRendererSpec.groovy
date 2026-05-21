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
package grails.gsp
import grails.core.DefaultGrailsApplication
import grails.spring.BeanBuilder
import org.grails.core.artefact.gsp.TagLibArtefactHandler
import org.grails.core.io.SimpleMapResourceLoader
import org.grails.gsp.GroovyPagesTemplateEngine
import org.grails.plugins.web.taglib.ApplicationTagLib
import org.grails.taglib.TagLibraryLookup
import org.grails.web.gsp.io.CachingGrailsConventionGroovyPageLocator
import org.springframework.core.io.ByteArrayResource
import spock.lang.Specification

class PageRendererSpec extends Specification {

    SimpleMapResourceLoader resourceLoader = new SimpleMapResourceLoader()

    void "Test render simple template"() {
        given:
            resourceLoader.resources.put("/foo/_bar.gsp", new ByteArrayResource("Hello \${person}".bytes))
        when:
            def contents = pageRenderer.render(template:"/foo/bar", model:[person:"John"])
        then:
            contents != null
            contents == "Hello John"
    }

    void "Test render template with tag"() {
        given:
            resourceLoader.resources.put("/foo/_bar.gsp", new ByteArrayResource('Hello <g:join in="[\'john\', \'fred\']" />'.bytes))
        when:
            def contents = pageRenderer.render(template:"/foo/bar", model:[person:"John"])
        then:
            contents != null
            contents == "Hello john, fred"
    }

    void "Test renderTo simple template"() {
        given:
            resourceLoader.resources.put("/foo/_bar.gsp", new ByteArrayResource("Hello \${person}".bytes))
        when:
            def sw = new StringWriter()
            pageRenderer.renderTo(template:"/foo/bar", model:[person:"John"], sw)
            def contents = sw.toString()

        then:
            contents != null
            contents == "Hello John"
    }

    void "Test render page with embedded JavaScript function call"() {
        given:
            resourceLoader.resources.put("/foo/_bar.gsp", new ByteArrayResource("""
            <h1>\${person}</h1>
            <script type="text/javascript">
            alert("\${person}");
            </script>
            """.bytes))
        when:
            def contents = pageRenderer.render(template:"/foo/bar", model:[person:"John"])
        then:
            contents != null
            contents == """
            <h1>John</h1>
            <script type="text/javascript">
            alert("John");
            </script>
            """
    }

    void "Test render page with curly braces in parens"() {
        given:
            resourceLoader.resources.put("/foo/_bar.gsp", new ByteArrayResource('''
                <g:each var="formatter" in="${formatters}">
                  <h2>${formatter.object} (${formatter.options})</h2>
                </g:each>'''.bytes))
        when:
            def contents = pageRenderer.render(template:"/foo/bar", model:[formatters:[[object: 'obj1', options: 'opt1'], [object: 'obj2', options: 'opt2']]])
        then:

        println "C: $contents"
            contents != null

            contents == '''
                
                  <h2>obj1 (opt1)</h2>
                
                  <h2>obj2 (opt2)</h2>
                '''
    }

    void "Test render page with brackets in HTML"() {
        given:
            resourceLoader.resources.put("/foo/_bar.gsp", new ByteArrayResource("""
				{<% if(something) { %> \${message} ({[<% } %>)
            """.bytes))
        when:
            def contents = pageRenderer.render(template:"/foo/bar", model:[something:true,message:"hello, world"])
        then:
            contents != null

            contents == """
				{ hello, world ({[)
            """
    }

    private PageRenderer getPageRenderer() {
        GroovyPagesTemplateEngine te = new GroovyPagesTemplateEngine()

        te.afterPropertiesSet()
        def renderer = new PageRenderer(te)

        def bb = new BeanBuilder().beans {
            grailsApplication(DefaultGrailsApplication)
            "${ApplicationTagLib.name}"(ApplicationTagLib) {
                grailsApplication = ref('grailsApplication')
            }
        }
        def ctx = bb.createApplicationContext()
        def ga = ctx.getBean(DefaultGrailsApplication)
        ga.initialise()
        ga.addArtefact(TagLibArtefactHandler.TYPE, ApplicationTagLib)

        def tll = new TagLibraryLookup()
        tll.grailsApplication = ga
        tll.applicationContext = ctx

        tll.afterPropertiesSet()

        te.tagLibraryLookup = tll

        def locator = new CachingGrailsConventionGroovyPageLocator()
        locator.addResourceLoader(resourceLoader)
        renderer.groovyPageLocator = locator
        return renderer
    }
}
