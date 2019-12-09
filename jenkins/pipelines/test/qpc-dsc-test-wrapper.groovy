pipeline {
    agent any

    parameters {
        choice(choices: ['rhel8-os'], description: "Node OS", name: 'node_os')
        //choice(choices: ['branch', 'tag'], description: 'Branch or Tag?', name: 'version_type')
        string(defaultValue: '0.9.1', description: "Quipucords Server Version", name: 'qpc_server_install_version')
        string(defaultValue: '0.9.1', description: "QPC CLI Version", name: 'qpc_cli_install_version')
        string(defaultValue: '0.9.1', description: "Discovery Server Version", name: 'dsc_server_install_version')
        string(defaultValue: '0.9.1', description: "DSC CLI Version", name: 'dsc_cli_install_version')
    }

    stages {
        stage("Build Info") {
            steps {
                echo "Version: ${params.version_name}\nVersion Type: ${params.version_type}"
            }
        }
        stage ('Run Tests') {
            parallel {
                stage ('Test Quipucords') {
                    steps {
                        build job: 'qpc-dsc-test-pipeline', propagate: false, parameters:[
                            string(name: 'project',value: "qpc"),
                            string(name: 'node_os',value: "${node_os}"),
                            //string(name: 'version_name',value: "${version_name}"),
                            //string(name: 'version_type',value: "${version_type}")
                            string(name: 'server_install_version',value: "${qpc_server_install_version}"),
                            string(name: 'cli_install_version',value: "${qpc_cli_install_version}")
                        ]
                    }
                }
                stage ('Test Discovery') {
                    steps {
                        build job: 'qpc-dsc-test-pipeline2', propagate: false, parameters:[
                            string(name: 'project',value: "dsc"),
                            string(name: 'node_os',value: "${node_os}"),
                            //string(name: 'version_type',value: "${version_type}")
                            //string(name: 'version_type',value: "${version_type}")
                            string(name: 'server_install_version',value: "${dsc_server_install_version}"),
                            string(name: 'cli_install_version',value: "${dsc_cli_install_version}")
                        ]
                    }
                }
            }
        }
    }
}

