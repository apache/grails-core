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
package org.grails.plugins.sitemesh3;

import java.io.IOException;
import java.nio.CharBuffer;

import jakarta.servlet.http.HttpServletRequest;

import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.Content;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.content.tagrules.TagBasedContentProcessor;
import org.sitemesh.content.tagrules.TagRuleBundle;
import org.sitemesh.content.tagrules.decorate.DecoratorTagRuleBundle;
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle;
import org.sitemesh.content.tagrules.html.Sm2TagRuleBundle;
import org.sitemesh.webapp.WebAppContext;

/**
 * {@link ContentProcessor} that short-circuits the HTML parse when a
 * {@link Sitemesh3CapturedPage} is already populated on the current request
 * (i.e. the GSP compile-time capture taglib has filled it in). Falls back to a
 * {@link TagBasedContentProcessor} with the SiteMesh 2 bundle for responses
 * that were not produced by the capture taglib.
 */
public class CaptureAwareContentProcessor implements ContentProcessor {

    private final ContentProcessor fallback;

    public CaptureAwareContentProcessor() {
        this(new TagBasedContentProcessor(
                new CoreHtmlTagRuleBundle(),
                new DecoratorTagRuleBundle(),
                new Sm2TagRuleBundle()));
    }

    public CaptureAwareContentProcessor(ContentProcessor fallback) {
        this.fallback = fallback;
    }

    public CaptureAwareContentProcessor(TagRuleBundle... bundles) {
        this(new TagBasedContentProcessor(bundles));
    }

    @Override
    public Content build(CharBuffer data, SiteMeshContext context) throws IOException {
        Sitemesh3CapturedPage captured = findCapturedPage(context);

        // Decoration phase: RenderSitemeshTagLib has already inlined layout
        // placeholders at tag-render time, so the layout output is final.
        // If the layout's own capture taglibs populated a page (the common
        // case when the layout has <head>/<body>), attach the raw layout
        // output as the page's rendered data and return it — chained
        // decoration can still read head/body/title properties from the
        // captured page. Fall back to the parser only when no capture
        // happened (e.g. a layout with no HTML skeleton).
        if (context.getContentToMerge() != null) {
            if (captured != null && captured.isUsed()) {
                captured.setRenderedContent(data.toString());
                return captured;
            }
            return fallback.build(data, context);
        }

        if (captured != null && captured.isUsed()) {
            return captured;
        }
        return fallback.build(data, context);
    }

    private Sitemesh3CapturedPage findCapturedPage(SiteMeshContext context) {
        if (!(context instanceof WebAppContext)) {
            return null;
        }
        HttpServletRequest request = ((WebAppContext) context).getRequest();
        if (request == null) {
            return null;
        }
        Object attr = request.getAttribute(Sitemesh3CapturedPage.REQUEST_ATTRIBUTE);
        return attr instanceof Sitemesh3CapturedPage ? (Sitemesh3CapturedPage) attr : null;
    }
}
