package org.grails.plugins.web.taglib

import org.grails.web.util.WebUtils
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProperty
import org.sitemesh.webapp.SiteMeshFilter
import org.sitemesh.webapp.WebAppContext
import org.sitemesh.webapp.contentfilter.ResponseMetaData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import grails.artefact.TagLibrary
import grails.gsp.TagLib

import java.nio.CharBuffer

@TagLib
class RenderSitemeshTagLib implements TagLibrary {

    SiteMeshFilter siteMeshFilter

    @Autowired
    RenderSitemeshTagLib(@Qualifier("sitemesh") FilterRegistrationBean sitemesh) {
        this.siteMeshFilter = (SiteMeshFilter) sitemesh.getFilter()
    }

    Closure applyLayout = { Map attrs, body ->
        String savedAttribute = request.getAttribute(WebUtils.LAYOUT_ATTRIBUTE)
        WebAppContext context = new WebAppContext("text/html", request, response,
                servletContext, siteMeshFilter.contentProcessor,  new ResponseMetaData(), false);
        Content content = siteMeshFilter.contentProcessor.build(CharBuffer.wrap(body()), context);
        if (attrs.name) {
            request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, attrs.name)
        }
        String[] decoratorPaths = siteMeshFilter.decoratorSelector.selectDecoratorPaths(content, context);
        for (String decoratorPath : decoratorPaths) {
            content = context.decorate(decoratorPath, content);
        }
        content.getData().writeValueTo(out)
        request.setAttribute(WebUtils.LAYOUT_ATTRIBUTE, savedAttribute)
    }

    private ContentProperty getContentProperty(String name) {
        if (!name) {
            return null
        }
        Content content = request.getAttribute(WebAppContext.CONTENT_KEY)
        ContentProperty currentProperty = content.getExtractedProperties();
        for (String childPropertyName : name.split("\\.")) {
            currentProperty = currentProperty.getChild(childPropertyName);
        }
        currentProperty
    }

    /**
     * Used to retrieve a property of the decorated page.<br/>
     *
     * &lt;g:pageProperty default="defaultValue" name="body.onload" /&gt;<br/>
     *
     * @emptyTag
     *
     * @attr REQUIRED name the property name
     * @attr default the default value to use if the property is null
     * @attr writeEntireProperty if true, writes the property in the form 'foo = "bar"', otherwise renders 'bar'
     */
    Closure pageProperty = { attrs ->
        if (!attrs.name) {
            throwTagError("Tag [pageProperty] is missing required attribute [name]")
        }
        String propertyName = attrs.name as String
        ContentProperty contentProperty = getContentProperty(propertyName)
        def propertyValue = contentProperty?.hasValue()? contentProperty.getValue() : attrs.'default' ?: null

        if (propertyValue) {
            if (attrs.writeEntireProperty) {
                out << ' '
                out << propertyName.substring(propertyName.lastIndexOf('.') + 1)
                out << "=\""
                out << propertyValue
                out << "\""
            } else {
                out << propertyValue
            }
        }
    }

    /**
     * Invokes the body of this tag if the page property exists:<br/>
     *
     * &lt;g:ifPageProperty name="meta.index"&gt;body to invoke&lt;/g:ifPageProperty&gt;<br/>
     *
     * or it equals a certain value:<br/>
     *
     * &lt;g:ifPageProperty name="meta.index" equals="blah"&gt;body to invoke&lt;/g:ifPageProperty&gt;
     *
     * @attr name REQUIRED the property name
     * @attr equals optional value to test against
     */
    Closure ifPageProperty = { Map attrs, body ->
        if (!attrs.name) {
            return
        }
        List names = ((attrs.name instanceof List) ? (List)attrs.name : [attrs.name])

        def invokeBody = true
        for (i in 0..<names.size()) {
            def propertyValue = getContentProperty(names[i] as String)?.getValue()
            if (propertyValue) {
                if (attrs.containsKey('equals')) {
                    if (attrs.equals instanceof List) {
                        invokeBody = ((List)attrs.equals)[i] == propertyValue
                    } else {
                        invokeBody = attrs.equals == propertyValue
                    }
                }
            } else {
                invokeBody = false
                break
            }
        }
        if (invokeBody && body instanceof Closure) {
            out << body()
        }
    }

    Closure layoutTitle = { attrs ->
        out << """<sitemesh:write property="title">${attrs.default?:''}</sitemesh:write>""".toString()
    }

    Closure layoutHead = { attrs, body ->
        StringBuilder tag = new StringBuilder('<sitemesh:write property="head"')
        String bodyContent = body()
        if (bodyContent) {
            tag.append('>')
            tag.append(bodyContent)
            tag.append('</sitemesh:write>')
        } else {
            tag.append('/>')
        }
        out << tag.toString()
    }

    Closure layoutBody = { attrs, body ->
        StringBuilder tag = new StringBuilder('<sitemesh:write property="body"')
        String bodyContent = body()
        if (bodyContent) {
            tag.append('>')
            tag.append(bodyContent)
            tag.append('</sitemesh:write>')
        } else {
            tag.append('/>')
        }
        out << tag.toString()
    }

    Closure content = { attrs, body ->
        StringBuilder tag = new StringBuilder("""<content tag="${attrs.tag}">""")
        tag.append(body())
        tag.append('</content>')
        out << tag.toString()
    }
}
