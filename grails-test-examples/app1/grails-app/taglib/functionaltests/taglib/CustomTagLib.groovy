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

package functionaltests.taglib

/**
 * Custom Tag Library demonstrating various GSP TagLib features.
 * Tests custom tags with different attribute handling, body processing, and service injection.
 */
class CustomTagLib {

    static namespace = 'custom'

    // Use 'none' encoding for tags that output HTML structure
    // Individual tags can use encodeAsHTML() where needed
    static defaultEncodeAs = [taglib: 'none']

    // Note: grailsApplication is automatically available via WebAttributes trait

    /**
     * Simple tag that outputs a greeting message.
     * Usage: <custom:hello name="World"/>
     */
    def hello = { attrs ->
        def name = attrs.name ?: 'Guest'
        out << "Hello, ${name}!"
    }

    /**
     * Tag with body content processing.
     * Usage: <custom:wrapper title="My Title">Body content here</custom:wrapper>
     */
    def wrapper = { attrs, body ->
        def title = attrs.title ?: 'Default Title'
        def cssClass = attrs.cssClass ?: 'wrapper'
        out << "<div class=\"${cssClass}\">"
        out << "<h2>${title}</h2>"
        out << "<div class=\"content\">${body()}</div>"
        out << "</div>"
    }

    /**
     * Tag that iterates over a collection.
     * Usage: <custom:iterate items="${['a','b','c']}" var="item">${item}</custom:iterate>
     */
    def iterate = { attrs, body ->
        def items = attrs.items ?: []
        def varName = attrs.var ?: 'it'
        def separator = attrs.separator ?: ''

        items.eachWithIndex { item, index ->
            out << body((varName): item, index: index)
            if (index < items.size() - 1 && separator) {
                out << separator
            }
        }
    }

    /**
     * Conditional tag that shows content based on a condition.
     * Usage: <custom:showIf test="${condition}">Content shown if true</custom:showIf>
     */
    def showIf = { attrs, body ->
        if (attrs.test) {
            out << body()
        }
    }

    /**
     * Conditional tag that hides content based on a condition.
     * Usage: <custom:hideIf test="${condition}">Content hidden if true</custom:hideIf>
     */
    def hideIf = { attrs, body ->
        if (!attrs.test) {
            out << body()
        }
    }

    /**
     * Tag demonstrating multiple attribute types.
     * Usage: <custom:formatted value="${123.456}" format="currency" decimals="2"/>
     */
    def formatted = { attrs ->
        def value = attrs.value
        def format = attrs.format ?: 'default'
        def decimals = (attrs.decimals ?: '2') as Integer

        switch (format) {
            case 'currency':
                out << String.format("\$%.${decimals}f", value as Double)
                break
            case 'percentage':
                out << String.format("%.${decimals}f%%", (value as Double) * 100)
                break
            case 'number':
                out << String.format("%.${decimals}f", value as Double)
                break
            default:
                out << value?.toString()
        }
    }

