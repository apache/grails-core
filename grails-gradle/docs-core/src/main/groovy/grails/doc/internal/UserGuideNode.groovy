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

package grails.doc.internal

import groovy.transform.ToString

@ToString(excludes="parent, children")
class UserGuideNode {
    UserGuideNode parent
    List children = []

    /**
     * The identifier for this node. It's basically the gdoc filename minus the
     * '.gdoc' suffix. Will be <code>null</code> or empty for the root node.
     */
    String name

    /** The node title, as displayed in the generated user guide. */
    String title

    /**
     * The location (including filename) of the node, relatively to the root
     * of the gdoc source directory. Uses Unix style path separators, i.e. '/'/
     */
    String file
}

