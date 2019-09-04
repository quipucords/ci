pipeline {
    agent { label 'rhel7-os' }

environment {
    //qpc_version = getQPCVersion()
    //build_name = getBuildName()
    //image_name = "quipucords:${qpc_version}"
    //tarfile = "quipucords.${qpc_version}.tar"
    //targzfile = "${tarfile}.gz"
    install_tar = "quipucords.install.tar"
    //install_targzfile = "${install_tar}.gz"
}

parameters {
    string(defaultValue: "master", description: 'What version?', name: 'version_name')
    choice(choices: ['branch', 'tag'], description: "Branch or Tag?", name: 'version_type')
    choice(choices: ['0.9.0', '0.9.1'], description: "Server Version", name: 'server_install_version')
    choice(choices: ['0.9.0', '0.9.1'], description: "CLI Version", name: 'cli_install_version')
}

stages {
    stage('Build Info') {
        steps {
            echo "Version: ${params.version_name}\nVersion Type: ${params.version_type}\nCommit: ${env.GIT_COMMIT}\n\nBuild_version: ${env.build_version}\n\nServer Install Version: ${params.server_install_version}\nCLI Install Version: ${params.cli_install_version}"
        }
    }

    stage('Setup System') {
    	steps {
            install_deps()
            setupDocker()
            setupScanUsers()
        }//end steps
    }//end stage

    stage('Setup Camayoc') {
        steps {
            setup_camayoc()
        }//end steps
    }//end stage

    stage('Install') {
        steps {
            echo "Install Server and CLI"
            sh 'git clone https://github.com/quipucords/quipucords-installer.git'
            dir('quipucords-installer/install') {
                sh "pwd"
                sh "ls -lah"
                sh "./quipucords-installer -e server_version=${params.server_install_version} -e cli_version=${params.cli_install_version} -e server_install_dir=${workspace}"
            }//end dir
        }//end steps
    }//end stage

    stage('Run Camayoc API Tests') {
        steps {
            sh 'sleep 60'
            runCamayocTest 'api'
        }//end steps
    }//end stage

    stage('Run Test Reports') {
    	steps{
    		dir('camayoc') {
    			//junit 'yupana-junit.xml'
    		}
    	}
    }
}
}


/////////////////////////
//// Setup Functions ////
/////////////////////////
def install_deps() {
    configFileProvider([configFile(fileId:
    '0f157b1a-7068-4c75-a672-3b1b90f97ddd', targetLocation: 'rhel7-custom.repo')]) {
	    //sh 'sudo yum update -y'
        sh 'sudo yum -y install python36 python36-pip ansible'
        sh 'python3 -m pip install pipenv --user'
    }//end configfile
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
        String ssh_keyfile_string = "${workspace}/sshkeys/id_rsa"
        ssh_keyfile = ssh_keyfile_string.replace("/", "\\/")
        sh """\
            mkdir -p "${workspace}"/sshkeys
            cp "${ID_JENKINS_RSA}" "${ssh_keyfile_string}"
            chmod 0600 "${ssh_keyfile_string}"
            cat "${ssh_keyfile_string}"
            ## Edit ssh location in config file
            sed -i "s/{jenkins_ssh_file}/\\/sshkeys\\/id_rsa/" camayoc/camayoc/config.yaml
        """.stripIndent()
    }//end withCredentials
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
        sh 'pwd'
        sh 'sudo docker ps -a'
        sh 'sudo docker exec quipucords ls -lah'
        sh 'sudo docker exec quipucords ls /sshkeys -lah'
        sh """
            set +e
            export XDG_CONFIG_HOME=\$(pwd)
            echo \$XDG_CONFIG_HOME
            cat \$XDG_CONFIG_HOME/camayoc/config.yaml
#cat \$(XDG_CONFIG_HOME)/camayoc/config.yaml # Check for config file
            python3 -m pipenv run py.test -c pytest.ini -l -ra -s -vvv --junit-xml $testset-junit.xml --rootdir camayoc/tests/qpc camayoc/tests/qpc/$testset
            set -e

            sudo docker rm \$(sudo docker stop \$(sudo docker ps -aq))
            #tar -cvzf test-$testset-logs.tar.gz log
            #sudo rm -rf log
        """.stripIndent()
        sh 'ls -la'
        echo "$testset-junit.xml"
        sh "cat $testset-junit.xml"
        archiveArtifacts "$testset-junit.xml"

        junit "$testset-junit.xml"
        }//end dir
    }//end sshagent
    //archiveArtifacts "test-$testset-logs.tar.gz"
    // step([$class: 'XUnitBuilder',
    // thresholds: [[$class: 'FailedThreshold', unstableThreshold: '1']],
    // tools: [[$class: 'JUnitType', pattern: "$testset-junit.xml"]]])
}

