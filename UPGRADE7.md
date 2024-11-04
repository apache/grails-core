# Common 7.0 Upgrade Gotchas

Experienced while upgrading modules for Grails 7

- h2 2.x is stricter about reserved words
  - https://github.com/grails/gorm-hibernate5/pull/910/commits/c8de45df204966ccc228b46b94beeb2142ae0f59 
- GROOVY-10621 
  - Primitive booleans will no longer generate the form of isProperty & getProperty.  They will only generate isProperty()
- GROOVY-5169
  - Fields with a public modifier were not considered a property in groovy 3, but are now. 
- Jar artifacts produced by Grails Plugins will no longer have the suffix `-plain`
  - https://github.com/grails/grails-gradle-plugin/pull/347
