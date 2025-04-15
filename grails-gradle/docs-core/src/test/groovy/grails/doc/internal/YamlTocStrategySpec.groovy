package grails.doc.internal

import spock.lang.Specification

class YamlTocStrategySpec extends Specification {
    def "Test basic behaviour"() {
      given: "A YAML loader"
        def loader = new YamlTocStrategy(new MockResourceChecker([
                "intro.gdoc",
                "intro/whatsNew.gdoc",
                "intro/whatsNew/devEnvFeatures.gdoc",
                "intro/whatsNew/coreFeatures.gdoc",
                "intro/whatsNew/webFeatures.gdoc",
                "intro/changes.gdoc",
                "intro/partOne.gdoc",
                "intro/partTwo.gdoc",
                "gettingStarted.gdoc",
                "downloading.gdoc",
                "upgrading.gdoc",
                "creatingApp.gdoc"]))

      when: "A test YAML document is loaded"
        def toc = loader.load("""\
                intro:
                  title: Introduction
                  whatsNew:
                    title: What's new in Grails 2.0?
                    devEnvFeatures: Development Environment Features
                    coreFeatures: Core Features
                    webFeatures: Web Features
                  changes:
                    title: Breaking Changes
                    partOne: Part One
                    partTwo: Part Two
                gettingStarted:
                  title: Getting Started
                  downloading: Downloading and Installing
                  upgrading:
                    title: Upgrading from previous versions of Grails
                  creatingApp: Creating an Application
                """.stripIndent())

      then: "The correct UserGuideNode tree is created"
        toc.children?.size() == 2
        toc.children[0].name == "intro"
        toc.children[0].title == "Introduction"
        toc.children[0].file == "intro.gdoc"
        toc.children[0].parent == toc
        toc.children[0].children*.name == ["whatsNew", "changes"]
        toc.children[0].children*.title == ["What's new in Grails 2.0?", "Breaking Changes"]
        toc.children[0].children*.file == ["intro/whatsNew.gdoc", "intro/changes.gdoc"]
        toc.children[0].children[0].children[1].name == "coreFeatures"
        toc.children[0].children[0].children[1].title == "Core Features"
        toc.children[0].children[0].children[1].file == "intro/whatsNew/coreFeatures.gdoc"
        toc.children[1].children*.name == ["downloading", "upgrading", "creatingApp"]
        toc.children[1].children*.title == [
                "Downloading and Installing",
                "Upgrading from previous versions of Grails",
                "Creating an Application"]
        toc.children[1].children*.file == ["downloading.gdoc", "upgrading.gdoc", "creatingApp.gdoc"]
        toc.children[1].children[1].parent == toc.children[1]
        !(toc.children[0].children[1].parent == toc)
    }
}

class MockResourceChecker {
    private Set resources

    MockResourceChecker(availableResources) {
        resources = new HashSet(availableResources)
    }

    boolean exists(String path) {
        resources.contains(path)
    }
}
