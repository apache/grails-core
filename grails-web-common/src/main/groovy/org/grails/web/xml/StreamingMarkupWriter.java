/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.web.xml;

import java.io.Writer;
import java.io.IOException;

/**
 * Specialized StreamingMarkupWriter that handles the escaping of double quotes in XML attributes
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
public class StreamingMarkupWriter extends groovy.xml.streamingmarkupsupport.StreamingMarkupWriter {

    public StreamingMarkupWriter(Writer writer, String s) {
        super(writer, s);
    }

    public StreamingMarkupWriter(Writer writer) {
        super(writer);
    }

    @Override
    public void write(int i) throws IOException {
        if (writingAttribute && i == '"') {
            writer.write("&quot;");
        }
        else {
            super.write(i);
        }
    }
}
