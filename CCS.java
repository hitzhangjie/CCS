import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.border.MatteBorder;

import org.snmp4j.smi.UdpAddress;

public class CCS {
	
	// neccessary info to connect to oracle database
	String				url		= null;
	String				username	= null;
	String				password	= null;
	
	// Connection obj shared among threads 
	public static Connection			conn		= null;
	
	// all devices
	public static ArrayList<Device> devices = new ArrayList<Device>();
	
	// whether refresh devices or not
	public static boolean firstTimeRefreshDevices = true;
	
	// threads to access the devices
	public static ArrayList<AccessDevicesThread> childThreadsList = 
			new ArrayList<AccessDevicesThread>();
	
	// task list submitted to ScheduledThreadPoolExecutor
	public static ArrayList<ScheduledFuture<AccessDevicesThread>> taskList = 
			new ArrayList<ScheduledFuture<AccessDevicesThread>>();
	
	// thread pool for access devices
	public static ScheduledThreadPoolExecutor pool_access = 
			new ScheduledThreadPoolExecutor(1000);
	
	// thread pool for update devices
	public static ScheduledThreadPoolExecutor pool_update = 
			new ScheduledThreadPoolExecutor(1);
	
	public CCS( String url, String username, String password ) {
		this.url = url;
		this.username = username;
		this.password = password;
		try {
			// load the oracle driver
			Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
			// initialize the connection to oracle db server
			conn = DriverManager.getConnection(url, username, password);
			System.out.println("CCS initialization success");
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
	
	// 关闭数据库相关对象
	public static void closeStatement(Statement stmt) {
		try {
			//if(stmt!=null && !stmt.isClosed())
			if(stmt!=null)
				stmt.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void closeResultSet(ResultSet rs) {
		try {
			//if(rs!=null && !rs.isClosed())
			if(rs!=null)
				rs.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static boolean ipCheck(String ip) {
        if (ip != null && !ip.isEmpty()) {
            // 定义正则表达式
            String regex = "^(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)\\."
                    + "(1\\d{2}|2[0-4]\\d|25[0-5]|[1-9]\\d|\\d)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(ip);
            if(matcher.matches()) {
            	//System.out.println("matched");
            	return true;
            }
            else {
            	//System.out.println("not matched");
            	return false;
            }
        }
        else
        	return false;
    }
	
	// refresh devices according to devicetab
	public static void refreshDevices() {
		// every element of var devices, is a <interval,devicesInOneGroup> pair
		String sql_devices = "select * from devicetab";
		Statement stmt_devices = null;
		ResultSet rs_devices = null;
		
		try {
			stmt_devices = conn.createStatement();
			stmt_devices.execute(sql_devices);
			rs_devices=stmt_devices.getResultSet();
					
			while(rs_devices.next()) {
				String deviceTag = rs_devices.getString("devicetag").trim();
				String deviceType = rs_devices.getString("typeid").trim();
				String ip = rs_devices.getString("ipaddress").trim();
				
				// ip无效，则跳过当前设备
				if(!ipCheck(ip))
					continue;
				
				int snmpVersion = rs_devices.getInt("snmpVersion");
				int snmpPort = rs_devices.getInt("snmpPort");
				String snmpCommunity = rs_devices.getString("snmpCommunity").trim();
				int interval = rs_devices.getInt("interval");
				
				Device dev = new Device(deviceTag, deviceType, ip, snmpVersion, 
													  snmpPort, snmpCommunity, interval);
				// add dev to devices
				devices.add(dev);
			}
			//System.out.println("total "+devices.size()+" devices");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			closeResultSet(rs_devices);
			closeStatement(stmt_devices);
		}
	}
	
	// create threads to access devices 
	public static void createChildThreads(ArrayList<Device> devices) {
		for (Device dev : devices	) {
			AccessDevicesThread child_t = new AccessDevicesThread(dev);
			childThreadsList.add(child_t	);
		}
	}	
	
	// entry
	public void start() {
		try {
			// whether need to update devices and create new child threads
			CheckTableModifiedThread update_t = 
					new CheckTableModifiedThread(conn);
			pool_update. scheduleWithFixedDelay(
					update_t, 0, 10*1000, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}	
	
	public static void main(String args[]) {

		// neccessary info to connect to oracle database
		String ip = null;
		String port = null;
		String dbname = null;
		String username = null;
		String password = null;
		
		if(args.length!=5) {
			
			System.out.println("usage:");
			System.out.println("java CCS "
					+"<DBServerIP> "
					+"<DBPort> "
					+"<DBName> "
					+"<DBUsername> "
					+"<DBPasswd>");
			
			System.exit(0);
		}
		else {
			ip = args[0].trim();
			port = args[1].trim();
			dbname = args[2].trim();
			username = args[3].trim();
			password = args[4].trim();
		}
		
		// local oracle database
		String url = "jdbc:oracle:thin:@"+ip+":"+port+":"+dbname;
		
		// SnmpRequest, actively capture device info, such as SNMPGET
		CCS ccs = new CCS(url,username,password);
		ccs.start();
		
		SnmpTrap trapReceiver = new SnmpTrap();
		try {
			trapReceiver.listen(new UdpAddress("127.0.0.1/162"));
		}
		catch (IOException e) {
			System.err.println("Error in Listening for Trap");
			System.err.println("Exception Message = " + e.getMessage());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}

