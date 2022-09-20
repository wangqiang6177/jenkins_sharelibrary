def info(message) {
    echo "INFO: ${message}"
}

def warning(message) {
    echo "WARNING: ${message}"
}

def error(message) {
    echo "ERROR: ${message}"
}

def addgitComment(status){
            echo "${status}"
            updateGitlabCommitStatus(name: 'build', state: "${status}")
            addGitLabMRComment comment: """
**PreCheck CI自动构建详情**\n
| 条目 | 值 |
| ------ | ------ |
| 结果 | ${currentBuild.currentResult?: 'Unknow'} |
| MR LastCommit | ${env.gitlabMergeRequestLastCommit} | 
| MR id | ${env.gitlabMergeRequestIid} |
| Message Title | ${env.gitlabMergeRequestTitle} |
| 构建任务ID | ${env.BUILD_NUMBER} ${env.gitlabMergeRequestAssignee} ${env.gitlabUserEmail} |
| 构建详情链接 | [${env.BUILD_URL}](${env.BUILD_URL})"""
}


// 编译打包
def mvnPackage(Dir) {
    out = sh(cd "${Dir}" && mvn clean package -DskipTests=true ")
    def commitTime = out.toString().trim()
    assert commitTime != 'null' || commitTime.trim() != ''
    return commitTime
}


def sonarScanner() {
    def sonarDir = tool name: 'scanner-docker', type: 'hudson.plugins.sonar.SonarRunnerInstallation'
    printMes("sonarqube scanner started. sonarHomeDir: ${sonarDir}", "info")
    withSonarQubeEnv(credentialsId: 'comp-sonar') {
        sh "${sonarDir}/bin/sonar-scanner \
           -Dsonar.projectKey=${projectName} \
           -Dsonar.projectName=${projectName} \
           -Dsonar.ws.timeout=60 \
           -Dsonar.sources=. \
           -Dsonar.sourceEncoding=UTF-8 \
           -Dsonar.java.binaries=. \
           -Dsonar.language=java \
           -Dsonar.java.source=1.8"
    }
    printMes("${projectName} scan success!", "info")
}