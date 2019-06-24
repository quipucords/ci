pipeline {
    agent any

    parameters {
        choice(choices: ['master', '0.9.0'], description: 'What branch?', name: 'version_name')
        choice(choices: ['branch', 'tag'], description: 'Branch or Tag?', name: 'version_type')
    }

    stages {
        stage("Build Info") {
            steps {
                echo "Version: ${params.version_name}\nVersion Type: ${params.version_type}"
            }
        }
        stage ('Build UI') {
            steps {
                build job: 'quipucords-ui-build-job-test' , parameters:[
                    string(name: 'version_name',value: "${version_name}"),
                    string(name: 'version_type',value: "${version_type}")
                ]
            }
        }
        stage ('Build Server') {
            steps {
                build job: 'quipucords-build-job-test' , parameters:[
                    string(name: 'version_name',value: "${version_name}"),
                    string(name: 'version_type',value: "${version_type}")
                ]
            }
        }
        }
    }

