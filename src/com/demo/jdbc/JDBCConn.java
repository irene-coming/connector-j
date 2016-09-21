package com.demo.jdbc;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.mysql.jdbc.Connection;

public class JDBCConn {
	String errMsg = null;
	Connection connection = null;
	ResultSet resultSet = null;
	java.sql.Statement stmt = null;
	PreparedStatement preparedStatement = null;


	public JDBCConn(String host, String user, String password, String db, int port){
		try{
			Class.forName("com.mysql.jdbc.Driver");
			connection = (Connection) DriverManager.getConnection("jdbc:mysql://"+host+":"+port + "/" + db + "?useSSL=false",user,password);
		}catch(Exception e){
			System.out.println("create conn err!");
			e.printStackTrace();
		}
	}
	
	public ResultSet executeQuery(String sql){
		errMsg = null;
		if(resultSet!=null){
			try{
				resultSet.close();
			}catch (SQLException e){
				e.printStackTrace();
			}
		}
		try{
			stmt = connection.createStatement();
			preparedStatement = connection.prepareStatement(sql);
			resultSet = preparedStatement.executeQuery();
		}catch(Exception e){
			System.out.println("errrrrrrr");
			e.printStackTrace();
			errMsg = e.getMessage();
		}finally{


		}
		return resultSet;
	}
	
	public void close(){
		if(resultSet!=null){
			try{
				resultSet.close();
			}catch (SQLException e){
				e.printStackTrace();
			}
		}
		if(preparedStatement!=null){
			try{
				preparedStatement.close();
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
