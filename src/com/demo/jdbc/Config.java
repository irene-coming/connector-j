package com.demo.jdbc;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Config {

	public static String Host_Single_MySQL = "172.100.7.3";
	public static String Host_Uproxy = "172.100.7.1";
	public static String Host_Master = "172.100.7.4";
	public static String Host_Slave1 = "172.100.7.5";
	public static String Host_Slave2 = "172.100.7.6";
	public static String[] mysql_hosts = {Host_Single_MySQL, Host_Master, Host_Slave1, Host_Slave2};
	public static String UPROXY_ADMIN = "admin";
	public static String UPROXY_ADMIN_PASSWD = "password";
	
	public static String TEST_USER = "uproxy";
	public static String TEST_USER_PASSWD = "111111";
	public static String TEST_DB = "mytest";
	public static int UPROXY_PORT = 1234;
	public static int MYSQL_PORT = 3306;
	
	public static String SSH_USER = "root";
	public static String SSH_PASSWORD = "sshpass";
	
	public static String MYSQL_INSTALL_PATH = "/usr/local/mysql";
	public static String UPROXY_INSTALL_PATH = "/usr/local/uproxy";
	
	public Config() {
		// TODO Auto-generated constructor stub
	}
	
	public static String getUproxyAdminCmd(){
		String cmd = Config.MYSQL_INSTALL_PATH + " -u"+Config.UPROXY_ADMIN+" -p"+Config.UPROXY_ADMIN+" -h127.0.0.1 -P" + Config.UPROXY_PORT
				+ "-e \"";
		return cmd;
	}
	
	public static void sleep(int interval){
		try {
			Thread.sleep(1000*interval);
			System.out.print("thread sleep "+interval+" seconds! \n");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static String getUproxyLogName(){
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_H:m:s");
		String log_name = format.format(new Date());
		return log_name;
	}

}
