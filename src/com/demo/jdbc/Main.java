package com.demo.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class Main {

	public Main() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {

		// TODO Auto-generated method stub
//		dotest();

		 dowork();

	}

	private static void dotest() {
		JDBCConn conn = new JDBCConn("localhost", "uproxy", "111111", "", 3306);
		Boolean isR = conn.execute("select {d'2012@12@31'} /* uproxy_dest_expect:S */");
		if (null != isR) {
			if (isR) {
				try {
					ResultSet re = conn.stmt.getResultSet();
					re.next();
					System.out.println(re.getString(1));
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} else {
				try {
					int rows = conn.stmt.getUpdateCount();
					System.out.println("affected rows: " + rows);
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
		conn.close();
	}

	private static void dowork() {
		Setup setter = Setup.getInstance();
		setter.prepare();
		List<String> sqlFiles = setter.getSqlFiles("/share_dir/sqls.config");
		for(String sqlFile:sqlFiles){
			System.out.println("sql file: " + sqlFile);
			setter.createTestDB();
			ExecSQLAndCompare executer = new ExecSQLAndCompare(sqlFile);
			executer.analyzeSql();
			setter.clearDirtyFiles();
		}
	}

}
