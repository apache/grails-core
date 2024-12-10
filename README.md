# Grails Fields plugin

The grails fields plugin simplifies working with forms in Grails by offering convenient and customizable ways to manage
field rendering, validation, and interactions. It provides an easy-to-use API that reduces the boilerplate code for
generating form fields, allowing developers to focus on building their applications rather than managing repetitive UI
logic.

## Documentation 
For further information please see the full [Grails fields plugin documentation](https://gpc.github.io/fields).

## Tags and branches: 
- `6.0.x` latest version for Grails 7.x `implementation "org.grails.plugins:fields:7.0.0"`
- `5.1.0` latest version for Grails 6.x `implementation "org.grails.plugins:fields:5.1.1"`
- `5.0.3` latest version for Grails 5.x `implementation "org.grails.plugins:fields:5.0.3"`
- `4.0.1` latest version for Grails 4.x `compile "io.github.gpc:fields:4.0.0"` (no support)
- `3.0.0.RC1` last version on coordinates: `org.grails.plugins:fields:3.0.0.RC` - Grails 4.x (no support)
- `2.2.x` and `2.1.x` for Grails 3. (no support)
- `grails2.x` for Grails 2. (no support)


>[!NOTE] 
>If you use `org.grails.plugins:scaffolding` version `4.10.0` or less you need to exclude the original `org.grails.plugins:fields:3.3.0.RC1` like this:
>```
>implementation("org.grails.plugins:scaffolding") {
>  exclude module: 'fields'
>}
>implementation 'io.github.gpc:fields:5.0.0'
>```