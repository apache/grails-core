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
package org.apache.grails.web.layout;

import com.opensymphony.module.sitemesh.Page;
import com.opensymphony.module.sitemesh.html.HTMLProcessor;
import com.opensymphony.module.sitemesh.html.State;
import com.opensymphony.module.sitemesh.html.StateTransitionRule;
import com.opensymphony.module.sitemesh.html.rules.BodyTagRule;
import com.opensymphony.module.sitemesh.html.rules.ContentBlockExtractingRule;
import com.opensymphony.module.sitemesh.html.rules.FramesetRule;
import com.opensymphony.module.sitemesh.html.rules.HeadExtractingRule;
import com.opensymphony.module.sitemesh.html.rules.HtmlAttributesRule;
import com.opensymphony.module.sitemesh.html.rules.MSOfficeDocumentPropertiesRule;
import com.opensymphony.module.sitemesh.html.rules.MetaTagRule;
import com.opensymphony.module.sitemesh.html.rules.PageBuilder;
import com.opensymphony.module.sitemesh.html.rules.ParameterExtractingRule;
import com.opensymphony.module.sitemesh.html.rules.TitleExtractingRule;
import com.opensymphony.module.sitemesh.html.util.CharArray;
import com.opensymphony.module.sitemesh.parser.HTMLPageParser;
import com.opensymphony.module.sitemesh.parser.TokenizedHTMLPage;
import com.opensymphony.sitemesh.Content;

import java.io.IOException;

public class GrailsHTMLPageParser extends HTMLPageParser {

    @Override
    public Page parse(char[] data) throws IOException {
        CharArray head = new CharArray(64);
        CharArray body = new CharArray(4096);
        TokenizedHTMLPage page = new TokenizedHTMLPage(data, body, head);
        HTMLProcessor processor = new HTMLProcessor(data, body);
        State html = processor.defaultState();

        // Core rules for Grails Layout to be functional.
        html.addRule(new HeadExtractingRule(head)); // contents of <head>
        html.addRule(new BodyTagRule(page, body)); // contents of <body>
        html.addRule(new TitleExtractingRule(page)); // the <title>
        html.addRule(new FramesetRule(page)); // if the page is a frameset

        // Additional rules - designed to be tweaked.
        addUserDefinedRules(html, page);

        processor.process();
        return page;
    }

    public Content parseContent(char[] data) throws IOException {
        return new TokenizedHTMLPage2Content((TokenizedHTMLPage) parse(data));
    }

    @Override
    protected void addUserDefinedRules(State html, PageBuilder page) {
        // Ensure that while in <xml> tag, none of the other rules kick in.
        // For example <xml><book><title>hello</title></book></xml> should not change the affect the title of the page.
        State xml = new State();
        html.addRule(new StateTransitionRule("xml", xml));

        // Useful properties
        html.addRule(new HtmlAttributesRule(page));         // attributes in <html> element
        html.addRule(new MetaTagRule(page));                // all <meta> tags
        html.addRule(new ParameterExtractingRule(page));    // <parameter> blocks
        html.addRule(new ContentBlockExtractingRule(page)); // <content> blocks

        // Capture properties written to documents by MS Office (author, version, company, etc).
        // Note: These properties are from the xml state, not the html state.
        xml.addRule(new MSOfficeDocumentPropertiesRule(page));
    }
}
