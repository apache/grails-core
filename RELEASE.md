# Grails Core Release Process

1. Perform the release of any other dependent library and update the version in `gradle.properties`/`grails-bom/plugins.properties`
1. Ensure you have the latest changes locally `git pull`
1. Ensure all changes from previous branches are merged up `git merge ...`
1. Ensure there are no snapshot dependencies 
1. Ensure the latest build passed and the latest grails3-functional-tests build passed
1. Set the version to a release version in `build.gradle`
1. Set the version in `grails-core/src/test/groovy/grails/util/GrailsUtilTests.java`
1. Commit the release `git commit -a -m "Release Grails XXX"`
1. Tag the release `git tag vXXX` (Don't forget the `v` prefix!)
1. Push the tag `git push --tags` and waiting for Travis to complete the tagged release https://travis-ci.org/grails/grails-core
1. Verify the release worked 
 * Run `sdk install grails XXX` and perform smoke tests or creating an application etc.
 * Check the documentation published to docs.grails.org/XXX
1. Run the maven central sync `./gradlew sWMC`. Requires BINTRAY_USER/BINTRAY_KEY env vars or bintrayUser/bintrayKey gradle properties
1. Ensure grails.org shows the new release version
1. Ensure the documentation published correctly docs.grails.org
1. Create a release in Github. Copy the previous release and change the relevant info
1. Change the version in `build.gradle` back to a snapshot of next release
1. Push the code `git push` 
1. Announce the Release
