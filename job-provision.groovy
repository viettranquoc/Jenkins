import groovy.json.*
import jenkins.model.Jenkins

Jenkins jenkins = Jenkins.instance
def stages = [:]

stages['Code-review-application-maven'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "compile"},{"name": "spotbugs"},{"name": "checkstyle"},' +
        '{"name": "tests"}, {"name": "sonar"}]'
stages['Code-review-application-npm'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "build"},{"name": "tests"},{"name": "sonar"}]'
stages['Code-review-application-gradle' ] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "lint"},{"name": "compile"},{"name": "tests"},{"name": "sonar"}]'
stages['Code-review-application-dotnet'] = stages['Code-review-application-maven']
stages['Code-review-application-xcode'] = stages['Code-review-application-maven']
stages['Code-review-application-terraform'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "tool-init"},{"name": "lint"}]'
stages['Code-review-application-helm'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "lint"}]'
stages['Code-review-application-docker'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "lint"}]'
stages['Code-review-library'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "compile"},{"name": "tests"},' +
        '{"name": "sonar"}]'
stages['Code-review-library-maven'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "compile"},{"name": "tests"},' +
        '{"name": "sonar"}]'
stages['Code-review-autotests'] = '[{"name": "checkout"},{"name": "commit-validate"},{"name": "tests"},{"name": "sonar"}]'
stages['Build-library-maven'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build"},{"name": "push"},{"name": "git-tag"}]'
stages['Build-library-npm'] = stages['Build-library-maven']
stages['Build-library-gradle'] = stages['Build-library-maven']
stages['Build-library-dotnet'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "push"},{"name": "git-tag"}]'
stages['Build-application-maven'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},{"name": "spotbugs"},{"name": "checkstyle"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build"},{"name": "build-image-kaniko"},' +
        '{"name": "push"},{"name": "git-tag"}]'
stages['Build-application-xcode'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build"},' + '{"name": "push"},{"name": "git-tag"}]'
stages['Build-application-npm'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "build"},{"name": "tests"},{"name": "sonar"},{"name": "build-image-kaniko"},{"name": "push"},{"name": "git-tag"}]'
stages['Build-application-gradle'] = stages['Build-application-maven']
stages['Build-application-dotnet'] = '[{"name": "checkout"},{"name": "get-version"},{"name": "compile"},' +
        '{"name": "tests"},{"name": "sonar"},{"name": "build-image-kaniko"},' +
        '{"name": "push"},{"name": "git-tag"}]'
stages['Build-application-terraform'] = '[{"name": "checkout"},{"name": "tool-init"},' +
        '{"name": "lint"},{"name": "git-tag"}]'
stages['Build-application-helm'] = '[{"name": "checkout"},{"name": "lint"}]'
stages['Build-application-docker'] = '[{"name": "checkout"},{"name": "lint"}]'
stages['Create-release'] = '[{"name": "checkout"},{"name": "create-branch"},{"name": "trigger-job"}]'

def codebaseName = "${NAME}"
def buildTool = "${BUILD_TOOL}"
def gitServerCrName = "${GIT_SERVER_CR_NAME}"
def gitServerCrVersion = "${GIT_SERVER_CR_VERSION}"
def gitServer = "${GIT_SERVER ? GIT_SERVER : 'gerrit'}"
def gitSshPort = "${GIT_SSH_PORT ? GIT_SSH_PORT : '29418'}"
def gitUsername = "${GIT_USERNAME ? GIT_USERNAME : 'jenkins'}"
def gitCredentialsId = "${GIT_CREDENTIALS_ID ? GIT_CREDENTIALS_ID : 'gerrit-ciuser-sshkey'}"
def defaultRepoPath = "ssh://${gitUsername}@${gitServer}:${gitSshPort}/${codebaseName}"
def repositoryPath = "${REPOSITORY_PATH ? REPOSITORY_PATH : defaultRepoPath}"

def codebaseFolder = jenkins.getItem(codebaseName)
if (codebaseFolder == null) {
    folder(codebaseName)
}

createListView(codebaseName, "Releases")
createReleasePipeline("Create-release-${codebaseName}", codebaseName, stages["Create-release"], "create-release.groovy",
        repositoryPath, gitCredentialsId, gitServerCrName, gitServerCrVersion)

if (BRANCH == "master" && gitServerCrName != "gerrit") {
    def branch = "${BRANCH}"
    def formattedBranch = "${branch.toUpperCase().replaceAll(/\\//, "-")}"
    createListView(codebaseName, formattedBranch)

    def type = "${TYPE}"
    // createCiPipeline("Code-review-${codebaseName}", codebaseName, stages["Code-review-${type}-${buildTool.toLowerCase()}"], "code-review.groovy",
    //         repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion)
    createCiPipeline("FB-Code-review-${codebaseName}", codebaseName, stages["Code-review-${type}-${buildTool.toLowerCase()}"], "code-review.groovy",
        repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion)

    if (type.equalsIgnoreCase('application') || type.equalsIgnoreCase('library')) {
        def jobExists = false
        if("${formattedBranch}-Build-${codebaseName}".toString() in Jenkins.instance.getAllItems().collect{it.name}) {
           jobExists = true
        }
        createCiPipeline("${formattedBranch}-Build-${codebaseName}", codebaseName, stages["Build-${type}-${buildTool.toLowerCase()}"], "build.groovy",
                repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion)
        if(!jobExists) {
            queue("${codebaseName}/${formattedBranch}-Build-${codebaseName}")
        }
    }
    registerWebHook(repositoryPath)
    return
}

if (BRANCH) {
    def branch = "${BRANCH}"
    def formattedBranch = "${branch.toUpperCase().replaceAll(/\\//, "-")}"
    createListView(codebaseName, formattedBranch)

    def type = "${TYPE}"
    // createCiPipeline("Code-review-${codebaseName}", codebaseName, stages["Code-review-${type}-${buildTool.toLowerCase()}"], "code-review.groovy",
    //         repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion)
    createCiPipeline("FB-Code-review-${codebaseName}", codebaseName, stages["Code-review-${type}-${buildTool.toLowerCase()}"], "code-review.groovy",
        repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion)

    if (type.equalsIgnoreCase('application') || type.equalsIgnoreCase('library')) {
        def jobExists = false
        if("${formattedBranch}-Build-${codebaseName}".toString() in Jenkins.instance.getAllItems().collect{it.name}) {
           jobExists = true
        }
        createCiPipeline("${formattedBranch}-Build-${codebaseName}", codebaseName, stages["Build-${type}-${buildTool.toLowerCase()}"], "build.groovy",
                repositoryPath, gitCredentialsId, branch, gitServerCrName, gitServerCrVersion)
       if(!jobExists) {
         queue("${codebaseName}/${formattedBranch}-Build-${codebaseName}")
       }
    }
}


def createCiPipeline(pipelineName, codebaseName, codebaseStages, pipelineScript, repository, credId, watchBranch = "master", gitServerCrName, gitServerCrVersion) {
    // pipelineJob("${codebaseName}/${watchBranch.toUpperCase().replaceAll(/\\//, "-")}-${pipelineName}")
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(10)
            daysToKeep(7)
        }
        if(gitServerCrName == "gerrit") {
            triggers {
                gerrit {
                    events {
                        if (pipelineName.contains("Build"))
                            changeMerged()
                        else
                            patchsetCreated()
                    }
                    project("plain:${codebaseName}", ["plain:${watchBranch}"])
                }
            }
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repository)
                            credentials(credId)
                        }
                        if (pipelineName.contains("FB-Code-review"))
                            branches("\${BRANCH}")
                        else
                            branches("${watchBranch}")
                        scriptPath("${pipelineScript}")
                    }
                }
                parameters {
                    stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                    stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                    stringParam("STAGES", "${codebaseStages}", "Consequence of stages in JSON format to be run during execution")
                    stringParam("GERRIT_PROJECT_NAME", "${codebaseName}", "Gerrit project name(Codebase name) to be build")
                    stringParam("BRANCH", "${watchBranch}", "Branch to build artifact from")
                }
            }
        }
    }
}

