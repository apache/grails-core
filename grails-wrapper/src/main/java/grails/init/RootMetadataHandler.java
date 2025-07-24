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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A SAX handler to parse the metadata-maven.xml file and extract the latest release version
 */
public class RootMetadataHandler extends DefaultHandler {

    private Set<GrailsReleaseType> allowedReleaseTypes;
    private List<GrailsVersion> versions = new ArrayList<>();

    private boolean foundVersions;
    private boolean foundVersion;

    public RootMetadataHandler(Set<GrailsReleaseType> allowedReleaseTypes) {
        this.allowedReleaseTypes = allowedReleaseTypes;
    }

    @Override
    public void startElement(String uri, String localName,String qName,
                            Attributes attributes) {
        if (qName.equalsIgnoreCase("VERSIONS")) {
            foundVersions = true;
        } else if (foundVersions && qName.equalsIgnoreCase("VERSION")) {
            foundVersion = true;
        } else {
            foundVersions = false;
            foundVersion = false;
        }
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        if (foundVersion) {
            foundVersion = false;

            String versionString = new String(ch, start, length);
            try {
                GrailsVersion version = new GrailsVersion(versionString);
                if (allowedReleaseTypes.contains(version.releaseType)) {
                    versions.add(version);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse version '" + versionString + "' from maven repository.", e);
            }
        }
    }

    public List<GrailsVersion> getVersions() {
        return versions;
    }
}
