package com.demo.jdbc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecSQLAndCompare {
	static Map<String, JDBCConn> share_conns_uproxy = new HashMap<String, JDBCConn>();
	static Map<String, JDBCConn> share_conns_mysql = new HashMap<String, JDBCConn>();
	String rootPath = "/share_dir/";

	String PASS_PRE = "pass_";
	String FAIL_PRE = "fail_";
	String WARN_PRE = "warn_";
	String SERIOUS_PRE = "serious_warn_";

	String pass_log;
	String fail_log;
	String warn_log;
	String serious_warn_log;

	private String _sqlFile;

	private JDBCConn _cur_conn_mysql;
	private JDBCConn _cur_conn_uproxy;

	public ExecSQLAndCompare(String sqlFile) {
		_sqlFile = sqlFile;
		// TODO Auto-generated constructor stub
		initData();

	}

	private void initData() {
		pass_log = PASS_PRE + _sqlFile;
		fail_log = FAIL_PRE + _sqlFile;
		warn_log = WARN_PRE + _sqlFile;
		serious_warn_log = SERIOUS_PRE + _sqlFile;
	}

	public void analyzeSql() {
		String sql = "";
		int line_nu = 0;
		Boolean is_multiline = false;
		Boolean toClose = true;

		String full_path = Config.getSqlPath() + _sqlFile;
		System.out.println(full_path);
		File file = new File(full_path);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;
			String nextLine = reader.readLine().trim();
			String line = nextLine;
			while (nextLine != null) {
				line = nextLine.trim();
				nextLine = reader.readLine();

				line_nu += 1;
				if (line.startsWith("#")) {
					// when meet a line starts with #, reset state variables
					is_multiline = false;
					toClose = true;

					if (line.startsWith("#!share_conn")) {
						if (line.contains("#!multiline"))
							is_multiline = true;
						toClose = false;
						Pattern p = Pattern.compile("share_conn_\\d+");
						Matcher m = p.matcher(line);
						if (m.find()) {
							String uproxy_conn_name = m.group(1);
							String mysql_conn_name = uproxy_conn_name + "_mysql";
							if (!share_conns_uproxy.containsKey(uproxy_conn_name)) {
								JDBCConn conn_uproxy = new JDBCConn(Config.Host_Uproxy, Config.TEST_USER,
										Config.TEST_USER_PASSWD, Config.TEST_DB, Config.UPROXY_PORT);
								JDBCConn conn_mysql = new JDBCConn(Config.Host_Single_MySQL, Config.TEST_USER,
										Config.TEST_USER_PASSWD, Config.TEST_DB, Config.MYSQL_PORT);

								share_conns_uproxy.put(uproxy_conn_name, conn_uproxy);
								share_conns_mysql.put(mysql_conn_name, conn_mysql);
							}
							System.out.println("conntions to exec sql:" + uproxy_conn_name);
							_cur_conn_mysql = share_conns_mysql.get(mysql_conn_name);
							_cur_conn_uproxy = share_conns_uproxy.get(uproxy_conn_name);
						} else {
							check_destroy_old_conn();
							_cur_conn_uproxy = new JDBCConn(Config.Host_Uproxy, Config.TEST_USER,
									Config.TEST_USER_PASSWD, Config.TEST_DB, Config.UPROXY_PORT);
							_cur_conn_mysql = new JDBCConn(Config.Host_Single_MySQL, Config.TEST_USER,
									Config.TEST_USER_PASSWD, Config.TEST_DB, Config.MYSQL_PORT);
							System.out.println("open a pair of new conntions to exec sql");
						}
					} else if (line.startsWith("#!restart-mysql")) {
						String[] partitions = line.split("::", 2);
						String str = partitions[1].trim();
						String options = str.substring(1, str.length() - 1);
						restartMysql(options);
						updateConns();
						reconnectUproxy();
					} else if (line.startsWith("#!restart-uproxy")) {
						String[] partitions = line.split("::", 2);
						String str = partitions[1].trim();
						String options = str.substring(1, str.length() - 1);
						restartUproxy(options);
					} else if (line.startsWith("#!multiline")) {
						is_multiline = true;
					}
					continue;
				}
				sql = sql + line + "\n";
				Boolean is_multiline_over = (nextLine == null) || (is_multiline && nextLine.startsWith("#"));
				if (!is_multiline || is_multiline_over) {
					do_query(line_nu, sql, toClose);
					sql = "";
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
	}

	private void check_destroy_old_conn() {
		if (_cur_conn_mysql != null) {
			Boolean destroyOldConn = true;

			for (int i = 0; i < share_conns_mysql.size(); ++i) {
				if (share_conns_mysql.containsValue(_cur_conn_mysql)) {
					destroyOldConn = false;
					break;
				}
			}
			if (destroyOldConn) {
				_cur_conn_mysql.close();
				_cur_conn_mysql = null;

				_cur_conn_uproxy.close();
				_cur_conn_uproxy = null;
			}
		}
	}

	private void restartMysql(String options) {
		String stop_cmd = Config.MYSQL_INSTALL_PATH + "/support-files/mysql.server stop";
		String start_cmd = Config.MYSQL_INSTALL_PATH + "/support-files/mysql.server start" + options;

		for (int i = 0; i < Config.mysql_hosts.length; ++i) {
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.mysql_hosts[i], Config.SSH_USER,
					Config.SSH_PASSWORD);
			sshExecutor.execute(stop_cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
		for (int i = 0; i < Config.mysql_hosts.length; ++i) {
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.mysql_hosts[i], Config.SSH_USER,
					Config.SSH_PASSWORD);
			sshExecutor.execute(start_cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
	}

	private void updateConns() {
		String precmd = Config.getUproxyAdminCmd();

		String cmd1 = precmd + "uproxy update_conns '" + Config.TEST_USER + "' masters '" + Config.Host_Master + ":"
				+ Config.MYSQL_PORT + "'\"";
		String cmd2 = precmd + "uproxy update_conns '" + Config.TEST_USER + "' slaves '" + Config.Host_Slave1 + ":"
				+ Config.MYSQL_PORT + "'\"";
		String cmd3 = precmd + "uproxy update_conns '" + Config.TEST_USER + "' slaves '" + Config.Host_Slave2 + ":"
				+ Config.MYSQL_PORT + "'\"";

		SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.Host_Uproxy, Config.SSH_USER,
				Config.SSH_PASSWORD);

		sshExecutor.execute(cmd1);
		sshExecutor.execute(cmd2);
		sshExecutor.execute(cmd3);
	}

	private void reconnectUproxy() {
		JDBCConn conn_uproxy = null;
		int max_try = 5, interval = 30;
		Boolean success = false;
		while (max_try > 0) {
			Config.sleep(interval);
			try {
				conn_uproxy = new JDBCConn(Config.Host_Uproxy, Config.TEST_USER, Config.TEST_USER_PASSWD, "",
						Config.UPROXY_PORT);
				success = true;
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (success) {
					break;
				} else {
					max_try--;
				}
				if (conn_uproxy != null) {
					conn_uproxy.close();
					conn_uproxy = null;
				}
			}
		}

		if (!success)
			System.out.println("can not connect to uproxy after " + max_try * interval + " seconds wait");
	}

	private void restartUproxy(String options) {
		String[] ary = options.split(",", 2);
		Map<String, String> opt_dic = new HashMap<String, String>();
		for (int i = 0; i < ary.length; ++i) {
			String items = ary[i].trim();
			String[] subStr = items.split(":", 2);
			opt_dic.put(subStr[0], subStr[1]);
		}

		if (opt_dic.containsKey("default_bconn_limit")) {
			String cmd = "sed -i '/default_bconn_limit/s/[0-9][0-9]*/" + opt_dic.get("default_bconn_limit") + "/' "
					+ Config.UPROXY_INSTALL_PATH + "/uproxy.json";
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.Host_Uproxy, Config.SSH_USER,
					Config.SSH_PASSWORD);
			sshExecutor.execute(cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
		if (opt_dic.containsKey("smp")) {
			String cmd = "sed -i '/\"smp\":/s/[0-9]/" + opt_dic.get("smp") + "/' " + Config.UPROXY_INSTALL_PATH
					+ "/uproxy.json";
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Config.Host_Uproxy, Config.SSH_USER,
					Config.SSH_PASSWORD);
			sshExecutor.execute(cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}

		Setup.getInstance().prepare();
	}

	private void do_query(int line_nu, String sql, Boolean toClose) {
		sql = sql.trim();
		if (sql.length() == 0)
			return;
		if (toClose)
			check_destroy_old_conn();

		if (_cur_conn_uproxy == null) {
			_cur_conn_uproxy = new JDBCConn(Config.Host_Uproxy, Config.TEST_USER, Config.TEST_USER_PASSWD,
					Config.TEST_DB, Config.UPROXY_PORT);
			_cur_conn_mysql = new JDBCConn(Config.Host_Single_MySQL, Config.TEST_USER, Config.TEST_USER_PASSWD,
					Config.TEST_DB, Config.MYSQL_PORT);
		}

		Boolean reset_autocommit = false;
		if (sql.endsWith("#!autocommit=False")) {
			reset_autocommit = true;
			sql = sql.replace("#!autocommit=False", "").trim();
			try {
				_cur_conn_mysql.connection.setAutoCommit(false);
				_cur_conn_uproxy.connection.setAutoCommit(false);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		Boolean isR = _cur_conn_mysql.execute(sql);
		_cur_conn_uproxy.execute(sql);

		Object result_mysql = null, result_uproxy = null;
		if (null != isR) {
			try {
				if (isR) {
					result_mysql = _cur_conn_mysql.stmt.getResultSet();
					result_mysql = _cur_conn_uproxy.stmt.getResultSet();
				} else {
					result_mysql = _cur_conn_mysql.stmt.getUpdateCount();
					result_uproxy = _cur_conn_uproxy.stmt.getUpdateCount();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		String err_mysql = _cur_conn_mysql.errMsg;
		String err_uproxy = _cur_conn_uproxy.errMsg;

		if (reset_autocommit) {
			try {
				_cur_conn_mysql.connection.setAutoCommit(true);
				_cur_conn_uproxy.connection.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		compare_result(line_nu, sql, result_mysql, result_uproxy, err_mysql, err_uproxy);
		if (toClose) {
			_cur_conn_mysql.close();
			_cur_conn_uproxy.close();
			_cur_conn_mysql = null;
			_cur_conn_uproxy = null;
		}
	}

	private boolean equal(Object set1, Object set2) {
		if (set1 instanceof ResultSet) {
			return equal((ResultSet) set1, (ResultSet) set2);
		}
		boolean b = set1 == set2;
		if (!b) {
			System.err.println("update rows count is not equal:[" + set1 + "," + set2 + "]");
		}
		return b;
	}

	private boolean equal(ResultSet set1, ResultSet set2) {
		try {
			ResultSetMetaData metaData1 = set1.getMetaData();
			ResultSetMetaData metaData2 = set2.getMetaData();
			int columnCount2 = metaData2.getColumnCount();
			int columnCount1 = metaData1.getColumnCount();
			if (columnCount1 != columnCount2) {
				System.err.println("column count is not equal[" + columnCount1 + "," + columnCount2 + "]");
				return false;
			}
			boolean line2 = set2.next();
			boolean line1 = set1.next();
			while (line1 && line2) {
				for (int i = 0; i < columnCount1; i++) {
					String value1 = set1.getString(i);
					String value2 = set2.getString(i);
					if (value1 == null && value2 == null) {
						continue;
					}
					if (value1 == null || value2 == null) {
						System.err.println("value is not null,[" + value1 + "," + value2 + "]");
						return false;
					}
					if (!value1.equals(value2)) {
						System.err.println("value is not null,[" + value1 + "," + value2 + "]");
						return false;
					}
				}
			}
			if (line1 != line2) {
				System.err.println("rows count is not equal");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private void compare_result(int id, String sql, Object mysql_result, Object uproxy_result, String mysql_err,
			String uproxy_err) {
		System.out.println("line:" + id + "  sql:[" + sql + "]");
		Boolean isResultSame = equal(uproxy_result, mysql_result);
		String uproxy_re = "uproxy:[" + uproxy_result + "]\n";

		if (isResultSame) {
			if (mysql_err != null || uproxy_err != null) {
				Boolean isMysqlSynErr = null != mysql_err && mysql_err.contains("You have an error in your SQL syntax");
				Boolean isUproxySynErr = null != uproxy_err
						&& uproxy_err.contains("Syntax error or unsupported sql by uproxy");
				MyWriter writer = null;
				if (mysql_err == uproxy_err || (isMysqlSynErr && isUproxySynErr)) {
					writer = new MyWriter(warn_log);
				} else {
					writer = new MyWriter(serious_warn_log);
				}
				writer.write("===id:" + id + ", sql:[" + sql + "]===\n");
				writer.write("mysql err:" + mysql_err + "\n");
				writer.write("mysql err:" + uproxy_err + "\n");
				writer.close();
			} else {
				MyWriter writer = new MyWriter(pass_log);
				writer.write("===id:" + id + ", sql:[" + sql + "]===\n");
				writer.write(uproxy_re);
				writer.close();
			}
		} else {
			MyWriter writer = new MyWriter(fail_log);
			writer.write("===id:" + id + ", sql:[" + sql + "]===\n");
			writer.write(uproxy_re);
			writer.write("mysql:[" + mysql_result + "]\n");

			if (mysql_err != null)
				writer.write("mysql err:" + mysql_err + "\n");
			if (uproxy_err != null)
				writer.write("uproxy err:" + uproxy_err + "\n");
		}
	}
}
