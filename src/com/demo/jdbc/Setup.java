package com.demo.jdbc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

public class Setup {
	public static String Host_Single_MySQL = "172.100.7.3";
	public static String Host_Uproxy = "172.100.7.1";
	public static String Host_Master = "172.100.7.4";
	public static String Host_Slave1 = "172.100.7.5";
	public static String Host_Slave2 = "172.100.7.6";
	
	public static String TEST_USER = "user1";
	public static String TEST_USER_PASSWD = "111111";
	public static String TEST_DB = "mytest";
	public static int UPROXY_PORT = 1234;
	public static int MYSQL_PORT = 3306;
	public static String UPROXY_LOG = null;

	public static String[] mysql_hosts = {Host_Single_MySQL, Host_Master, Host_Slave1, Host_Slave2};

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
		doCreateTestDB(Host_Uproxy,
	    		"user1",
	    		"111111",
	    		"",
	    		1234);
		doCreateTestDB(Host_Single_MySQL,
				"uproxy",
				"111111",
				"",
				3306
				);
	}
	
	private void doCreateTestDB(String host, String user, String password, String db, int port){
	    JDBCConn conn = new JDBCConn(host, user, password, db, port);
	    conn.executeQuery("drop database if exists mytest");
	    conn.executeQuery("create database mytest");
	    conn.close();
	}
	
	public void clearDirtyFiles(){
		String cmd = "cd /var/lib/mysql/mytest && rm -rf outfile*.txt dumpfile.txt";
		for(int i=0; i<mysql_hosts.length; ++i){
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(mysql_hosts[i], "root", "sshpass");
			sshExecutor.execute(cmd);
			
			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
	}

	public void prepare(){
		restart();
		addUser();
		addGroupAndMysqld();
	}
	private void restart(){
		//stop uproxy
		String cmd = "ps aux | grep uproxy | grep -v grep | awk '{print $2}'";
		SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Setup.Host_Uproxy, "root", "sshpass");
		sshExecutor.execute(cmd);
		
		Vector<String> stdout = sshExecutor.getStandardOutput();
		for (String str : stdout) {
			String killCmd = "kill " + str;
			sshExecutor.execute(killCmd);	
		}
		//start uproxy
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_H:m:s");
		UPROXY_LOG = format.format(new Date());
		String start_cmd = "cd /usr/local/uproxy && mkdir -p logs && (./uproxy >> logs/"+ UPROXY_LOG +" 2>&1 &)";
		sshExecutor.execute(start_cmd);
		
		try {
            Thread.sleep(10000);
            System.out.print("thread sleep 10 sec! \n");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
	}
	
	private void addUser(){
		String[] hosts = {Host_Master, Host_Single_MySQL};
		for(int i=0; i<hosts.length; ++i){
			JDBCConn conn = new JDBCConn(Host_Master, Setup.TEST_USER, Setup.TEST_USER_PASSWD, "", Setup.MYSQL_PORT);
			conn.executeQuery("create user '"+ Setup.TEST_USER  +"'@'%' identified by '"+Setup.TEST_USER_PASSWD+"'");
			conn.executeQuery("grant all on *.* to '"+ Setup.TEST_USER  +"'@'%' with grant option");
			conn.close();
		}
	}
	
	private void addGroupAndMysqld(){
		String cmd1 = "/usr/local/mysql -uadmin -ppassword -h127.0.0.1 -P" + Setup.UPROXY_PORT
				+ "-e \"uproxy add_group '"+ Setup.TEST_USER  +"' '"+Setup.TEST_USER_PASSWD+"'\"";
		String cmd2 = "/usr/local/mysql -uadmin -ppassword -h127.0.0.1 -P" + Setup.UPROXY_PORT
				+ "-e \"uproxy add_mysqlds 'user1' masters '172.100.7.4:3306'\"";
		String cmd3 = "/usr/local/mysql -uadmin -ppassword -h127.0.0.1 -P" + Setup.UPROXY_PORT
				+ "-e \"uproxy add_mysqlds 'user1' slaves '172.100.7.5:3306' '172.100.7.6:3306'\"";
		SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Setup.Host_Uproxy, "root", "sshpass");
		sshExecutor.execute(cmd1);

		Vector<String> stdout = sshExecutor.getStandardOutput();
		for (String str : stdout) {
			System.out.println(str);
		}
		sshExecutor.execute(cmd2);

		stdout = sshExecutor.getStandardOutput();
		for (String str : stdout) {
			System.out.println(str);
		}
		sshExecutor.execute(cmd3);

		stdout = sshExecutor.getStandardOutput();
		for (String str : stdout) {
			System.out.println(str);
		}
	}
}
