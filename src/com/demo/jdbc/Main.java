package com.demo.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Main {

	public Main() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		JDBCConn conn = new JDBCConn("127.0.0.1", "janey", "111111", "", 3306);
		ResultSet re = conn.executeQuery("show databases");
		try {
			re.next();
			System.out.println(re.getString(1));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		conn.close();

		// TODO Auto-generated method stub
		// Setup setter = Setup.getInstance();
		// String[] sqlFiles = setter.getSqlFiles("/share_dir/sqls.config");
		// int sqlFilesNum = sqlFiles.length;
		//
		// for (int i = 0; i < sqlFilesNum; ++i) {
		// String curFile = sqlFiles[i];
		// if (curFile == null) {
		// break;
		// }
		// System.out.println("sql file: " + curFile);
		// setter.createTestDB();
		// ExecSQLAndCompare executer = new ExecSQLAndCompare(curFile);
		// executer.analyzeSql();
		// setter.clearDirtyFiles();
		// }
	}
}
