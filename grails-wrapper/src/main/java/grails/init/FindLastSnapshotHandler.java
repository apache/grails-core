/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.init;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class FindLastSnapshotHandler extends DefaultHandler {

    private boolean insideSnapshotVersion = false;
    private boolean insideVersion = false;
    private boolean jarExtension = false;

    private String version;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (qName) {
            case "snapshotVersion":
                insideSnapshotVersion = true;
                jarExtension = false;
                break;
            case "value":
                if (insideSnapshotVersion && jarExtension) {
                    insideVersion = true;
                }
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (!insideSnapshotVersion) {
            return;
        }

        if (insideVersion) {
            version = new String(ch, start, length);
        } else if (!jarExtension) {
            String text = new String(ch, start, length).trim();
            if ("jar".equals(text)) {
                jarExtension = true;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        switch (qName) {
            case "value":
                if (insideVersion) {
                    insideVersion = false;
                }
                break;
            case "snapshotVersion":
                insideSnapshotVersion = false;
                jarExtension = false;
                break;
        }
    }

    public String getVersion() {
        return version;
    }
}
