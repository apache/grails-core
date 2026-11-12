/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
description("Creates a new Quartz scheduled job") {
    usage "grails create-job [JOB NAME]"
    argument name:'Job Name', description:"The name of the job"
}

model = model(trimTrailingJobFromJobName(args[0]) )
render  template:"Job.groovy",
        destination: file( "grails-app/jobs/$model.packagePath/${trimTrailingJobFromJobName(model.simpleName)}Job.groovy"),
        model: model

/**
 * //if 'Job' already exists in the end of JobName, then remove it from jobName.
 * @param name
 * @return
 */
String trimTrailingJobFromJobName(String name){
    String type = "Job"
    String processedName = name
    Integer lastIndexOfJOBInJobName = name.lastIndexOf(type)
    if(lastIndexOfJOBInJobName == (name.length() - type.length())){
        processedName = name.substring(0, lastIndexOfJOBInJobName)
    }
    return processedName
}