library('sharelibrary@master')
pipeline{
//在任何可用的代理上执行Pipeline
    agent any

    environment {
        //收件人信息
        //_receiverEamil="xxx01@qq.com,xxx02@qq.com"
        _receiverEamil="xxx01@qq.com,xxx02@qq.com"   
        //sonar属性参数
        _sonarProjectKey="mammoth-biz"
        _sonarExclusions="**/src/test/**"
        //部门简称，用于sonar搜索
        _departmentName="CFYFZX"
        //workspace目录下的pom路径，例如：server-api/
        _pomDir=""
        //jdk版本
        _jdkVersion="jdk1.8.0_77"
        //maven版本
        _mavenVersion="maven_3"
    }

    //声明使用的工具
    tools {
        jdk "${_jdkVersion}"
        maven "${_mavenVersion}"
    }
    //定义连接系统配置中的gitlab名称，为了在post阶段访问gitlab添加评论
    options {
        timestamps()
        gitLabConnection('gitlab')
    }
    stages{
        stage ('代码克隆') {
            steps{
                //使用Merge before build功能，实现PreCheck CI流程
                script{
                    echo "git clone"
                    try {
                    checkout([$class: 'GitSCM', branches: [[name: '*/$gitlabSourceBranch']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'PreBuildMerge', options: [mergeRemote: 'origin', mergeTarget: '${gitlabTargetBranch}']]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '7c0324ba-3771-4c1a-9957-bd67e68c4c13', url: '$gitlabSourceRepoHttpUrl']]])
                    } catch (exc) {
                        currentBuild.result = "FAILURE"    
                        emailext body: '''${FILE, path="/data/jenkins/email-templates/PreMerge_fail_mail_template.html"}''', 
                        subject: '❌[Jenkins Build Notification] ${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}!', 
                        to: "${_receiverEamil}"
                        error "代码预合并报错"
                    }
			    }
                script{
                    env.GIT_COMMIT_MSG = sh (script:'git log -1 --pretty=%B ${GIT_COMMIT}',returnStdout: true).trim()
                }
                script{
				//完整commit id
				env.GIT_COMMIT_ID = sh (script: 'git rev-parse HEAD ${GIT_COMMIT}',returnStdout: true).trim()
				echo "commit_id:$GIT_COMMIT_ID"
				}
                }
        }
        stage('编译打包'){
            steps{
                script{
                    echo "Building..."
                    try {
                    sh '''cd ${WORKSPACE}/${_pomDir} && mvn clean package -DskipTests=true'''
                    } catch (exc) {
                        currentBuild.result = "FAILURE"    
                        emailext body: '''${FILE, path="/data/jenkins/email-templates/Build_fail_mail_template.html"}''', 
                        subject: '❌[Jenkins Build Notification] ${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}!', 
                        to: "${_receiverEamil}"
                        error "编译打包报错"
                    }
			    }
            }
        }
        stage('单元测试'){
            steps{
                script{
                    echo "Testing..."
                    try {
                    //先执行输出单元测试报告，再执行输出单元测试覆盖率报告
                    sh '''cd ${WORKSPACE}/${_pomDir} && mvn org.jacoco:jacoco-maven-plugin:prepare-agent surefire-report:report
                mvn org.jacoco:jacoco-maven-plugin:report'''
                    } catch (exc) {
                        currentBuild.result = "FAILURE"    
                        emailext body: '''${FILE, path="/data/jenkins/email-templates/Test_fail_mail_template.html"}''', 
                        subject: '❌[Jenkins Build Notification] ${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}!', 
                        to: "${_receiverEamil}"
                        error "单元测试报错"
                    }
			    }
            }
        }
        stage ('静态代码检测') {
            steps{
				//sonar8.9指的是jenkins系统配置SonarQube Severs的名称
                withSonarQubeEnv('sonar8.9'){
                    sh '''cd ${WORKSPACE}/${_pomDir} && mvn sonar:sonar \
                    -Dsonar.projectKey=${_sonarProjectKey} \
                    -Dsonar.projectName=${_departmentName}_${JOB_NAME}_$BUILD_NUMBER \
		            -Dsonar.projectDescription=$gitlabSourceRepoHttpUrl \
                    -Dsonar.projectVersion=1.0 \
                    -Dsonar.sourceEncoding=UTF-8 \
                    -Dsonar.exclusions=${_sonarExclusions} '''
                    //sh ''' cd ${WORKSPACE} && mvn sonar:sonar'''
                }
            }
        }
        stage("Quality Gate") {
            steps{
                timeout(time: 5, unit: 'MINUTES') { 
                    script{
                        sleep(5)
                        def qg = waitForQualityGate() 
                        if (qg.status != 'OK') {
                            currentBuild.result = "FAILURE"  
                            emailext body: '''${FILE, path="/data/jenkins/email-templates/Quality_fail_mail_template.html"}''', 
                            subject: '❌[Jenkins Build Notification] ${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}!', 
                            to: "${_receiverEamil}"
                            error "未通过Sonarqube的代码质量阈检查，请及时修改！failure: ${qg.status}"
                        }
                    }
                }
            }
        }
    }


    post { 
    		always{
    			//总是打印此消息
    			echo '执行完成'
    		}
    		success {
    			//当此Pipeline成功时打印消息
    			echo 'success'
    			emailext body: '''${FILE, path="/data/jenkins/email-templates/mail_template.html"}''', 
    			subject: '✅[Jenkins Build Notification] ${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}!', 
    			to: "${_receiverEamil}"
                //调用共享库函数
                script{
                    log.addgitComment("success")
                }
    		}   
    		failure {
    			//当此Pipeline失败时打印消息
    			echo 'failure'
                //调用共享库函数 
                script{
                    log.addgitComment("failed")
                }
    		}
    		unstable {
    			//当此Pipeline 为不稳定时打印消息
    			echo 'unstable'		
    		}
    		aborted {
    			//当此Pipeline 终止时打印消息
    			echo 'aborted'	
                updateGitlabCommitStatus(name: 'build', state: 'failed')
    		}
    		changed {
    			//当pipeline的状态与上一次build状态不同时打印消息
    			echo 'changed'			
    		}        
    	}
}
