// {release} {install_type}

def qpc_version = "" // Better way for this?
if ('{release}' == 'master') {{
    qpc_version = "0.0.47"
}} else {{
    qpc_version = "{release}"
}}


def image_name = "quipucords:${{qpc_version}}"
def tarfile = "quipucords.${{qpc_version}}.tar"
def targzfile = "${{tarfile}}.gz"
def install_tar = "quipucords.install.tar"
def install_targzfile = "${{install_tar}}.gz"

def getQPCVersion() {{
    if ('{release}' == 'master') {{
        return "0.0.47"
    }} else {{
        return "{release}"
    }}
}}

def setupDocker() {{
    echo 'Setup docker configuration'
    sh '''\
    echo "OPTIONS=--log-driver=journald" > docker.conf
    echo "DOCKER_CERT_PATH=/etc/docker" >> docker.conf
    sudo cp docker.conf /etc/sysconfig/docker
    '''.stripIndent()
}}

def startQPCServer = {{
    sh """\
    echo 'startQPCServer'
    pwd
    ls -lah
    sudo docker run --name qpc-db -e POSTGRES_PASSWORD=password -d postgres:9.6.10
    sudo docker run -d -p "9443:443" --link qpc-db:qpc-link \\
        -e QPC_DBMS_HOST=qpc-db \\
        -e QPC_DBMS_PASSWORD=password \\
        -v /tmp:/tmp \
        -v /home/jenkins/.ssh:/home/jenkins/.ssh \\
        -v \${{PWD}}/log:/var/log \\
        -i ${{image_name}}
    """.stripIndent()

    sh '''\
    for i in {{1..30}}; do
        SERVER_ID="$(curl -ks https://localhost:9443/api/v1/status/ | grep server_id || true)"

        if [ "${{SERVER_ID}}" ]; then
            break
        fi

        if [ $i -eq 30 ]; then
            echo "Server took too long to start"
            exit 1
        fi

        sleep 1
    done
    '''.stripIndent()
}}


def configureDocker = {{
    sh """\
    echo "OPTIONS=--log-driver=journald" > docker.conf
    echo "DOCKER_CERT_PATH=/etc/docker" >> docker.conf
    echo "INSECURE_REGISTRY=\\"--insecure-registry \${{DOCKER_REGISTRY}}\\"" >> docker.conf
    sudo cp docker.conf /etc/sysconfig/docker
    sudo systemctl start docker
    ls -lah
    pwd
    ls -lah ..
    ls -lah install/
    sudo docker load -i quipucords.${{qpc_version}}.tar.gz
    # make log dir to save server logs
    mkdir -p log
    """.stripIndent()
}}

def installQPCDefault() {{
    echo "Execute install.sh to install"
    dir("${{WORKSPACE}}/install") {{
        sh 'pwd'
        sh 'ls -l'
        sh 'sudo ./install.sh -e server_install_dir=${{WORKSPACE}}'
    }}
}}

def installQpcClient() {{
    sh '''\
    sudo wget -O /etc/yum.repos.d/group_quipucords-qpc-fedora-28.repo https://copr.fedorainfracloud.org/coprs/g/quipucords/qpc/repo/fedora-28/group_quipucords-qpc-fedora28.repo
    sudo dnf -y install qpc
    '''.stripIndent()
}}

def installQPCNoSupervisorD() {{
    echo "Execute install.sh to install without supervisord"
    dir("${{WORKSPACE}}/install") {{
        sh 'pwd'
        sh 'ls -l'
        sh 'sudo ./install.sh -e server_install_dir=${{WORKSPACE}} -e use_supervisord=false'

        // Docker log to check for supervisord
        sh 'sudo docker ps -a'
        sh 'sudo docker logs quipucords | grep -i "Running without supervisord"'
    }}
}}

def getQuipucords() {{
    // Grabs the quipucords build
    echo "Copying Latest build artifact..."
    copyArtifacts filter: 'quipucords.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-testing-build', selector: lastCompleted()
    copyArtifacts filter: 'quipucords.*.install.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-testing-build', selector: lastCompleted()
    copyArtifacts filter: 'postgres.*.tar.gz', fingerprintArtifacts: true, projectName: 'qpc-testing-build', selector: lastCompleted()
}}

def getMasterQPC() {{
    // Grabs the master qpc builds and sets it up
    def qpc_version = getQPCVersion()
    getQuipucords()

    sh 'ls -lah'
    echo 'Extract the installer script'
    sh """\
    echo ${{qpc_version}}
    tar -xvzf quipucords.${{qpc_version}}.install.tar.gz
    """.stripIndent()

    echo 'Copy container to installer packages directory'
    sh '''\
    mkdir -p install/packages
    '''.stripIndent()

}}

