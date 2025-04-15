package grails.doc.internal

class LegacyTocStrategy {
    def generateToc(files) {
        // Compares two gdoc filenames based on the section number in the
        // form x.y.z...
        def sectionNumberComparator = [
                compare: {o1, o2 ->
                    def idx1 = o1.name[0..o1.name.indexOf(' ') - 1]
                    def idx2 = o2.name[0..o2.name.indexOf(' ') - 1]
                    def nums1 = idx1.split(/\./).findAll { it.trim() != ''}*.toInteger()
                    def nums2 = idx2.split(/\./).findAll { it.trim() != ''}*.toInteger()
                    // pad out with zeros to ensure accurate comparison
                    while (nums1.size() < nums2.size()) {
                        nums1 << 0
                    }
                    while (nums2.size() < nums1.size()) {
                        nums2 << 0
                    }
                    def result = 0
                    for (i in 0..<nums1.size()) {
                        result = nums1[i].compareTo(nums2[i])
                        if (result != 0) break
                    }
                    result
                },
                equals: { false }] as Comparator

        // Search the given directory for all gdoc files and order them based
        // on their section numbers.
        if(files) {
            Collections.sort files, sectionNumberComparator
        } else {
            files = []
        }

        // A tree of book sections, where 'book' is a list of the top-level
        // sections and each of those has a list of sub-sections and so on.
        def book = new UserGuideNode()
        for (f in files) {
            // Chapter is filename - '.gdoc' suffix.
            def chapter = f.name[0..-6]
            def section = new UserGuideNode(name: chapter, title: chapter, file: f.name)

            def level = 0
            def matcher = (chapter =~ /^(\S+?)\.?\s(.+)/) // drops last '.' of "xx.yy. "
            if (matcher) {
                level = matcher.group(1).split(/\./).size() - 1
                section.title = matcher.group(2)
            }

            // This cryptic line finds the appropriate parent section list based
            // on the current section's level. If the level is 0, then it's 'book'.
            def parent = (0..<level).inject(book) { node, n -> node.children[-1] }
            section.parent = parent
            parent.children << section
        }

        return book
    }
}
