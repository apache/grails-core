The plugin index of Grails, in part, for the tests of the feature catalog
=========================================================================

`plugins-index.json` is a part of `grails-plugins-index.json` of
https://github.com/apache/grails-plugins-metadata at commit `b17759d` (the index then had 257 plugins,
5,081 versions and 638,899 bytes). It has 23 of those plugins, each as the index has it, byte for byte
what the index's own writer gives for them, with one change: `cloud.wondrify:asset-pipeline-grails` has
the first 210 of its 303 versions.

The plugins were picked for what the index has that a reader has to cope with:

- versions published under other coordinates and to another repository (`org.grails.plugins:grails-mail`,
  whose versions up to 4.0.2 are `org.grails.plugins:mail`; `cloud.wondrify:asset-pipeline-grails`;
  `org.grails.plugins:grails-elasticsearch`; `org.grails.plugins:grails-shiro`, `grails-web-console` and
  `audit-logging` with a repository of their own for a version);
- a plugin with more than 200 versions (`asset-pipeline-grails`), and ones with about a hundred;
- `grailsVersion` in the forms the index has: `7.0.0 > *`, `7.0.0  > *` with two spaces
  (`grails-datastore-rest-client-legacy`, `grails-elasticsearch`), `2.4 >= *` (`filterpane`),
  `3.1.* > *` (`spring-security-oauth2-github`), with an upper version (`1.2 > 1.3.7` in `grails-quartz`,
  `5.3.0 > 6.2.3` in `grails-elasticsearch`), with qualifiers, and missing;
- `artifactAvailable: false` (`grails-hibernate-filter` 0.2.0);
- `deprecated` with its reason (`schwartz`, `json-annotations-marshaller`, `aws-sdk-sqs`), `comment`,
  plugins without `desc`, `labels` or `docs`, the longest text of the index (`mailinglist`), and each
  of the repositories the index names.

`plugins-index-with-packs.json` is the same file with a `pack` member added to some versions, which the
index does not have yet: it is what the indexer is to write for a version whose jar has a feature pack.
The `sha256` of each pack is a number, `000...01` to `000...11`, in the place of a digest;
`PluginIndexCatalogSpec` publishes a jar for each and puts its digest there before the file is read.

Neither file is edited by hand: a script took the plugins from the index and wrote them as the index
writes them. To refresh them, take the same plugins from a newer index.