def createReleasePipeline(pipelineName, codebaseName, codebaseStages, pipelineScript, repository, credId, gitServerCrName, gitServerCrVersion) {
    pipelineJob("${codebaseName}/${pipelineName}") {
        logRotator {
            numToKeep(14)
            daysToKeep(30)
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(repository)
                            credentials(credId)
                        }
                        branches("master")
                        scriptPath("${pipelineScript}")
                    }
                }
                parameters {
                    stringParam("STAGES", "${codebaseStages}", "")
                    if (pipelineName.contains("Create-release")) {
                        stringParam("GERRIT_PROJECT", "${codebaseName}", "")
                        stringParam("RELEASE_NAME", "", "Name of the release(branch to be created)")
                        stringParam("COMMIT_ID", "", "Commit ID that will be used to create branch from for new release. If empty, HEAD of master will be used")
                        stringParam("GIT_SERVER_CR_NAME", "${gitServerCrName}", "Name of Git Server CR to generate link to Git server")
                        stringParam("GIT_SERVER_CR_VERSION", "${gitServerCrVersion}", "Version of GitServer CR Resource")
                        stringParam("REPOSITORY_PATH", "${repository}", "Full repository path")
                    }
                }
            }
        }
    }
}

def createListView(codebaseName, branchName) {
    listView("${codebaseName}/${branchName}") {
        if (branchName.toLowerCase() == "releases") {
            jobFilters {
                regex {
                    matchType(MatchType.INCLUDE_MATCHED)
                    matchValue(RegexMatchValue.NAME)
                    regex("^Create-release.*")
                }
            }
        } else {
            jobFilters {
                regex {
                    matchType(MatchType.INCLUDE_MATCHED)
                    matchValue(RegexMatchValue.NAME)
                    regex("^${branchName}-(Code-review|Build).*")
                }
            }
        }
        columns {
            status()
            weather()
            name()
            lastSuccess()
            lastFailure()
            lastDuration()
            buildButton()
        }
    }
}

