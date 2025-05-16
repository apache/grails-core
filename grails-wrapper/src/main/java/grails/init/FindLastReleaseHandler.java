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
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A SAX handler to parse the metadata-maven.xml file and extract the latest release version
 */
public class FindLastReleaseHandler extends DefaultHandler {

    private String releaseVersion;
    private String latestVersion;

    private boolean foundRelease;
    private boolean foundLatest;

    @Override
    public void startElement(String uri, String localName,String qName,
                            Attributes attributes) {
        if (qName.equalsIgnoreCase("RELEASE")) {
            foundRelease = true;
        }
        if (qName.equalsIgnoreCase("LATEST")) {
            foundLatest = true;
        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        if (foundRelease) {
            releaseVersion = new String(ch, start, length);
        }
        if (foundLatest) {
            latestVersion = new String(ch, start, length);
        }
        foundLatest = false;
        foundRelease = false;
    }

    public String getVersion() {
        if (releaseVersion != null) {
            return releaseVersion;
        } else if (latestVersion != null) {
            return latestVersion;
        }
        return null;
    }
}
