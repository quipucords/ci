// {release} {install_type}

def qpc_version = getQPCVersion()
def build_name = getBuildName()
def image_name = "quipucords:${{qpc_version}}"
def tarfile = "quipucords.${{qpc_version}}.tar"
def targzfile = "${{tarfile}}.gz"
def install_tar = "quipucords.install.tar"
def install_targzfile = "${{install_tar}}.gz"

def getQPCVersion() {{
    if ('{release}' == 'master') {{
        return "master"
    }} else {{
        return "{release}"
    }}
}}

def getCLIVersion() {{
    if ('{release}' == 'master') {{
        return "master"
    }} else {{
        return "0.9.0"
    }}
}}

def getBuildName() {{
    if ('{release}' == 'master') {{
        return "master"
    }} else {{
        return "latest_release"
    }}
}}

/////////////////////////
//// Setup Functions ////
/////////////////////////
def setupDocker() {{
    sh """\
    echo "OPTIONS=--log-driver=journald" > docker.conf
    echo "DOCKER_CERT_PATH=/etc/docker" >> docker.conf
    echo "INSECURE_REGISTRY=\\"--insecure-registry \${{DOCKER_REGISTRY}}\\"" >> docker.conf
    sudo cp docker.conf /etc/sysconfig/docker
    """.stripIndent()
}}

def setupPython3() {{
    // Currently for RHEL7
	echo "Python3 Setup"
    sh '''\
    # wget https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm
    # sudo yum install epel-release-latest-7.noarch.rpm
	sudo yum update -y
    # sudo yum -y install @development
	sudo yum -y install python36 python36-pip
    # scl enable python36 bash
	python --version
    python3 --version
    pip --version
    pip3 --version
    '''.stripIndent()
}}



/////////////////////////////////////
//// Configure & Start Functions ////
/////////////////////////////////////
def startQPCServer = {{
    sh """\
    echo 'startQPCServer'
    pwd
    ls -lah
    sudo systemctl start docker # Ensure docker running
    QPC_DB=\$(sudo docker ps | grep qpc-db || true)
    QPC_CONTAINER=\$(sudo docker ps | grep ${{image_name}} || true)

    # Stop containers if they are running before restarting or testing
    if [ \"\$QPC_DB\" != '' ] || [ \"\$QPC_CONTAINER\" != '' ]
    then
        sudo docker rm \$(sudo docker stop \$(sudo docker ps -aq))
    fi
    # Start up docker contaienrs
    sudo docker run --name qpc-db -e POSTGRES_PASSWORD=password -d postgres:9.6.10
    sudo docker run -d -p '9443:443' --link qpc-db:qpc-link \\
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



//////////////////////////////
//// Get Builds Functions ////
//////////////////////////////
def getQuipucords() {{
    // get QPC
    echo 'getQuipucords'
    if ('{release}' == 'master') {{
        getMasterQPC()
    }} else {{
        getReleasedQPC()
    }}
}}


def getQuipucordsBuild() {{
    // Grabs the quipucords build
    def builder_name = getBuildName()
    sh 'ls -la'
    echo "getQuipucords: Copying Latest build artifact..."
    copyArtifacts filter: 'quipucords_server_image.tar.gz', fingerprintArtifacts: true, projectName: "qpc_${{builder_name}}_server_images", selector: lastCompleted()

    copyArtifacts filter: 'quipucords_install.tar.gz', fingerprintArtifacts: true, projectName: "qpc_${{builder_name}}_installer", selector: lastCompleted()

    copyArtifacts filter: 'postgres.*.tar.gz', fingerprintArtifacts: true, projectName: "qpc_${{builder_name}}_server_images", selector: lastCompleted()

    sh 'ls -la'
}}


def getMasterQPC() {{
    // Grabs the master qpc builds and sets it up
    echo 'getMasterQPC'
    def qpc_version = getQPCVersion()
    getQuipucordsBuild()

    sh 'ls -lah'
    echo 'Extract the installer script'
    sh """\
    echo ${{qpc_version}}
    tar -xvzf quipucords_install.tar.gz
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
    sh "curl -vvvvv -k -O -sSL https://github.com/quipucords/quipucords/releases/download/{release}/quipucords_server_image.tar.gz"
    // Pulls down Released Install
    // TODO: Fix this versioning
    sh "curl -k -O -sSL https://github.com/quipucords/quipucords-installer/releases/download/0.1.1/quipucords_install.tar.gz"

    echo "extract the installer into ${{WORKSPACE}}/install"

    sh "tar -xvzf ${{WORKSPACE}}/quipucords_install.tar.gz"
}}



