pipeline {
    agent any

    parameters {
        // General params
        choice(choices: ['both', 'qpc', 'dsc'], description: "Projects to run", name: 'projects')
        choice(choices: ['rhel8-os'], description: "Node OS", name: 'node_os')
        // Quipucords Params
        string(defaultValue: '0.9.1', description: "Quipucords Server Version", name: 'qpc_server_install_version')
        string(defaultValue: '0.9.1', description: "QPC CLI Version", name: 'qpc_cli_install_version')
        // Discovery Params
        string(defaultValue: '0.9.1', description: "Discovery Server Version", name: 'dsc_server_install_version')
        string(defaultValue: '0.9.1', description: "DSC CLI Version", name: 'dsc_cli_install_version')
    }

    stages {
        stage("Build Info") {
            steps {
                echo "Projects: ${params.projects}\nNode OS: ${params.node_os}\n\nQuipucords Server Version: ${params.qpc_server_install_version}\nQuipucords CLI Version: ${params.qpc_cli_install_version}\n\nDiscovery Server Version: ${params.dsc_server_install_version}\nDiscovery CLI Version: ${params.dsc_cli_install_version}"
            }
        }
        stage ('Run Tests') {
            parallel {
                stage ('Test Quipucords') {
                    when {
                        expression { params.projects == 'qpc' || params.projects == 'both' }
                    }//end when
                    steps {
                        build job: 'qpc-dsc-test-pipeline', propagate: false, parameters:[
                            string(name: 'project',value: "qpc"),
                            string(name: 'node_os',value: "${node_os}"),
                            string(name: 'server_install_version',value: "${qpc_server_install_version}"),
                            string(name: 'cli_install_version',value: "${qpc_cli_install_version}")
                        ]
                    }//end steps
                }//end stage
                stage ('Test Discovery') {
                    when {
                        expression { params.projects == 'dsc' || params.projects == 'both' }
                    }//end when
                    steps {
                        build job: 'qpc-dsc-test-pipeline2', propagate: false, parameters:[
                            string(name: 'project',value: "dsc"),
                            string(name: 'node_os',value: "${node_os}"),
                            string(name: 'server_install_version',value: "${dsc_server_install_version}"),
                            string(name: 'cli_install_version',value: "${dsc_cli_install_version}")
                        ]
                    }
                }
            }
        }
        stage ('Collect Tests') {
                parallel {
                stage ('Collect Quipucords Test Results') {
                    when {
                        expression { params.projects == 'qpc' || params.projects == 'both' }
                    }
                    steps {
                        copyArtifacts filter: 'qpc-*-junit.xml', fingerprintArtifacts: true, projectName: "qpc-dsc-test-pipeline", selector: lastCompleted()
                        archiveArtifacts "qpc-*-junit.xml"
                        junit "qpc-*-junit.xml"
                    }
                }
                stage ('Collect Discovery Test Results') {
                    when {
                        expression { params.projects == 'dsc' || params.projects == 'both' }
                    }
                    steps {
                        copyArtifacts filter: 'dsc-*-junit.xml', fingerprintArtifacts: true, projectName: "qpc-dsc-test-pipeline2", selector: lastCompleted()
                        archiveArtifacts "dsc-*-junit.xml"
                        junit "dsc-*-junit.xml"
                    }
                }
            }
        }
    }
}

