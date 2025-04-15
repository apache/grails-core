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