///////////////////////////
//// Install Functions ////
///////////////////////////
def installQPC(distro) {{
    // Install QPC
    if ('{install_type}' == 'nosupervisord') {{
        installQPCNoSupervisorD()
    }} else if ('{install_type}' == 'container') {{
        containerInstall(distro)
    }} else {{
        defaultInstall()
    }}
}}


def defaultInstall() {{
    echo "Execute install.sh to install"
    dir("${{WORKSPACE}}/install") {{
        sh 'pwd'
        sh 'ls -l'
        sh 'sudo ./install.sh -e server_install_dir=${{WORKSPACE}}'
    }}
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


def containerInstall(distro) {{
    echo "Execute docker container install"
    def qpc_version = getQPCVersion()
    if (distro == 'rhel6') {{
        echo 'No systemd, Docker should already be started.'
    }} else {{
        echo 'Starting Docker using Systemd'
        sh 'sudo systemctl start docker'
        sh 'sudo systemctl status docker'
    }}

    sh """\
    sudo docker load -i quipucords_server_image.tar.gz
    # make log dir to save server logs
    mkdir -p log
    """.stripIndent()
}}


def installQPCClient(distro) {{
    // Install the qpc client
    echo 'Install QPC Client'
    // Pull Latest repo source from Copr
    sh '''\
    sudo wget -O /etc/yum.repos.d/group_quipucords-qpc-fedora-28.repo https://copr.fedorainfracloud.org/coprs/g/quipucords/qpc/repo/fedora-28/group_quipucords-qpc-fedora28.repo
    ls -la /etc/yum.repos.d/
    '''.stripIndent()

    if ('{release}' == 'master') {{
		installMasterQPCClient(distro)
    }} else {{
        installReleasedQPCClient()
    }}

}}

def installMasterQPCClient(distro) {{
	// Install latest qpc
	if (distro ==~ /f\d\d/) {{
		sh 'sudo dnf -y install qpc'
	}} else {{
		sh 'sudo yum -y install qpc'
	}}
}}


def installReleasedQPCClient() {{
    // Pulls down current released cli
	dir("${{WORKSPACE}}/install") {{
        def CLI_VERSION = getCLIVersion()
        sh """\
        wget -O qpc-client.rpm "https://github.com/quipucords/qpc/releases/download/${{CLI_VERSION}}/qpc.el7.noarch.rpm"
        ls -la
        # Install the rpm
        if grep -q -i "Fedora" /etc/redhat-release; then
            sudo dnf install -y qpc-client.rpm
        else
            sudo yum install -y qpc-client.rpm
        fi
		""".stripIndent()
    }}
}}



////////////////////
//// Test Setup ////
////////////////////
def setupScanUsers() {{
    dir('ci') {{
        git 'https://github.com/quipucords/ci.git'
    }}

    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {{
        withCredentials([file(credentialsId: '50dc19ce-555f-422c-af38-3b5ede422bb4', variable: 'ID_JENKINS_RSA_PUB')]) {{
            sh 'sudo yum -y install ansible'

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
    sudo python36 -m pip install ./camayoc[dev]
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
        '''.stripIndent()

    }}
}}



////////////////////////
//// Test Functions ////
////////////////////////
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
        nosetests --with-xunit --xunit-file=$distro-install-junit.xml ci/scripts/quipucords/master/install/test_install.py
        set -e
        '''.stripIndent()
    }} else {{
        sh 'sudo pip install -U pip'
        sh 'sudo pip install pexpect pytest'

        sh """\
        set +e
        pytest --junit-prefix ${{distro}} --junit-xml $distro-install-junit.xml ci/scripts/quipucords/master/install/test_install.py
        set -e
        """.stripIndent()
    }}

    // junit "junit.xml"
}}


def runCamayocTest(testset) {{
    echo "Running ${{testset}} Tests"

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
    archiveArtifacts "test-$testset-logs.tar.gz"
    sh 'ls -la'
    echo "$testset-junit.xml"
    sh "cat $testset-junit.xml"
    archiveArtifacts "$testset-junit.xml"

    // junit "$testset-junit.xml"
    // step([$class: 'XUnitBuilder',
    // thresholds: [[$class: 'FailedThreshold', unstableThreshold: '1']],
    // tools: [[$class: 'JUnitType', pattern: "$testset-junit.xml"]]])
}}


def runCamayocUITest(browser) {{
    echo "Running ${{browser}} Tests"

    sh 'cat camayoc/config.yaml'
    sh 'ls -lah'
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

    echo 'Archiving artifacts'
    archiveArtifacts "test-ui-$browser-logs.tar.gz"
    // junit "ui-$browser-junit.xml"
}}



////////////////////////
//// Pipeline Stage ////
////////////////////////
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
                            sh """\
                            echo 'Testing qpc_version variable'
                            echo ${{qpc_version}}
                            """.stripIndent()
                            echo "Testing inline qpc_version variable: ${{qpc_version}}"
                            getQuipucords()
                            installQPC 'centos7'

//                            sh 'sudo yum install epel-release'
//                            sh 'sudo yum install -y python36 python36-pip'
                        }}
                    }}
                }}
            }}