    /**
     * Tag that creates a list with custom rendering.
     * Usage: <custom:list items="${items}" type="ordered"/>
     */
    def list = { attrs, body ->
        def items = attrs.items ?: []
        def type = attrs.type ?: 'unordered'
        def cssClass = attrs.cssClass ?: ''
        def listTag = type == 'ordered' ? 'ol' : 'ul'

        out << "<${listTag}${cssClass ? " class=\"${cssClass}\"" : ''}>"
        items.each { item ->
            out << "<li>"
            if (body) {
                out << body(item: item)
            } else {
                out << item?.toString()
            }
            out << "</li>"
        }
        out << "</${listTag}>"
    }

    /**
     * Tag that demonstrates nested tag support.
     * Usage: <custom:panel type="info" title="Panel Title">Panel content</custom:panel>
     */
    def panel = { attrs, body ->
        def type = attrs.type ?: 'default'
        def title = attrs.title
        def collapsible = attrs.collapsible?.toString() == 'true'

        out << "<div class=\"panel panel-${type}\">"
        if (title) {
            out << "<div class=\"panel-header\">"
            out << "<h3>${title}</h3>"
            if (collapsible) {
                out << "<button class=\"collapse-btn\">Toggle</button>"
            }
            out << "</div>"
        }
        out << "<div class=\"panel-body\">${body()}</div>"
        out << "</div>"
    }

    /**
     * Tag demonstrating service injection usage.
     * Usage: <custom:appInfo/>
     */
    def appInfo = { attrs ->
        def appName = grailsApplication?.config?.getProperty('info.app.name') ?: 'Unknown App'
        def appVersion = grailsApplication?.config?.getProperty('info.app.version') ?: 'Unknown'
        out << "<span class=\"app-info\">${appName} v${appVersion}</span>"
    }

    /**
     * Tag that creates a badge/pill component.
     * Usage: <custom:badge type="success">5</custom:badge>
     */
    def badge = { attrs, body ->
        def type = attrs.type ?: 'default'
        def size = attrs.size ?: 'normal'
        out << "<span class=\"badge badge-${type} badge-${size}\">${body()}</span>"
    }

    /**
     * Tag demonstrating attribute default values and type coercion.
     * Usage: <custom:progress value="75" max="100"/>
     */
    def progress = { attrs ->
        def value = (attrs.value ?: '0') as Integer
        def max = (attrs.max ?: '100') as Integer
        def showLabel = attrs.showLabel?.toString() != 'false'
        def percentage = max > 0 ? (value * 100 / max) as Integer : 0

        out << "<div class=\"progress\">"
        out << "<div class=\"progress-bar\" style=\"width: ${percentage}%\">"
        if (showLabel) {
            out << "${percentage}%"
        }
        out << "</div></div>"
    }

    /**
     * Tag that processes body content multiple times.
     * Usage: <custom:repeat times="3">Content to repeat</custom:repeat>
     */
    def repeat = { attrs, body ->
        def times = (attrs.times ?: '1') as Integer
        def separator = attrs.separator ?: ''

        times.times { i ->
            out << body(iteration: i + 1, isFirst: i == 0, isLast: i == times - 1)
            if (i < times - 1 && separator) {
                out << separator
            }
        }
    }

    /**
     * Tag demonstrating raw (unescaped) output.
     * Usage: <custom:raw content="${'<b>bold</b>'}"/>
     */
    def raw = { attrs ->
        out << raw(attrs.content ?: '')
    }

    /**
     * Tag for creating definition lists.
     * Usage: <custom:definitionList items="${[name:'John', age:30]}"/>
     */
    def definitionList = { attrs ->
        def items = attrs.items ?: [:]
        def cssClass = attrs.cssClass ?: ''

        out << "<dl${cssClass ? " class=\"${cssClass}\"" : ''}>"
        items.each { key, value ->
            out << "<dt>${key}</dt>"
            out << "<dd>${value}</dd>"
        }
        out << "</dl>"
    }

    /**
     * Tag demonstrating request attribute access.
     * Usage: <custom:requestInfo attr="contextPath"/>
     */
    def requestInfo = { attrs ->
        def attrName = attrs.attr ?: 'contextPath'
        def value

        switch (attrName) {
            case 'contextPath':
                value = request.contextPath
                break
            case 'method':
                value = request.method
                break
            case 'requestURI':
                value = request.requestURI
                break
            case 'serverName':
                value = request.serverName
                break
            default:
                value = request.getAttribute(attrName)
        }

        out << (value ?: '')
    }

    /**
     * Tag demonstrating session attribute handling.
     * Usage: <custom:sessionValue key="username" default="Anonymous"/>
     */
    def sessionValue = { attrs ->
        def key = attrs.key
        def defaultValue = attrs.default ?: ''
        out << (session?.getAttribute(key) ?: defaultValue)
    }

    /**
     * Tag demonstrating pageScope variable setting.
     * Usage: <custom:setVar name="myVar" value="myValue"/>
     */
    def setVar = { attrs ->
        def name = attrs.name
        def value = attrs.value
        if (name) {
            pageScope[name] = value
        }
    }

    /**
     * Alert/message box tag.
     * Usage: <custom:alert type="warning" dismissible="true">Warning message</custom:alert>
     */
    def alert = { attrs, body ->
        def type = attrs.type ?: 'info'
        def dismissible = attrs.dismissible?.toString() == 'true'
        def icon = attrs.icon

        out << "<div class=\"alert alert-${type}${dismissible ? ' alert-dismissible' : ''}\" role=\"alert\">"
        if (icon) {
            out << "<span class=\"alert-icon\">${icon}</span>"
        }
        if (dismissible) {
            out << "<button type=\"button\" class=\"close\" aria-label=\"Close\">&times;</button>"
        }
        out << "<div class=\"alert-content\">${body()}</div>"
        out << "</div>"
    }

    /**
     * Tag demonstrating collection transformation.
     * Usage: <custom:join items="${['a','b','c']}" separator=", "/>
     */
    def join = { attrs ->
        def items = attrs.items ?: []
        def separator = attrs.separator ?: ', '
        def property = attrs.property

        if (property) {
            out << items.collect { it[property] }.join(separator)
        } else {
            out << items.join(separator)
        }
    }

    /**
     * Tag for conditional class assignment.
     * Usage: <custom:cssClass base="btn" active="${isActive}" disabled="${isDisabled}"/>
     */
    def cssClass = { attrs ->
        def classes = [attrs.base ?: '']
        attrs.each { key, value ->
            if (key != 'base' && value?.toString() == 'true') {
                classes << key
            }
        }
        out << classes.findAll { it }.join(' ')
    }
}
