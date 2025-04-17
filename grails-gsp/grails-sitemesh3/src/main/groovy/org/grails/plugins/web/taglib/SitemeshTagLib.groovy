package org.grails.plugins.web.taglib

import org.grails.buffer.GrailsPrintWriter
import org.grails.buffer.StreamCharBuffer
import org.grails.encoder.CodecLookup
import org.grails.encoder.Encoder
import grails.artefact.TagLibrary
import grails.gsp.TagLib

@TagLib
class SitemeshTagLib implements TagLibrary {
    static String namespace = 'sitemesh'
    CodecLookup codecLookup
    // from org.grails.gsp.compiler.SitemeshPreprocessor
    public static final String XML_CLOSING_FOR_EMPTY_TAG_ATTRIBUTE_NAME = "gsp_sm_xmlClosingForEmptyTag";

    SitemeshTagLib(CodecLookup codecLookup) {
        this.codecLookup = codecLookup
    }

    def captureTagContent(GrailsPrintWriter writer, String tagname, Map attrs, Object body, boolean noEndTagForEmpty=false, boolean useNamespace = false) {
        def content = null
        if (body != null) {
            if (body instanceof Closure) {
                content = body()
            }
            else {
                content = body
            }
        }

        if (content instanceof StreamCharBuffer) {
            content.setPreferSubChunkWhenWritingToOtherBuffer(true)
        }
        writer << '<'+(useNamespace? SitemeshTagLib.namespace + ':' : '')
        writer << tagname
        def useXmlClosingForEmptyTag = false
        if (attrs) {
            def xmlClosingString = attrs.remove(XML_CLOSING_FOR_EMPTY_TAG_ATTRIBUTE_NAME)
            if (xmlClosingString=='/') {
                useXmlClosingForEmptyTag = true
            }
            Encoder htmlEncoder = codecLookup?.lookupEncoder('HTML')
            attrs.each { k, v ->
                writer << ' '
                writer << k
                writer << '="'
                writer << (htmlEncoder != null ? htmlEncoder.encode(v) : v)
                writer << '"'
            }
        }

        if (content) {
            writer << '>'
            // the following row must be written separately (append StreamCharBuffer gets appended as subchunk)
            writer << content
            writer << '</'+(useNamespace? SitemeshTagLib.namespace + ':' : '')
            writer << tagname
            writer << '>'
        }
        else {
            if (!useXmlClosingForEmptyTag) {
                writer << '>'
                // in valid HTML , closing of an empty tag depends on the element name
                // for empty title, the tag must be closed properly
                // for empty meta tag shouldn't be closed at all, see GRAILS-5696
                if (!noEndTagForEmpty) {
                    writer << '</'+(useNamespace? SitemeshTagLib.namespace + ':' : '')
                    writer << tagname
                    writer << '>'
                }
            }
            else {
                // XML / XHTML empty tag
                writer << '/>'
            }
        }
        content
    }

    // Grails org.grails.gsp.compiler.SitemeshPreprocessor replaces
    // <content /> tags with <sitemesh:captureContent /> tags. This just changes them back.
    // Alternatively, a 'sitemesh:captureContent' ContentBlockExtractingRule can be used.
    // See: GrailsTagRuleBundle
    Closure captureContent = { attrs, body ->
        StringBuilder tag = new StringBuilder('<content ')
        attrs.each {
            tag.append("""${it.key}="${it.value}" """)
        }
        tag.append('>')
        tag.append(body())
        tag.append('</content>')
        out << tag.toString()
    }

    Closure captureHead = { Map attrs, body ->
        captureTagContent(out, 'head', attrs, body)
    }

    /**
     * Allows passing of parameters to Sitemesh layout.<br/>
     *
     * &lt;sitemesh:parameter name="foo" value="bar" /&gt;
     */
    Closure parameter = { Map attrs, body ->
        captureTagContent(out, 'parameter', attrs, body)
    }

    Closure captureBody = { Map attrs, body ->
        captureTagContent(out, 'body', attrs, body)
    }
    Closure captureMeta = { Map attrs, body ->
        captureTagContent(out, 'meta', attrs, body, true)
    }

    Closure captureTitle = { Map attrs, body ->
        captureTagContent(out, 'title', attrs, body)
    }

    Closure wrapTitleTag = { Map attrs, body ->
        out << body()
    }

    Closure write = { Map attrs, body ->
        captureTagContent(out, 'write', attrs, body, false, true)
    }
}
