/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.gsp.jsp

import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.buffer.FastStringWriter
import org.springframework.beans.BeanWrapperImpl
import org.springframework.util.ClassUtils

import jakarta.servlet.jsp.JspContext
import jakarta.servlet.jsp.JspWriter
import jakarta.servlet.jsp.tagext.*

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class JspTagImpl implements JspTag {
    static final Log LOG = LogFactory.getLog(JspTagImpl)
    ClassLoader classLoader
    String tagClassName
    volatile Class tagClass
    boolean tryCatchFinally
    boolean body
    boolean iteration

    JspTagImpl(String tagClassName, ClassLoader classLoader) {
        this.tagClassName = tagClassName
        this.classLoader = classLoader
    }
    
    JspTagImpl(Class tagClass) {
        this.tagClass = tagClass
        this.tagClassName = tagClass.name
        this.classLoader = tagClass.classLoader
        initializeTagClassTypes()
    }

    void doTag(Writer targetWriter, Map<String,Object> attributes) {
        doTag targetWriter,attributes, null
    }
    
    protected void checkInitialized() {
        if(tagClass==null) {
            synchronized(this) {
                tagClass = ClassUtils.forName(tagClassName, classLoader)
                initializeTagClassTypes()
            }
        }
    }

    private initializeTagClassTypes() {
        tryCatchFinally = TryCatchFinally.isAssignableFrom(tagClass)
        body = BodyTag.isAssignableFrom(tagClass)
        iteration = IterationTag.isAssignableFrom(tagClass)
    }

    protected jakarta.servlet.jsp.tagext.JspTag createTagInstance() {
        checkInitialized()
        (jakarta.servlet.jsp.tagext.JspTag)tagClass.newInstance()
    }

    void doTag(Writer targetWriter, Map<String,Object> attributes, Closure<?> body) {
        jakarta.servlet.jsp.tagext.JspTag tag = createTagInstance()
        GroovyPagesPageContext pageContext = PageContextFactory.getCurrent()

        assignParentTag(pageContext, tag)
        
        assignPageContext(pageContext, tag)

        applyAttributes(tag, attributes)

        if (tag instanceof SimpleTag) {
            def simpleTag = (SimpleTag) tag
            handleSimpleTag(simpleTag, attributes, pageContext, targetWriter, body)
        }
        else if (tag instanceof Tag) {
            Tag theTag = (Tag)tag
            withJspWriterDelegate pageContext, targetWriter, {

                try {
                    pageContext.pushTopTag theTag
                    int state = theTag.doStartTag()
                    BodyContent bodyContent
                    def out = pageContext.getOut()
                    if ((state == Tag.EVAL_BODY_INCLUDE || state == IterationTag.EVAL_BODY_AGAIN) && body) {
                        if (state == BodyTag.EVAL_BODY_BUFFERED && isBody()) {
                            bodyContent = pageContext.pushBody()
                            out = bodyContent
                            BodyTag bodyTag = (BodyTag)theTag
                            bodyTag.bodyContent = bodyContent
                            bodyTag.doInitBody()
                        }
                        out << body.call()
                        if (isIterationTag()) {
                            state = ((IterationTag)theTag).doAfterBody()
                            while (state != Tag.SKIP_BODY) {
                                out << body.call()
                                state = ((IterationTag)theTag).doAfterBody()
                            }
                        }
                    }
                    if (bodyContent) {
                        pageContext.popBody()
                    }
                    state = theTag.doEndTag()
                    if (state == Tag.SKIP_PAGE) {
                        LOG.warn "Tag ${theTag.getClass().getName()} returned SKIP_PAGE which is not supported in GSP"
                    }
                }
                catch (Throwable t) {
                    if (isTryCatchFinally() && theTag) {
                        ((TryCatchFinally)theTag).doCatch(t)
                    }
                    else {
                        throw t
                    }
                }
                finally {
                    if (isTryCatchFinally() && theTag) {
                        ((TryCatchFinally)theTag).doFinally()
                    }
                    pageContext.popTopTag()
                    theTag?.release()
                }
            }
        }
                }

    private assignPageContext(GroovyPagesPageContext pageContext, jakarta.servlet.jsp.tagext.JspTag tag) {
        if (tag instanceof SimpleTag) {
            tag.jspContext = pageContext
            }
        if (tag instanceof Tag) {
            tag.pageContext = pageContext
        }
    }

    private applyAttributes(jakarta.servlet.jsp.tagext.JspTag tag, Map<String,Object> attributes) {
        BeanWrapperImpl tagBean = new BeanWrapperImpl(tag)

        attributes?.each { String key, Object value ->
            if (key && tagBean.isWritableProperty(key)) {
                tagBean.setPropertyValue key, value
            }
            else if(key && tag instanceof DynamicAttributes){
                ((DynamicAttributes)tag).setDynamicAttribute(null,key,value);
            }
        }
    }

    private assignParentTag(GroovyPagesPageContext pageContext, jakarta.servlet.jsp.tagext.JspTag tag) {
        def parentTag = pageContext.peekTopTag(jakarta.servlet.jsp.tagext.JspTag)
        if (parentTag) {
            if (tag instanceof Tag && parentTag instanceof SimpleTag) {
                tag.parent = new TagAdapter(parentTag)
            }
            else if (tag instanceof Tag) {
                tag.parent = (Tag)parentTag
            }
            else if (tag instanceof SimpleTag) {
                tag.parent = (Tag)parentTag
            }
        }
    }

    void withJspWriterDelegate(GroovyPagesPageContext pageContext,Writer delegate, Closure callable) {
        pageContext.pushWriter new JspWriterDelegate(delegate)
        try {
            callable()
        }
        finally {
            pageContext.popWriter()
        }
    }

    protected handleSimpleTag(SimpleTag tag, Map attributes,GroovyPagesPageContext pageContext,
            Writer targetWriter, Closure body) {

        withJspWriterDelegate pageContext, targetWriter, {
            if (body) {
                pageContext.pushTopTag tag
                try {
                    FastStringWriter buffer = new FastStringWriter()
                    JspWriter bufferedOut = new JspWriterDelegate(buffer)
                    pageContext.pushWriter bufferedOut
                    if (body) {
                        bufferedOut << body.call()
                        tag.setJspBody(new JspFragmentImpl(pageContext, buffer))
                    }
                }
                finally {
                    pageContext.popWriter()
                    pageContext.popTopTag()
                }
            }
            tag.doTag()
        }
    }

//----------------------------------------------------------------------------
// Workarounds for http://jira.codehaus.org/browse/GROOVY-2897
//
    boolean isBodyTag() {
        checkInitialized()
        return body
    }

    boolean isIterationTag() {
        checkInitialized()
        return iteration
    }

    boolean isTryCatchFinallyTag() {
        checkInitialized()
        return tryCatchFinally
    }
}

class JspFragmentImpl extends JspFragment {

    GroovyPagesPageContext pageContext
    FastStringWriter body

    JspFragmentImpl(GroovyPagesPageContext pageContext, FastStringWriter body) {
        this.pageContext = pageContext
        this.body = body
    }

    JspContext getJspContext() {
        return pageContext
    }

    void invoke(Writer out) {
        out << body.toString()
    }
}
