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

package  test

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString


@EqualsAndHashCode(includes='user')
@ToString(includes='user', includeNames=true, includePackage=false)
class Profile implements Serializable {

    private static final long serialVersionUID = 1

     
    String myQuestion1
    String myAnswer1
     
    String myQuestion2
    String myAnswer2
     
    User user

     static constraints = {
         
             myQuestion1 nullable: true, blank: false
             myAnswer1 nullable: false, blank: false
         
             myQuestion2 nullable: true, blank: false
             myAnswer2 nullable: false, blank: false
         
            user nullable: false, blank: false, unique: true
        }

    static belongsTo = [ user: User]


}