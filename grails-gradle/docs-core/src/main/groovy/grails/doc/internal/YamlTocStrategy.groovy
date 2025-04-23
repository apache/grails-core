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

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Class representing a Grails user guide table of contents defined in YAML.
 */
class YamlTocStrategy {
    private final parser = new Yaml(new SafeConstructor(new LoaderOptions()))
    private resourceChecker
    private String ext = ".gdoc"

    YamlTocStrategy(resourceChecker, String ext = ".gdoc") {
        this.resourceChecker = resourceChecker
        this.ext = ext
    }

    UserGuideNode generateToc(yaml) {
        return load(yaml)
    }

    protected UserGuideNode load(String yaml) {
        return process(parser.load(yaml))
    }

    protected UserGuideNode load(File file) {
        file.withInputStream { input ->
            return process(parser.load(input))
        }
    }

    protected UserGuideNode load(InputStream input) {
        return process(parser.load(input))
    }

    protected UserGuideNode load(Reader input) {
        return process(parser.load(input))
    }

    private process(yamlDoc) {
        def rootNode = new UserGuideNode()
        processSection(yamlDoc, rootNode)
        return rootNode
    }

    private processSection(Map sections, UserGuideNode node) {
        if (sections.title) {
            node.title = sections.title
            sections = sections.clone()
            sections.remove("title")
        }

        for (s in sections) {
            def child = new UserGuideNode(parent: node, name: s.key, file: determineFilePath(s.key, node))
            node.children << child
            processSection(s.value, child)
        }
    }

    private processSection(String title, UserGuideNode node) {
        node.title = title
    }

    private determineFilePath(basename, parent) {
        // Traverse the parent nodes and build a list of the node names.
        // The names are stored in reverse order, so the immediate parent
        // node is first in the list and the root (named) node is last.
        // The real root node, doesn't have a name and isn't included.
        def pathElements = []
        def node = parent
        while (node.name) {
            pathElements << node.name
            node = node.parent
        }

        // First check whether the gdoc file exists in the root directory.
        def filePath = "${basename}$ext"
        if (resourceChecker.exists(filePath)) {
            return filePath
        }
        else if (pathElements) {
            // Now check whether it's in any sub-directories named after the
            // ancestor nodes. First we look in a directory with the same
            // name as the root (named) node, then in a sub-directory of
            // that folder named after the next parent, and so on. So if
            // pathElements is ["changelog", "whatsNew", "intro"], then we
            // check:
            //
            //    intro/$basename.gdoc
            //    intro/whatsNew/$basename.gdoc
            //    intro/whatsNew/changelog/$basename.gdoc
            //
            for (i in 1..pathElements.size()) {
                filePath = "${pathElements[-1..-i].join('/')}/${basename}$ext"
                if (resourceChecker.exists(filePath)) {
                    return filePath
                }
            }
        }

        return null
    }
}
