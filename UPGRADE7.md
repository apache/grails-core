# Common 7.0 Upgrade Gotchas - DRAFT -

Experienced while upgrading modules for Grails 7

- h2 2.x is stricter about reserved words
  - https://github.com/grails/gorm-hibernate5/pull/910/commits/c8de45df204966ccc228b46b94beeb2142ae0f59 
- [GROOVY-10621](https://issues.apache.org/jira/browse/GROOVY-10621) 
  - Primitive booleans will no longer generate the form of isProperty & getProperty.  They will only generate isProperty()
- Jar artifacts produced by Grails Plugins will no longer have the suffix `-plain`
  - https://github.com/grails/grails-gradle-plugin/pull/347
- [GROOVY-5169](https://issues.apache.org/jira/browse/GROOVY-5169)  [GROOVY-10449](https://issues.apache.org/jira/browse/GROOVY-10449)
  - Fields with a public modifier were not returned with MetaClassImpl#getProperties() in groovy 3, but are now. 

## NOTE: This document is a draft and the explanations are only highlights and will be expanded further prior to release of 7.0.
