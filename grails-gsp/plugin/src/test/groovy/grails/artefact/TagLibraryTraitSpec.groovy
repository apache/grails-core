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
package grails.artefact

import spock.lang.Specification

class TagLibraryTraitSpec extends Specification {

    void 'test that a class marked with @Artefact("TagLib") is enhanced with grails.artefact.TagLibrary'() {
        expect:
        TagLibrary.isAssignableFrom SomeTagLib
    }
    
    void 'test that a class marked with @Artefact("TagLibrary") is enhanced with grails.artefact.TagLibrary'() {
        expect:
            TagLibrary.isAssignableFrom SomeOtherTagLib
    }
}

@Artefact('TagLib')
class SomeTagLib {}

@Artefact('TagLibrary')
class SomeOtherTagLib {}

