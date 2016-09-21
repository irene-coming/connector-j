package com.demo.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

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
		String[] sqlFiles = setter.getSqlFiles("/share_dir/sqls.config");
		int sqlFilesNum = sqlFiles.length;

		for (int i = 0; i < sqlFilesNum; ++i) {
			String curFile = sqlFiles[i];
			if (curFile == null) {
				break;
			}
			System.out.println("sql file: " + curFile);
			setter.createTestDB();
			ExecSQLAndCompare executer = new ExecSQLAndCompare(curFile);
			executer.analyzeSql();
			setter.clearDirtyFiles();
		}
	}

}
