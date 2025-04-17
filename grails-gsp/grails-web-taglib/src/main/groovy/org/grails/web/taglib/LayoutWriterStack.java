/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.taglib;

import groovy.lang.Closure;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Class that can be used by "layout" tags, i.e. tags that use the different parts in their body to assemble a bigger part.
 * Example a threeColumn tag that expects a left, center and right part in its body and places them in a table:
 * <pre>
 *  {@code
 *  <g.threeColumn>
 *      <g.left>left contents</g.left>
 *      <g.center>middle contents</g.center>
 *      <g.right>right contents</g.right>
 *  </g.threeColumn>
 *  }
 *  </pre>
 *
 *  @author Ivo Houbrechts
 */
public class LayoutWriterStack {
    private static final String ATTRIBUTE_NAME_WRITER_STACK = "be.ixor.grails.gsptaglib.WRITER_STACK";
    private Stack<Map<String, Object>> stack = new Stack<Map<String, Object>>();

    /**
     * Returns a {@link Writer} where a layout part can write its contents to.
     * This method should only be called by tags that are part of a surrounding layout tag.
     * Example:
     * <pre>
     *  {@code
     *  def left = &#123; attrs, body ->
     *      LayoutWriterStack.currentWriter('left') << "<div class='left'>" << body() <<"</div>"
     *  &#125;
     *  }
     *  </pre>
     *
     * @param name Name of the layout part
     * @return writer
     */
    public static Writer currentWriter(String name) {
        Map<String, Object> writers = currentStack().stack.peek();
        if (writers != null) {
            Writer result = (Writer) writers.get(name);
            if (result == null) {
                result = new StringWriter();
                writers.put(name, result);
            }
            return result;
        }
        return null;
    }

    /**
     * Executes the body closure of a tag and returns a Map with namned results that hold the content of the parts within the body.
     * This method should only be called by tags that are part of a surrounding layout tag.
     * Example:
     * <pre>
     *  {@code
     *  def parts = LayoutWriterStack.writeParts(body)
     *  out << "left part:" << parts.left << "; right part:" << parts.right << ";remainder of body:" << parts.body
     *  }
     *  </pre>
     *
     * @param body the body closure of the calling "layout" tag
     * @return a Map that contains the results of all the parts in the body and the body itself
     */
    public static Map<String, Object> writeParts(Closure<?> body) {
        LayoutWriterStack stack = LayoutWriterStack.currentStack();
        stack.push();
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("body", body.call());
        result.putAll(stack.pop());
        return result;
    }

    private static LayoutWriterStack currentStack() {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        if (attributes != null) {
            LayoutWriterStack stack = (LayoutWriterStack) attributes.getAttribute(ATTRIBUTE_NAME_WRITER_STACK, RequestAttributes.SCOPE_REQUEST);
            if (stack == null) {
                stack = new LayoutWriterStack();
                attributes.setAttribute(ATTRIBUTE_NAME_WRITER_STACK, stack, RequestAttributes.SCOPE_REQUEST);
            }
            return stack;
        }
        return null;
    }

    private void push() {
        stack.push(new HashMap<String, Object>());
    }

    private Map<String, Object> pop() {
        return stack.pop();
    }
}
