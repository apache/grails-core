description("Creates a new command for the profile") {
  usage "grails create-command [COMMAND NAME]"
  argument name:'Command Name', description:"The name of the command", required:true
  flag name:'force', description:"Whether to overwrite existing files"
}

if (args) {
	def scriptName = GrailsNameUtils.getClassNameForLowerCaseHyphenSeparatedName(args[0])
	def model = model(scriptName)
    render template: 'commands/Command.groovy',
		   destination: file("commands/${model.lowerCaseName}.groovy"),
		   model: model,
		   overwrite: flag('force')

}
else {
    error "No command name specified"
}
