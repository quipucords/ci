pipeline {
    agent { label "${params.node_os}" }

environment {
    install_tar = "quipucords.install.tar"
}

parameters {
    string(defaultValue: "master", description: 'What version?', name: 'version_name')
    choice(choices: ['rhel7-os', 'rhel8-os'], description: "Node OS", name: 'node_os')
    choice(choices: ['branch', 'tag'], description: "Branch or Tag?", name: 'version_type')
    choice(choices: ['0.9.0', '0.9.1'], description: "Server Version", name: 'server_install_version')
    choice(choices: ['0.9.0', '0.9.1'], description: "CLI Version", name: 'cli_install_version')
}

stages {
    stage('Build Info') {
        steps {
            echo "Version: ${params.version_name}\nVersion Type: ${params.version_type}\nCommit: ${env.GIT_COMMIT}\n\nNode OS: ${env.node_os}\n\nServer Install Version: ${params.server_install_version}\nCLI Install Version: ${params.cli_install_version}"
            sh 'cat /etc/redhat-release'
        }
    }

    stage('Setup System') {
    	steps {
            install_deps()
            setupDocker()
            setupScanUsers()
        }//end steps
    }//end stage

    stage('Install') {
        steps {
            qpc_tools_install()
        }//end steps
    }//end stage

//    stage('Run Camayoc API Tests') {
//        steps {
//            sh 'sleep 30'
//            runCamayocTest 'api'
//        }//end steps
//    }//end stage

    stage('Setup Camayoc') {
        steps {
            setup_camayoc()
        }//end steps
    }//end stage

    stage('Run Camayoc CLI Tests') {
        steps {
            runCamayocTest 'cli'
        }//end steps
    }//end stage

//    stage('Run Camayoc chrome Tests') {
//        steps {
//            runCamayocUITest 'chrome'
//        }//end steps
//    }//end stage
//
//    stage('Run Camayoc firefox Tests') {
//        steps {
//            runCamayocUITest 'firefox'
//        }//end steps
//    }//end stage
    }
}


/////////////////////////
//// Setup Functions ////
/////////////////////////
def install_deps() {
    configFileProvider([configFile(fileId:
    '0f157b1a-7068-4c75-a672-3b1b90f97ddd', targetLocation: 'rhel7-custom.repo')]) {
	    //sh 'sudo yum update -y'
        //sh 'sudo yum -y install python36 python36-pip ansible podman'
        sh 'sudo yum -y install python3 python3-pip ansible podman'
        sh 'python3 -m pip install pipenv --user'
        sh 'sudo cat /etc/containers/registries.conf'
    }//end configfile
}//end def


def qpc_tools_install() {
    echo "Install Server and CLI using qpc-tools"
    // Install qpc-tools (break into own function?)
    sh 'sudo dnf install -y https://github.com/quipucords/qpc-tools/releases/latest/download/qpc-tools.el8.noarch.rpm'

    sh "pwd"
    sh "ls -lah"
    sh 'sudo podman pull postgres:9.6.10'
    // Install CLI
    sh "sudo qpc-tools cli install --version ${params.server_install_version} --home-dir ${workspace}"
    // Install Server
    sh "sudo qpc-tools server install --version ${params.server_install_version} --password qpcpassw0rd --db-password pass --home-dir ${workspace}"
}//end def


def setupDocker() {
    sh """\
    echo "OPTIONS=--log-driver=journald" > docker.conf
    echo "DOCKER_CERT_PATH=/etc/docker" >> docker.conf
    echo "INSECURE_REGISTRY=\\"--insecure-registry \${DOCKER_REGISTRY}\\"" >> docker.conf
    sudo cp docker.conf /etc/sysconfig/docker
    """.stripIndent()
}//end def

