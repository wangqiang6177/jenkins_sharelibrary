// 获取时间 格式：20201208200419
def getTime() {
    return new Date().format('yyyyMMddHHmmss')
}

//格式化输出，需安装ansiColor插件
def printMes(value,level){
    colors = ['warning' : "\033[43;30m ==> ${value} \033[0m",
              'info'    : "\033[42;30m ==> ${value} \033[0m",
              'error'   : "\033[41;37m ==> ${value} \033[0m",
    ]
    ansiColor('xterm') {
        println(colors[level])
    }
}

// 远程主机上通过ssh执行命令
def runCmd(ip, user, pass, command, sshArgs = '') {
    return sh(returnStdout: true,
            script: "sshpass -p ${pass} ssh ${sshArgs} -oStrictHostKeyChecking=no -l ${user} ${ip} \"${command}\"")
}

// 格式化输出当前构建分支用于镜像tag
def getBranch() {
    def formatBranch = "${env.GIT_BRANCH}".replaceAll('origin/', '').replaceAll('/', '_')
    assert formatBranch != 'null' || formatBranch.trim() != ''
    return formatBranch
}

// 获取分支commitId
def getSHA() {
    def commitsha = "${env.GIT_COMMIT}".toString().substring(0, 6)
    assert commitsha != 'null' || commitsha.trim() != ''
    return commitsha
}

// 获取commit时间
def getCommitTime() {
    out = sh(returnStdout: true, script: "git show -s --format=%ct ${env.GIT_COMMIT}")
    def commitTime = out.toString().trim()
    assert commitTime != 'null' || commitTime.trim() != ''
    return commitTime
}

//获取git commit变更集
def getChangeString() {
    def result = []
    def changeString = []
    def authors = []
    def MAX_MSG_LEN = 20
    def changeLogSets = currentBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            truncatedMsg = entry.msg.take(MAX_MSG_LEN)
            commitTime = new Date(entry.timestamp).format("yyyy-MM-dd HH:mm:ss")
            changeString << "${truncatedMsg} [${entry.author} ${commitTime}]\n"
//            changeString += ">- ${truncatedMsg} [${entry.author} ${commitTime}]\n"
            authors << "${entry.author} "
        }
    }
    if (!changeString) {
        changeString = ">- No new changes"
        authors = "No new changes, No authors"
        result << changeString << authors
        return result
    } else {
        if (changeString.size() >5) {
            changeString = changeString[0,4]
            changeString.add("......")
        }
        changeString = ">-" + changeString.join(">-")
        authors.join(", ")
        result << changeString << authors.unique()
        return result
    }
}

// java项目sonar扫描
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

// 发送钉钉消息，按需自定义，需要安装httpRequest插件
def dingMes(){
    def user = ''
    def description = ''
    def specificCause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
    if (specificCause) {
        user = "$specificCause.userName"
        description = "$specificCause.shortDescription"
    }
    def DingTalkHook = "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxx"
    def ChangeStrings = getChangeString()[0]
    def ChangeAuthors = getChangeString()[1]
    def ReqBody = """{
            "msgtype": "markdown",
            "markdown": {
                "title": "项目构建信息",
                "text": "### [${JOB_NAME}](${BUILD_URL})\\n---\\n>- 分支：**${env.GIT_BRANCH}**\\n> - 执行人： **${user}**\\n>- 描述： ${description}\\n>#### 作者：\\n>- **${ChangeAuthors}**\\n>#### 更新记录: \\n${ChangeStrings}"
            },
            "at": {
                "atMobiles": [], 
                "isAtAll": false
                }
            }"""
    httpRequest acceptType: 'APPLICATION_JSON_UTF8',
            consoleLogResponseBody: true,
            contentType: 'APPLICATION_JSON_UTF8',
            httpMode: 'POST',
            ignoreSslErrors: true,
            requestBody: ReqBody,
            responseHandle: 'NONE',
            url: "${DingTalkHook}",
            timeout: 5,
            quiet: true
}

//添加git评论
def addgitComment(){
            addGitLabMRComment comment: """**PreCheck CI自动构建详情**\n
            | 条目 | 值 |
            | ------ | ------ |
            | 结果 | ${currentBuild.currentResult?: 'Unknow'} |
            | MR LastCommit | ${env.gitlabMergeRequestLastCommit} | 
            | MR id | ${env.gitlabMergeRequestIid} |
            | Message Title | ${env.gitlabMergeRequestTitle} |
            | 构建任务ID | ${env.BUILD_NUMBER} ${env.gitlabMergeRequestAssignee} ${env.gitlabUserEmail} |
            | 构建详情链接 | [${env.BUILD_URL}](${env.BUILD_URL})"""
}

//邮件通知
def emailSuccess(){
    emailext(
        subject: "✅${env.JOB_NAME} - 更新成功",
        body: '${SCRIPT, template="email-html.template"}',
        recipientProviders: [requestor(), developers()]
    )
}

def emailFailure(){
    emailext(
        subject: "❌${env.JOB_NAME} - 更新失败",
        body: '${SCRIPT, template="email-html.template"}',
        recipientProviders: [requestor(), developers()]
    )
}