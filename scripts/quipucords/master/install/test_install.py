"""Test to confirm functional install of quipucords server."""

import unittest
import pexpect

from io import BytesIO


class TestInstall(unittest.TestCase):
    """Test that we can log in and create items on the server.

    The intent of this test is to verify that the install is functional.
    """

    def test_all(self):
        command = 'qpc server config --host 127.0.0.1'
        qpc_server_config = pexpect.spawn(command)
        qpc_server_config.logfile = BytesIO()
        assert qpc_server_config.expect(pexpect.EOF) == 0
        qpc_server_config.close()
        assert qpc_server_config.exitstatus == 0

        # now login to the server
        command = 'qpc server login --username admin'
        qpc_server_login = pexpect.spawn(command)
        qpc_server_config.logfile = BytesIO()
        assert qpc_server_login.expect('Password: ') == 0
        qpc_server_login.sendline('pass')
        assert qpc_server_login.expect(pexpect.EOF) == 0
        qpc_server_login.close()
        output = qpc_server_config.logfile.getvalue().decode('utf-8')
        if not qpc_server_login.exitstatus == 0:
            raise AssertionError(output)

        # Create credential, source, and scan
        command = 'qpc cred add --type network --username example --name example --password'
        qpc_cred_add = pexpect.spawn(command)
        qpc_cred_add.logfile = BytesIO()
        assert qpc_cred_add.expect('Password: ') == 0
        qpc_cred_add.sendline('pass')
        assert qpc_cred_add.expect(pexpect.EOF) == 0
        qpc_cred_add.close()
        output = qpc_cred_add.logfile.getvalue().decode('utf-8')
        if not qpc_cred_add.exitstatus == 0:
            raise AssertionError(output)

        command = 'qpc source add --type network  --hosts localhost --name example --cred example'
        qpc_source_add = pexpect.spawn(command)
        qpc_source_add.logfile = BytesIO()
        assert qpc_source_add.expect(pexpect.EOF) == 0
        qpc_source_add.close()
        output = qpc_source_add.logfile.getvalue().decode('utf-8')
        if not qpc_source_add.exitstatus == 0:
            raise AssertionError(output)

        command = 'qpc scan add --name example --sources example'
        qpc_scan_add = pexpect.spawn(command)
        qpc_scan_add.logfile = BytesIO()
        assert qpc_scan_add.expect(pexpect.EOF) == 0
        qpc_scan_add.close()
        output = qpc_scan_add.logfile.getvalue().decode('utf-8')
        if not qpc_scan_add.exitstatus == 0:
            raise AssertionError(output)

        command = 'qpc scan start --name example'
        qpc_scan_start = pexpect.spawn(command)
        qpc_scan_start.logfile = BytesIO()
        assert qpc_scan_start.expect(pexpect.EOF) == 0
        qpc_scan_start.close()
        output = qpc_scan_start.logfile.getvalue().decode('utf-8')
        if not qpc_scan_start.exitstatus == 0:
            raise AssertionError(output)

    def tearDown(self):
        for item in ['scan', 'source', 'cred']:
            qpc_command = pexpect.spawn('qpc {0} clear --all'.format(item))
            qpc_command.expect(pexpect.EOF)
            qpc_command.close()