def setup_camayoc() {
    // Pull and Install Camayoc
   dir('camayoc') {
    git 'https://github.com/quipucords/camayoc.git'
    sh '''\
        git checkout issues/336
        python3 --version
    	python3 -m pipenv run make install-dev
    '''.stripIndent()
    }//end dir

    // Set pytest file
    sh '''\
        pwd
        cp camayoc/pytest.ini .
    '''.stripIndent()

    // Setup Camayoc Config File
	configFileProvider([configFile(fileId: '62cf0ccc-220e-4177-9eab-f39701bff8d7', targetLocation: 'camayoc/camayoc/config.yaml')]) {
        sh '''\
            pwd
            cat camayoc/camayoc/config.yaml
            sed -i "s/{jenkins_slave_ip}/${OPENSTACK_PUBLIC_IP}/" camayoc/camayoc/config.yaml
            cat camayoc/camayoc/config.yaml
        '''.stripIndent()
    }//end configfile

    // Setup ssh credentials
    withCredentials([file(credentialsId: '4c692211-c5e1-4354-8e1b-b9d0276c29d9', variable: 'ID_JENKINS_RSA')]) {
        String ssh_keyfile_string = "${workspace}/server/volumes/sshkeys/id_rsa"
        ssh_keyfile = ssh_keyfile_string.replace("/", "\\/")
        sh """\
            mkdir -p "${workspace}"/sshkeys
            sudo cp "${ID_JENKINS_RSA}" "${ssh_keyfile_string}"
            sudo chown -R jenkins:jenkins "${ssh_keyfile_string}"
            sudo chown -R jenkins:jenkins "${workspace}"
            sudo chmod -R 0600 "${ssh_keyfile_string}"
            sudo cat "${ssh_keyfile_string}"
            ## Edit ssh location in config file
            sed -i "s/{jenkins_ssh_file}/\\/sshkeys\\/id_rsa/" camayoc/camayoc/config.yaml
        """.stripIndent()
    }//end withCredentials

    // Add file location
    String isolated_fs_string = "${workspace}/server/volumes/sshkeys/"
    isolated_fs_path = isolated_fs_string.replace("/", "\\/")
    sh """\
        sed -i "s/{isolated_fs_placeholder}/${isolated_fs_path}/" camayoc/camayoc/config.yaml
    """.stripIndent()
}//end def


def setupScanUsers() {
    dir('ci') {
        git 'https://github.com/quipucords/ci.git'
    }//end dir

    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
        withCredentials([file(credentialsId: '50dc19ce-555f-422c-af38-3b5ede422bb4', variable: 'ID_JENKINS_RSA_PUB')]) {
            sh 'sudo yum -y install ansible'

            sh '''\
                cat > jenkins-slave-hosts <<EOF
                [jenkins-slave]
                ${OPENSTACK_PUBLIC_IP}

                [jenkins-slave:vars]
                ansible_user=jenkins
                ansible_ssh_extra_args=-o StrictHostKeyChecking=no
                ssh_public_key_file=$(cat ${ID_JENKINS_RSA_PUB})
                EOF
            '''.stripIndent()

            sh 'ansible-playbook -b -i jenkins-slave-hosts ci/ansible/sonar-setup-scan-users.yaml'
        }//end withCredentials
    }//end sshagent
}//end def


////////////////////
// Test Functions //
////////////////////
def runCamayocTest(testset) {
    echo "Running ${testset} Tests"

    sh 'ls -lah'
    sh 'pwd'
    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
        dir('camayoc') {
        sh """
            set +e
            export XDG_CONFIG_HOME=\$(pwd)
            echo \$XDG_CONFIG_HOME
            cat \$XDG_CONFIG_HOME/camayoc/config.yaml
            python3 -m pipenv run py.test -c pytest.ini -l -ra -s -vvv --junit-xml $testset-junit.xml --rootdir camayoc/tests/qpc camayoc/tests/qpc/$testset
            set -e
            # tar -cvzf test-$testset-logs.tar.gz log
        """.stripIndent()
        sh 'ls -la'
        echo "$testset-junit.xml"
        sh "cat $testset-junit.xml"
        archiveArtifacts "$testset-junit.xml"

        junit "$testset-junit.xml"
        }//end dir
    }//end sshagent
    //archiveArtifacts "test-$testset-logs.tar.gz"
}

def runCamayocUITest(browser) {
    echo "Running ${browser} Tests"

    sh 'ls -lah'
    sshagent(['390bdc1f-73c6-457e-81de-9e794478e0e']) {
        dir('camayoc') {
            sh "sudo docker run --net='host' -d -p 4444:4444 -v /dev/shm:/dev/shm:z -v /tmp:/tmp:z selenium/standalone-$browser"
            sleep 3

            sh """\
                set +e
                export XDG_CONFIG_HOME=\$(pwd)
                echo \$XDG_CONFIG_HOME
                export SELENIUM_DRIVER=$browser
                cat \$XDG_CONFIG_HOME/camayoc/config.yaml
                python3 -m pipenv run py.test -c pytest.ini -l -ra -vvv --junit-prefix $browser --junit-xml ui-$browser-junit.xml --rootdir camayoc/tests/qpc camayoc/tests/qpc/ui
                set -e
                # tar -cvzf test-ui-$browser-logs.tar.gz log
                # sudo rm -rf log
            """.stripIndent()

            echo 'Archiving artifacts'
            //archiveArtifacts "test-ui-$browser-logs.tar.gz"
            junit "ui-$browser-junit.xml"
        }//end dir
    }//end sshagent
}//end def
