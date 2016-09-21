package com.demo.jdbc;

import java.sql.DriverManager;
import java.sql.SQLException;

import com.mysql.jdbc.Connection;

public class JDBCConn {
	String errMsg = null;
	Connection connection = null;
	Boolean isDDL = null;
	java.sql.Statement stmt = null;


	public JDBCConn(String host, String user, String password, String db, int port){
		try{
			Class.forName("com.mysql.jdbc.Driver");
			connection = (Connection) DriverManager.getConnection("jdbc:mysql://"+host+":"+port + "/" + db + "?useSSL=false",user,password);
		}catch(Exception e){
			System.out.println("create conn err!");
			e.printStackTrace();
		}
	}
	
	public Boolean execute(String sql){
		errMsg = null;
		try{
			stmt = connection.createStatement();
			isDDL = stmt.execute(sql);
		}catch(SQLException e){
			errMsg = e.getMessage();
		    System.out.println("SQLException: " + errMsg);
		    System.out.println("SQLState: " + e.getSQLState());
		    System.out.println("VendorError: " + e.getErrorCode());
		    
//		    e.printStackTrace();
		}
		return isDDL;
	}
	
	public void close(){
		if(stmt!=null){
			try{
				stmt.close();
			}catch (SQLException e){
				e.printStackTrace();
			}
		}
		if( connection!=null){
			try{
				connection.close();
			}catch (SQLException e){
				e.printStackTrace();
			}
		}		
	}
}
