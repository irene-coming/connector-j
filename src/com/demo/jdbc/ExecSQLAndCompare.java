package com.demo.jdbc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
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
	String BALANCE_PRE = "balance_";

	String pass_log;
	String fail_log;
	String warn_log;
	String serious_warn_log;
	String balance_log;

	private String _sqlFile;

	private JDBCConn _cur_conn_mysql;
	private JDBCConn _cur_conn_uproxy;

	public ExecSQLAndCompare(String sqlFile) {
		_sqlFile = sqlFile;
		// TODO Auto-generated constructor stub
		initData();

	}

	private void initData() {
		Map<String, String> balance = new HashMap<String, String>();
		balance.put("slaves_total", "0");
		balance.put("slave1", "0");
		balance.put("slave2", "0");

		pass_log = PASS_PRE + _sqlFile;
		fail_log = FAIL_PRE + _sqlFile;
		warn_log = WARN_PRE + _sqlFile;
		serious_warn_log = SERIOUS_PRE + _sqlFile;
		balance_log = BALANCE_PRE + _sqlFile;
	}

	public void analyzeSql() {
		String sql = "";
		int line_nu = 0;
		Boolean is_multiline = false;
		Boolean toClose = true;

		File file = new File(_sqlFile);
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;
			String nextLine = reader.readLine().trim();
			String line = nextLine;
			while (nextLine != null) {
				line = nextLine;
				tempString = reader.readLine();
				nextLine = tempString.trim();

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
								JDBCConn conn_uproxy = new JDBCConn(Setup.Host_Uproxy, Setup.TEST_USER,
										Setup.TEST_USER_PASSWD, Setup.TEST_DB, Setup.UPROXY_PORT);
								JDBCConn conn_mysql = new JDBCConn(Setup.Host_Single_MySQL, Setup.TEST_USER,
										Setup.TEST_USER_PASSWD, Setup.TEST_DB, Setup.MYSQL_PORT);

								share_conns_uproxy.put(uproxy_conn_name, conn_uproxy);
								share_conns_mysql.put(mysql_conn_name, conn_mysql);
							}
							System.out.println("conntions to exec sql:" + uproxy_conn_name);
							_cur_conn_mysql = share_conns_mysql.get(mysql_conn_name);
							_cur_conn_uproxy = share_conns_uproxy.get(uproxy_conn_name);
						} else {
							check_destroy_old_conn();
							_cur_conn_uproxy = new JDBCConn(Setup.Host_Uproxy, Setup.TEST_USER, Setup.TEST_USER_PASSWD,
									Setup.TEST_DB, Setup.UPROXY_PORT);
							_cur_conn_mysql = new JDBCConn(Setup.Host_Single_MySQL, Setup.TEST_USER,
									Setup.TEST_USER_PASSWD, Setup.TEST_DB, Setup.MYSQL_PORT);
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
		String stop_cmd = "/usr/local/mysql/support-files/mysql.server stop";
		String start_cmd = "/usr/local/mysql/support-files/mysql.server start" + options;

		for (int i = 0; i < Setup.mysql_hosts.length; ++i) {
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Setup.mysql_hosts[i], "root", "sshpass");
			sshExecutor.execute(stop_cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
		for (int i = 0; i < Setup.mysql_hosts.length; ++i) {
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Setup.mysql_hosts[i], "root", "sshpass");
			sshExecutor.execute(start_cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
	}

	private void updateConns() {
		String cmd1 = "/usr/local/mysql -uadmin -ppassword -h127.0.0.1 -P" + Setup.UPROXY_PORT
				+ "-e \"uproxy update_conns 'user1' masters '172.100.7.4:3306'\"";
		String cmd2 = "/usr/local/mysql -uadmin -ppassword -h127.0.0.1 -P" + Setup.UPROXY_PORT
				+ "-e \"uproxy update_conns 'user1' slaves '172.100.7.5:3306'\"";
		String cmd3 = "/usr/local/mysql -uadmin -ppassword -h127.0.0.1 -P" + Setup.UPROXY_PORT
				+ "-e \"uproxy update_conns 'user1' slaves '172.100.7.6:3306'\"";
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

	private void reconnectUproxy() {
		JDBCConn conn_uproxy = null;
		int max_try = 5;
		while (max_try > 0) {
			try {
				Thread.sleep(30000);
				System.out.print("thread sleep 5 sec! \n");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			try {
				conn_uproxy = new JDBCConn(Setup.Host_Uproxy, Setup.TEST_USER, Setup.TEST_USER_PASSWD, "",
						Setup.UPROXY_PORT);
				break;
			} catch (Exception e) {
				if (conn_uproxy != null) {
					conn_uproxy.close();
					conn_uproxy = null;
				}
			} finally {
				max_try--;
			}
		}

		System.out.println("can not connect to uproxy after 30*5 sec wait");
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
			String cmd = "sed -i '/default_bconn_limit/s/[0-9][0-9]*/" + opt_dic.get("default_bconn_limit")
					+ "/' /usr/local/uproxy/uproxy.json";
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Setup.Host_Uproxy, "root", "sshpass");
			sshExecutor.execute(cmd);

			Vector<String> stdout = sshExecutor.getStandardOutput();
			for (String str : stdout) {
				System.out.println(str);
			}
		}
		if (opt_dic.containsKey("smp")) {
			String cmd = "sed -i '/\"smp\":/s/[0-9]/" + opt_dic.get("smp") + "/' /usr/local/uproxy/uproxy.json";
			SSHCommandExecutor sshExecutor = new SSHCommandExecutor(Setup.Host_Uproxy, "root", "sshpass");
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
		if (sql.length() > 0) {
			if (toClose)
				check_destroy_old_conn();

			if (_cur_conn_uproxy == null)
				_cur_conn_uproxy = new JDBCConn(Setup.Host_Uproxy, Setup.TEST_USER, Setup.TEST_USER_PASSWD,
						Setup.TEST_DB, Setup.UPROXY_PORT);
			_cur_conn_mysql = new JDBCConn(Setup.Host_Single_MySQL, Setup.TEST_USER, Setup.TEST_USER_PASSWD,
					Setup.TEST_DB, Setup.MYSQL_PORT);

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

			ResultSet result_mysql = _cur_conn_mysql.executeQuery(sql);
			String err_mysql = _cur_conn_mysql.errMsg;

			ResultSet result_uproxy = _cur_conn_uproxy.executeQuery(sql);
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
	}

	private void compare_result(int id, String sql, ResultSet mysql_result, ResultSet uproxy_result, String mysql_err,
			String uproxy_err) {
		System.out.println("line:" + id + "  sql:[" + sql + "]");
		Boolean isResultSame = uproxy_result == mysql_result;
		String uproxy_re = "uproxy:[" + uproxy_result + "]\n";

		if (isResultSame) {
			if (mysql_err != null || uproxy_err != null) {
				Boolean isMysqlSynErr = mysql_err.contains("You have an error in your SQL syntax");
				Boolean isUproxySynErr = uproxy_err.contains("Syntax error or unsupported sql by uproxy");
				if (mysql_err == uproxy_err || (isMysqlSynErr && isUproxySynErr)) {
					File file = new File(warn_log);
					BufferedWriter writer = null;
					try {
						writer = new BufferedWriter(new FileWriter(file));
						writer.write("===id:" + id + ", sql:[" + sql + "]===\n");
						writer.write("mysql err:" + mysql_err + "\n");
						writer.write("mysql err:" + uproxy_err + "\n");
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (writer != null) {
							try {
								writer.close();
							} catch (IOException e1) {
								e1.printStackTrace();
							}
						}
					}
				} else {
					MyWriter writer = new MyWriter(serious_warn_log);
					writer.write("===id:" + id + ", sql:[" + sql + "]===\n");
					writer.write("mysql err:" + mysql_err + "\n");
					writer.write("mysql err:" + uproxy_err + "\n");
					writer.close();
				}
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