def registerWebHook(repositoryPath) {
    if(!Jenkins.getInstance().getItemByFullName("webhook-listener")) {
        println("Job \"webhook-listener\" doesn't exist. Webhook is not configured.")
        return
    }

    def apiUrl = 'https://' + repositoryPath.split('@')[1].replaceAll('/',"%2F").replace(':22%2F', '/api/v4/projects/') + '/hooks'
    def webhookListenerJob = Jenkins.getInstance().getItemByFullName("webhook-listener")
    def jobUrl = webhookListenerJob.getAbsoluteUrl().replace('/job/','/project/')
    def triggersMap = webhookListenerJob.getTriggers()

    triggersMap.each { key, value ->
        webhookSecretToken = value.getSecretToken()
    }

    def webhookConfig = [:]
    webhookConfig["url"]                        = jobUrl
    webhookConfig["push_events"]                = "true"
    webhookConfig["merge_requests_events"]      = "true"
    webhookConfig["enable_ssl_verification"]    = "true"
    webhookConfig["token"]                      = webhookSecretToken
    def requestBody = JsonOutput.toJson(webhookConfig)
    def http = new URL(apiUrl).openConnection() as HttpURLConnection
    http.setRequestMethod('POST')
    http.setDoOutput(true)
    println(apiUrl)
    http.setRequestProperty("Accept", 'application/json')
    http.setRequestProperty("Content-Type", 'application/json')
    http.setRequestProperty("Authorization", "Bearer ${getSecretValue('gitlab-api')}")
    http.outputStream.write(requestBody.getBytes("UTF-8"))
    http.connect()
    println(http.responseCode)

    if (http.responseCode == 201) {
        response = new JsonSlurper().parseText(http.inputStream.getText('UTF-8'))
    } else {
        response = new JsonSlurper().parseText(http.errorStream.getText('UTF-8'))
    }

    println "response: ${response}"
}

def getSecretValue(name) {
    def creds = com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
            com.cloudbees.plugins.credentials.common.StandardCredentials.class,
            Jenkins.instance,
            null,
            null
    ) 

    def secret = creds.find {it.properties['id'] == name}
  	
    return secret != null ? secret.getSecret() : null
}