def getReleasedQPC() {{
    echo 'Get released QPC'
    // Clean up QPC package if here...
    sh '''\
    ls -l
    if [ -f quipucords.*tar.gz ]; then
        rm quipucords.*.tar.gz
    fi
    '''.stripIndent()

    // Pulls down Released Container
    echo "load docker container from tarball"
    sh "curl -k -O -sSL https://github.com/quipucords/quipucords/releases/download/{release}/quipucords.{release}.tar.gz"
    // Pulls down Released Install
    sh "curl -k -O -sSL https://github.com/quipucords/quipucords/releases/download/{release}/quipucords.{release}.install.tar.gz"

    echo "extract the installer into ${{WORKSPACE}}/install"

    sh "tar -xvzf ${{WORKSPACE}}/quipucords.{release}.install.tar.gz"

    sh "ls -lah"
    sh "ls -lah install/"

}}

def setupQPC() {{
    // get QPC
    if ('{release}' == 'master') {{
        echo "in setup master conditional"
        getMasterQPC()
    }} else {{
        echo "in setup release conditional"
        getReleasedQPC()
    }}
}}

def installQPC() {{
    // Install QPC
    if ('{install_type}' == 'nosupervisord') {{
        installQPCNoSupervisorD()
    }} else {{
        installQPC()
    }}
}}

def runInstallTests(distro) {{
    if (distro ==~ /f\d\d/) {{
        sh 'sudo dnf -y install python-pip'
    }} else {{
        sh 'sudo yum -y install python-pip'
    }}

    if (distro == 'rhel6') {{
        sh 'sudo pip install pexpect nose'

        sh '''\
        set +e
        nosetests --with-xunit --xunit-file=junit.xml ci/scripts/quipucords/master/install/test_install.py
        set -e
        '''.stripIndent()
    }} else {{
        sh 'sudo pip install -U pip'
        sh 'sudo pip install pexpect pytest'

        sh """\
        set +e
        pytest --junit-prefix ${{distro}} --junit-xml junit.xml ci/scripts/quipucords/master/install/test_install.py
        set -e
        """.stripIndent()
    }}
}}

def setupScanUsers() {{
    dir('ci') {{
        git 'https://github.com/quipucords/ci.git'
    }}

    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
        withCredentials([file(credentialsId: '50dc19ce-555f-422c-af38-3b5ede422bb4', variable: 'ID_JENKINS_RSA_PUB')]) {{
            sh 'sudo dnf -y install ansible'

            sh '''\
            cat > jenkins-slave-hosts <<EOF
            [jenkins-slave]
            ${{OPENSTACK_PUBLIC_IP}}

            [jenkins-slave:vars]
            ansible_user=jenkins
            ansible_ssh_extra_args=-o StrictHostKeyChecking=no
            ssh_public_key_file=$(cat ${{ID_JENKINS_RSA_PUB}})
            EOF
            '''.stripIndent()

            sh 'ansible-playbook -b -i jenkins-slave-hosts ci/ansible/sonar-setup-scan-users.yaml'
        }}
    }}
}}


def setupCamayoc() {{
    dir('camayoc') {{
        git 'https://github.com/quipucords/camayoc.git'
    }}

    sh '''\
    sudo pip install ./camayoc[dev]
    cp camayoc/pytest.ini .
    '''.stripIndent()

    withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {{
        sh '''\
        mkdir -p /home/jenkins/.ssh
        cp "${{ID_JENKINS_RSA}}" /home/jenkins/.ssh/id_rsa
        chmod 0600 /home/jenkins/.ssh/id_rsa
        '''.stripIndent()
    }}

    configFileProvider([configFile(fileId: '62cf0ccc-220e-4177-9eab-f39701bff8d7', targetLocation: 'camayoc/config.yaml')]) {{
        sh '''\
        sed -i "s/{{jenkins_slave_ip}}/${{OPENSTACK_PUBLIC_IP}}/" camayoc/config.yaml
        echo 'post-sed'
        '''.stripIndent()

    }}
}}


def runCamayocTest(testset) {{
    echo 'Fedora 28: Test '
    echo testset

    sh 'cat camayoc/config.yaml'
    sh 'ls -lah'
    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
        sh """
        export XDG_CONFIG_HOME=\$(pwd)

        set +e
        py.test -c pytest.ini -l -ra -s -vvv --junit-xml $testset-junit.xml --rootdir camayoc/camayoc/tests/qpc camayoc/camayoc/tests/qpc/$testset
        set -e

        sudo docker rm \$(sudo docker stop \$(sudo docker ps -aq))
        tar -cvzf test-$testset-logs.tar.gz log
        sudo rm -rf log
        """.stripIndent()
    }}
    echo 'pre archive artifacts'
    archiveArtifacts "test-$testset-logs.tar.gz"

    echo "pre $testset-junit"
    junit "$testset-junit.xml"
}}

