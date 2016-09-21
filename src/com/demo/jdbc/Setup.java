package com.demo.jdbc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

public class Setup {

	public static String UPROXY_LOG = null;

	private static Setup instance = null;
	private Setup() {
		// TODO Auto-generated constructor stub
	}
	
	public static Setup getInstance(){
		if(instance == null)
			instance = new Setup();
		return instance;
	}
	
	public String[] getSqlFiles(String fileName){
		String[] sqlFiles = new String[20];
		int sqlFilesNum = 0;
		
		File file = new File(fileName);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String tempString = null;
            while ((tempString = reader.readLine()) != null) {
            	String sqlFile = tempString.trim();
            	if(sqlFile.length()>0){
            		sqlFiles[sqlFilesNum] = sqlFile;
            		sqlFilesNum++;
            	}
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                }
            }
        }
        
        return sqlFiles;
	}

	public void createTestDB(){
		doCreateTestDB(Config.Host_Uproxy,
				Config.TEST_USER,
				Config.TEST_USER_PASSWD,
	    		"",
	    		Config.UPROXY_PORT);
		doCreateTestDB(Config.Host_Single_MySQL,
				Config.TEST_USER,
				Config.TEST_USER_PASSWD,
				"",
				Config.MYSQL_PORT
				);
	}
	
	private void doCreateTestDB(String host, String user, String password, String db, int port){
	    JDBCConn conn = new JDBCConn(host, user, password, db, port);
	    conn.execute("drop database if exists mytest");
	    conn.execute("create database mytest");
	    conn.close();
	}
	
	public void clearDirtyFiles(){
		String cmd = "cd "+Config.MYSQL_INSTALL_PATH+"/"+Config.TEST_DB+" && rm -rf outfile*.txt dumpfile.txt";
		for(int i=0; i<Config.mysql_hosts.length; ++i){
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.mysql_hosts[i], Config.SSH_USER, Config.SSH_PASSWORD);
			sshExecutor.execute(cmd);
		}
	}

	public void prepare(){
		restart();
		addGroupAndMysqld();
	}
	private void restart(){
		//stop uproxy
		String cmd = "ps aux | grep uproxy | grep -v grep | awk '{print $2}'";
		SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.Host_Uproxy, Config.SSH_USER, Config.SSH_PASSWORD);
		sshExecutor.execute(cmd);
		
		Vector<String> stdout = sshExecutor.getStandardOutput();
		for (String str : stdout) {
			String killCmd = "kill " + str;
			sshExecutor.execute(killCmd);	
		}
		
		//start uproxy
		UPROXY_LOG = Config.getUproxyLogName();
		
		String start_cmd = "cd "+Config.UPROXY_INSTALL_PATH+" && mkdir -p logs && (./uproxy >> logs/"+ UPROXY_LOG +" 2>&1 &)";
		sshExecutor.execute(start_cmd);
		
		Config.sleep(10);
	}
	
	private void addGroupAndMysqld(){
		String precmd = Config.getUproxyAdminCmd();
		String cmd1 = precmd + "uproxy add_group '"+ Config.TEST_USER  +"' '"+Config.TEST_USER_PASSWD+"'\"";
		String cmd2 = precmd + "uproxy add_mysqlds '"+ Config.TEST_USER  +"' masters '"+ Config.Host_Master+":"+Config.MYSQL_PORT  +"'\"";
		String cmd3 = precmd + "uproxy add_mysqlds '"+ Config.TEST_USER  +"' slaves '"+ Config.Host_Slave1+":"+Config.MYSQL_PORT  +"'"+ " '"+ Config.Host_Slave2+":"+Config.MYSQL_PORT  +"'\"";
		
		SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.Host_Uproxy, Config.SSH_USER, Config.SSH_PASSWORD);
		
		sshExecutor.execute(cmd1);
		sshExecutor.execute(cmd2);
		sshExecutor.execute(cmd3);
	}
}
