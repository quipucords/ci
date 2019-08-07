pipeline {
    agent { label 'f28-os' }

environment {
    qpc_version = getQPCVersion()
    build_name = getBuildName()
    image_name = "quipucords:${qpc_version}"
    tarfile = "quipucords.${qpc_version}.tar"
    targzfile = "${tarfile}.gz"
    install_tar = "quipucords.install.tar"
    install_targzfile = "${install_tar}.gz"
}

parameters {
    string(defaultValue: "master", description: 'What version?', name: 'version_name')
    choice(choices: ['branch', 'tag'], description: "Branch or Tag?", name: 'version_type')
}

stages {
    stage('Build Info') {
        steps {
            echo "Version: ${params.version_name}\nVersion Type: ${params.version_type}\nCommit: ${env.GIT_COMMIT}\n\nBuild_version: ${env.build_version}"
        }
    }

    stage('Setup System') {
    	steps {
			sh 'sudo dnf update'
		}
    }

    stage('Setup Camayoc') {
        steps {
            // Setup Camayoc
    		dir('camayoc') {
       	 		git 'https://github.com/quipucords/camayoc.git'

       	 		sh '''\
    				python3 --version
    				pipenv run make install-dev
    			'''.stripIndent()
    		}

    		// Set pytest file
    		sh '''\
    			mkdir -p ~/.config/camayoc/
    			cp camayoc/pytest.ini .
    		'''.stripIndent()

        	// Setup Camayoc Config File
		    configFileProvider([configFile(fileId: '62cf0ccc-220e-4177-9eab-f39701bff8d7', targetLocation: '/home/jenkins/.config/camayoc/config.yaml')]) {
        		sh '''\
        			cat ~/.config/camayoc/config.yaml
        			sed -i "s/{jenkins_slave_ip}/${OPENSTACK_PUBLIC_IP}/" ~/.config/camayoc/config.yaml
        		'''.stripIndent()
    		}

    		sh 'python3.6 -m pip freeze'

        }
    }

    stage('Run Camayoc Tests') {
        steps {

        // Set home dir to workspace dir
        sh 'export XDG_CONFIG_HOME=$(pwd)'
        sh 'ls -la ~'

        dir('camayoc') {
        	sh '''\
        		#py.test -c pytest.ini -l -ra -s -vvv --junit-xml yupana-junit.xml --rootdir camayoc/camayoc/tests/qpc camayoc/camayoc/tests/qpc/yupana
        		#pipenv run pytest -vvv --junit-xml yupana-junit.xml camayoc/tests/qpc/yupana
			'''.stripIndent()
			}
        }
    }

    stage('Run Test Reports') {
    	steps{
    		dir('camayoc') {
    			//junit 'yupana-junit.xml'
    		}
    	}
    }
}
}