//            stage('Centos7: Setup Integration Tests') {{
//                echo 'Centos 7: Install QPC Client'
//                installQPCClient 'centos7'
//                echo 'Centos 7: Setup Scan Users'
//                setupScanUsers()
//                echo 'Centos 7: Setup Camayoc'
//                setupCamayoc()
//            }}
//
//            stage('Centos7: test api') {{
//                echo 'Centos 7: Test API'
//                startQPCServer()
//                runCamayocTest 'api'
//            }}
//
//            stage('Centos7: Test CLI') {{
//                echo 'Centos 7: Test CLI'
//                startQPCServer()
//                runCamayocTest 'cli'
//            }}


        }}
    }},



    'RHEL6 Install': {{
        node('rhel6-os') {{
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

                                getQuipucords()
                                installQPC 'rhel6'
                            }}
                        }}
                    }}
                }}
            }}
        }}
    }}, 'RHEL7 Install': {{
        node('rhel7-os') {{
            sh 'sudo yum list installed'
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
                                echo 'Install Docker?'
                                // TODO: Better solution
                                sh 'sudo yum install docker -y'

                                setupDocker()

                                sh 'sudo cp rhel7-custom.repo /etc/yum.repos.d/rhel7-rcm-internal.repo'

								setupPython3()

                                getQuipucords()
                                installQPC 'rhel7'
                            }}
                        }}
                    }}
                }}
            }}
            stage('RHEL7: Setup Integration Tests') {{
                echo 'RHEL 7: Install QPC Client'
                installQPCClient 'centos7'
                echo 'RHEL 7: Setup Scan Users'
                setupScanUsers()
                echo 'RHEL 7: Setup Camayoc'
                setupCamayoc()
            }}

            stage('RHEL7: test api') {{
                echo 'RHEL 7: Test API'
                sh 'sudo yum list installed'
                startQPCServer()
                runCamayocTest 'api'
            }}

            stage('RHEL7: Test CLI') {{
                echo 'RHEL 7: Test CLI'
                startQPCServer()
                runCamayocTest 'cli'
            }}
        }}
    }}
}}
