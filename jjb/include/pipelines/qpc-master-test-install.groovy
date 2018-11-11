def setupDocker() {
    echo 'Setup docker configuration'

    sh '''\
    echo "OPTIONS=--log-driver=journald" > docker.conf
    echo "DOCKER_CERT_PATH=/etc/docker" >> docker.conf
    sudo cp docker.conf /etc/sysconfig/docker
    '''.stripIndent()
}


def setupQPC() {
    echo 'Extract the installer script'
    sh 'tar -xvzf quipucords.install.tar.gz'

    echo 'Copy container to installer packages directory'
    sh '''\
    mkdir -p install/packages
    mv quipucords.0.0.*.tar.gz install/packages/
    '''.stripIndent()

    echo 'Install QPC using the install.sh script'
    dir('install') {
        sh 'sudo ./install.sh -e server_install_dir=${WORKSPACE}'
    }
}


def runInstallTests(distro) {
    if (distro ==~ /f\d\d/) {
        sh 'sudo dnf -y install python-pip'
    } else {
        sh 'sudo yum -y install python-pip'
    }

    if (distro == 'rhel6') {
        sh 'sudo pip install pexpect nose'

        sh '''\
        set +e
        nosetests --with-xunit --xunit-file=junit.xml ci/scripts/quipucords/master/install/test_install.py
        set -e
        '''.stripIndent()
    } else {
        sh 'sudo pip install -U pip'
        sh 'sudo pip install pexpect pytest'

        sh """\
        set +e
        pytest --junit-prefix ${distro} --junit-xml junit.xml ci/scripts/quipucords/master/install/test_install.py
        set -e
        """.stripIndent()
    }
}


stage('Test Install') {
    parallel 'CentOS 7 Install': {
        node('centos7-os') {
            copyArtifacts filter: 'quipucords.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-master', selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')

            dir('ci') {
                git 'https://github.com/quipucords/ci.git'
            }

            sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
                withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
                    withEnv(['DISTRO=centos7', 'RELEASE=master']) {
                        setupDocker()

                        setupQPC()

                        runInstallTests 'centos7'
                    }
                }
            }

            junit 'junit.xml'
        }
    }, 'Fedora 27': {
        node('f27-os') {
            copyArtifacts filter: 'quipucords.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-master', selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')

            dir('ci') {
                git 'https://github.com/quipucords/ci.git'
            }

            sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
                withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
                    withEnv(['DISTRO=f27', 'RELEASE=master']) {
                        setupDocker()

                        setupQPC()

                        runInstallTests 'f27'
                    }
                }
            }

            junit 'junit.xml'
        }
    }, 'Fedora 28': {
        node('f28-os') {
            copyArtifacts filter: 'quipucords.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-master', selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')

            dir('ci') {
                git 'https://github.com/quipucords/ci.git'
            }

            sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
                withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
                    withEnv(['DISTRO=f28', 'RELEASE=master']) {
                        setupDocker()

                        setupQPC()

                        runInstallTests 'f28'
                    }
                }
            }

            junit 'junit.xml'
        }
    }, 'RHEL6 Install': {
        node('rhel6-os') {
            copyArtifacts filter: 'quipucords.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-master', selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')

            dir('ci') {
                git 'https://github.com/quipucords/ci.git'
            }

            configFileProvider([configFile(fileId: '5b276700-674e-4a0f-af91-3e725ed7a311', targetLocation: 'rhel6-custom.repo')]) {
                sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
                    withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
                        withEnv(['DISTRO=rhel6', 'RELEASE=master']) {
                            setupDocker()

                            sh '''\
                            sudo cp ${WORKSPACE}/rhel6-custom.repo /etc/yum.repos.d/rhel6-rcm-internal.repo
                            sudo yum clean all
                            sudo yum-config-manager --disable base
                            sudo yum-config-manager --disable optional
                            '''.stripIndent()

                            setupQPC()

                            runInstallTests 'rhel6'
                        }
                    }
                }
            }

            junit 'junit.xml'
        }
    }, 'RHEL7 Install': {
        node('rhel7-os') {
            copyArtifacts filter: 'quipucords.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-master', selector: upstream(allowUpstreamDependencies: false, fallbackToLastSuccessful: true, upstreamFilterStrategy: 'UseGlobalSetting')

            dir('ci') {
                git 'https://github.com/quipucords/ci.git'
            }

            configFileProvider([configFile(fileId: '0f157b1a-7068-4c75-a672-3b1b90f97ddd', targetLocation: 'rhel7-custom.repo')]) {
                sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
                    withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
                        withEnv(['DISTRO=rhel7', 'RELEASE=master']) {
                            setupDocker()

                            sh 'sudo cp rhel7-custom.repo /etc/yum.repos.d/rhel7-rcm-internal.repo'

                            setupQPC()

                            runInstallTests 'rhel7'
                        }
                    }
                }
            }

            junit 'junit.xml'
        }
    }
}
