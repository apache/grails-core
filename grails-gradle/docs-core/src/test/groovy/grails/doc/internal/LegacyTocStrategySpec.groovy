package grails.doc.internal

import spock.lang.Specification

class LegacyTocStrategySpec extends Specification {
    def "Test basic behaviour"() {
      given: "A legacy strategy"
        def strategy = new LegacyTocStrategy()

      when: "The TOC is generated for a list of filenames with section numbers"
        def toc = strategy.generateToc([
                [name: "1. Introduction.gdoc"],
                [name: "1.1.10 Web Features.gdoc"],
                [name: "1.1 What's new in Grails 2.0?.gdoc"],
                [name: "1.1.2. Core Features.gdoc"],
                [name: "1.2.1 Part One.gdoc"],
                [name: "2.2 Upgrading from previous versions of Grails.gdoc"],
                [name: "1.2. Breaking Changes.gdoc"],
                [name: "1.1.1 Development Environment Features.gdoc"],
                [name: "1.2.2. Part Two.gdoc"],
                [name: "2 Getting Started.gdoc"],
                [name: "2.1 Downloading and Installing.gdoc"],
                [name: "2.3. Creating an Application.gdoc"] ])

      then: "The correct UserGuideNode tree is created"
        toc.children?.size() == 2
        toc.children[0].name == "1. Introduction"
        toc.children[0].title == "Introduction"
        toc.children[0].file == "1. Introduction.gdoc"
        toc.children[0].parent == toc
        toc.children[0].children*.name == ["1.1 What's new in Grails 2.0?", "1.2. Breaking Changes"]
        toc.children[0].children*.title == ["What's new in Grails 2.0?", "Breaking Changes"]
        toc.children[0].children*.file == ["1.1 What's new in Grails 2.0?.gdoc", "1.2. Breaking Changes.gdoc"]
        toc.children[0].children[0].children[1].name == "1.1.2. Core Features"
        toc.children[0].children[0].children[1].title == "Core Features"
        toc.children[0].children[0].children[1].file == "1.1.2. Core Features.gdoc"
        toc.children[1].children*.name == [
                "2.1 Downloading and Installing",
                "2.2 Upgrading from previous versions of Grails",
                "2.3. Creating an Application"]
        toc.children[1].children*.title == [
                "Downloading and Installing",
                "Upgrading from previous versions of Grails",
                "Creating an Application"]
        toc.children[1].children*.file == [
                "2.1 Downloading and Installing.gdoc",
                "2.2 Upgrading from previous versions of Grails.gdoc",
                "2.3. Creating an Application.gdoc"]
        toc.children[1].children[1].parent == toc.children[1]
        !(toc.children[0].children[1].parent == toc)
    }
}