def runCamayocUITest(browser) {{
    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
        sh "sudo docker run --net='host' -d -p 4444:4444 -v /dev/shm:/dev/shm:z -v /tmp:/tmp:z selenium/standalone-$browser"

        sleep 3

        sh """\
        export XDG_CONFIG_HOME=\$PWD
        export SELENIUM_DRIVER=$browser

        set +e
        py.test -c pytest.ini -l -ra -vvv --junit-prefix $browser --junit-xml ui-$browser-junit.xml --rootdir camayoc/camayoc/tests/qpc camayoc/camayoc/tests/qpc/ui
        set -e

        sudo docker rm \$(sudo docker stop \$(sudo docker ps -aq))
        tar -cvzf test-ui-$browser-logs.tar.gz log
        sudo rm -rf log
        """.stripIndent()
    }}

    archiveArtifacts "test-ui-$browser-logs.tar.gz"
    junit "ui-$browser-junit.xml"
}}

stage('Run Tests') {{
    parallel 'CentOS 7 Install': {{
        node('centos7-os') {{
            stage('Centos7 Install') {{
                dir('ci') {{
                    git 'https://github.com/quipucords/ci.git'
                }}

                sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
                    withCredentials([file(credentialsId:
                    '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {{
                        withEnv(['DISTRO=centos7', 'RELEASE={release}']) {{
//                            setupDocker()

                            sh """\
                            echo 'Testing qpc_version variable'
                            echo ${{qpc_version}}
                            """.stripIndent()
                            echo "Testing inline qpc_version variable: ${{qpc_version}}"
                            setupQPC()

                            runInstallTests 'centos7'
                        }}
                    }}
                }}
            }}
            junit 'junit.xml'
        }}
    }},


    'Fedora 28': {{
        node('f28-os') {{
            stage('Fedora 28 Install') {{
                dir('ci') {{
                    git 'https://github.com/quipucords/ci.git'
                }}

                sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
                    withCredentials([file(credentialsId:
                    '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {{
                        withEnv(['DISTRO=f28', 'RELEASE={release}']) {{
//                            setupDocker()
                            sh """\
                            echo 'Testing qpc_version variable'
                            echo ${{qpc_version}}
                            """.stripIndent()
                            echo "Testing inline qpc_version variable: ${{qpc_version}}"
                            setupQPC()

//                            runInstallTests 'f28'
                        }}
                    }}
                }}
            }}

            stage('F28: Setup Integration Tests') {{
                echo 'Fedora 28: Configure Docker'
                configureDocker()
                echo 'Fedora 28: Install QPC Client'
                installQpcClient()
                echo 'Fedora 28: Setup Scan Users'
                setupScanUsers()
                echo 'Fedora 28: Setup Camayoc'
                setupCamayoc()
            }}

            stage('F28: test api') {{
                echo 'Fedora 28: Test API'
                startQPCServer()
                runCamayocTest 'api'
            }}

            stage('F28: Test CLI') {{
                echo 'Fedora 28: Test CLI'
                startQPCServer()
                runCamayocTest 'cli'
            }}

            stage('F28: Test UI Chrome') {{
                echo 'Fedora 28: Test UI Chrome'
                startQPCServer()
                runCamayocUITest 'chrome'
            }}

            stage('F28: Test UI Firefox') {{
                echo 'Fedora 28: Test UI Firefox'
                startQPCServer()
                runCamayocUITest 'firefox'
            }}

            stage('F28: Install Tests') {{
                runInstallTests 'f28'
                junit 'junit.xml'
            }}
         }}
    }},

    'RHEL6 Install': {{
        node('rhel6-os-old') {{
            stage('rhel6 Install') {{
                dir('ci') {{
                    git 'https://github.com/quipucords/ci.git'
                }}

                configFileProvider([configFile(fileId:
                '5b276700-674e-4a0f-af91-3e725ed7a311', targetLocation: 'rhel6-custom.repo')]) {{
                    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
                        withCredentials([file(credentialsId:
                        '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {{
                            withEnv(['DISTRO=rhel6', 'RELEASE={release}']) {{
                                setupDocker()

                                sh '''\
                                sudo cp ${{WORKSPACE}}/rhel6-custom.repo /etc/yum.repos.d/rhel6-rcm-internal.repo
                                sudo yum clean all
                                sudo yum-config-manager --disable base
                                sudo yum-config-manager --disable optional
                                '''.stripIndent()

                                setupQPC()

                                runInstallTests 'rhel6'
                            }}
                        }}
                    }}
                }}
            }}
            junit 'junit.xml'
        }}
    }}, 'RHEL7 Install': {{
        node('rhel7-os-old') {{
            stage('rhel7 Install') {{
                dir('ci') {{
                    git 'https://github.com/quipucords/ci.git'
                }}

                configFileProvider([configFile(fileId:
                '0f157b1a-7068-4c75-a672-3b1b90f97ddd', targetLocation: 'rhel7-custom.repo')]) {{
                    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
                        withCredentials([file(credentialsId:
                        '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {{
                            withEnv(['DISTRO=rhel7', 'RELEASE={release}']) {{
                                setupDocker()

                                sh 'sudo cp rhel7-custom.repo /etc/yum.repos.d/rhel7-rcm-internal.repo'
                                setupQPC()
                                runInstallTests 'rhel7'
                            }}
                        }}
                    }}
                }}
            }}
            junit 'junit.xml'
        }}
    }}
}}
