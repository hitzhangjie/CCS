import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Statement;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

class CheckTableModifiedThread implements Runnable {
	// Connection obj shared among threads
	Connection	conn			= null;
	// last timestamp of modification to table devicetab
	Timestamp	lastTimestamp_device	= null;

	public CheckTableModifiedThread(Connection conn) {
		this.conn = conn;
	}

	// return last time when the table 'tableName' is modified
	public Timestamp lastModifiedTime(
			String tableName) {
		
		Statement stmt = null;
		ResultSet rs = null;
		Timestamp ts = null;
		
		try {
			stmt = conn.createStatement();
		
			String sql = "select ora_rowscn from "+tableName;
			stmt.execute(sql);
			rs = stmt.getResultSet();
			// can't determine last mofication timestamp
			if(!rs.next())
				return null;

			sql = "select scn_to_timestamp(max(ora_rowscn)) as" 
					+ " lastModifiedTime from " + tableName;
			//System.out.println(sql);
			stmt.execute(sql);

			rs = stmt.getResultSet();
			if (rs.next())
				ts = rs.getTimestamp("lastModifiedTime");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			CCS.closeResultSet(rs);
			CCS.closeStatement(stmt);
		}

		return ts;
	}

	// whether table has been modified since the specified time
	public boolean isTableModifiedSinceTime(
			String tableName, 
			Timestamp lastTimestamp) {
		
		boolean isModified = false;
		
		Timestamp newModifiedTimestamp = lastModifiedTime(tableName);
		
		if(newModifiedTimestamp==null)
			return true;
		
		if(tableName.equalsIgnoreCase("devicetab")) {
			this.lastTimestamp_device = newModifiedTimestamp;
			
			if(newModifiedTimestamp!=null 
					&& !newModifiedTimestamp.equals(lastTimestamp))
				isModified = true;
		}

		return isModified;
	}

	// get devices from devicetab
	public ArrayList<Device> getDevices() {
		ArrayList<Device> devices = null;
		
		String sql_devices = "select * from devicetab";
		Statement stmt_devices = null;
		ResultSet rs_devices = null;
		
		try {
			stmt_devices = conn.createStatement();
			stmt_devices.execute(sql_devices);
			rs_devices=stmt_devices.getResultSet();
					
			while(rs_devices.next()) {
				if(devices==null)
					devices = new ArrayList<Device>();
				
				String deviceTag = rs_devices.getString("devicetag").trim();
				String deviceType = rs_devices.getString("typeid").trim();
				String ip = rs_devices.getString("ipaddress").trim();
				
				// ip地址无效，则跳过对应的设备
				if(!CCS.ipCheck(ip))
					continue;
				
				int snmpVersion = rs_devices.getInt("snmpVersion");
				int snmpPort = rs_devices.getInt("snmpPort");
				String snmpCommunity = rs_devices.getString("snmpCommunity").trim();
				int interval = rs_devices.getInt("interval");
				
				Device dev = new Device(deviceTag, deviceType, ip, snmpVersion, snmpPort, snmpCommunity, interval);
				
				// add dev to devices
				devices.add(dev);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			CCS.closeResultSet(rs_devices);
			CCS.closeStatement(stmt_devices);
		}
		
		return devices;
	}
	
	// process the new added devices
	synchronized public void processAddedDevices(
			ArrayList<Device> recentDevices) {
		
		for(Device device : recentDevices) {
			String dev_tag = device.deviceTag;
			
			int i = 0;
			for(; i<CCS.devices.size(); i++) {
				if(dev_tag.equals(CCS.devices.get(i).deviceTag))
					break;
			}
			// new added device
			if(i==CCS.devices.size()) {
				// add to CCS.devices
				CCS.devices.add(device);
				
				// 创建新线程访问该设备
				AccessDevicesThread access_t = new AccessDevicesThread(device);
				// 将任务提交至线程池，该access_t将被装换为ScheduledFuture<V>
				ScheduledFuture<AccessDevicesThread> task = 
						(ScheduledFuture<AccessDevicesThread>) 
						CCS.pool_access.scheduleWithFixedDelay(
								access_t, 0, device.interval*1000, TimeUnit.MILLISECONDS);
				// 将access_t保存至childThreadsList
				CCS.childThreadsList.add(access_t);
				// 将转换后的任务保存至taskList，以便以后取消任务
				CCS.taskList.add(task);
			}
		}
	}
	
	// process the deleted devices
	synchronized public void processDeletedDevices(
			ArrayList<Device> recentDevices) {
		
		// 待删除设备的位置，该位置与对应线程列表中的位置对应
		ArrayList<Integer> devicesPosToRemove = new ArrayList<Integer>();
		// 设备对应的列表索引
		int pos = -1;
		
		for(Device devicePrevious : CCS.devices) {
			pos ++;
			boolean isDeleted = true;
			for(Device deviceCurrent : recentDevices) {
				if(deviceCurrent.deviceTag.equals(devicePrevious.deviceTag)) {
					isDeleted = false;
					break;
				}
			}
			
			if(isDeleted) {
				if(CCS.pool_access.remove((Runnable)CCS.taskList.get(pos))) {
					// 添加待删除设备的位置到列表暂存
					devicesPosToRemove.add(pos);
					System.out.println("remove task success");
				}
			}
		}
		for(Integer dev_pos : devicesPosToRemove) {
			// 从线程列表中删除对应的线程
			CCS.childThreadsList.remove(dev_pos);
			// 从原设备列表中删除设备
			CCS.devices.remove(dev_pos);
		}
	}
	
	// process update interval
	synchronized public void processUpdateInterval(
			ArrayList<Device> recentDevices) {
		
		// 设备在设备列表中的索引值
		int pos = -1;
		for(Device devicePrevious : CCS.devices) {
			pos ++;
			for(Device deviceCurrent : recentDevices) {
				// 找到设备编号相同的设备
				if(devicePrevious.deviceTag.equals(deviceCurrent.deviceTag)) {
					// 检查设备的采集周期
					if(devicePrevious.interval!=deviceCurrent.interval) {
						// 更新采集周期
						devicePrevious.interval = deviceCurrent.interval;
						
						// 从线程池中取消该设备对应的任务
						if(CCS.pool_access.remove((Runnable) CCS.taskList.get(pos))) {
							AccessDevicesThread access_t = 
									CCS.childThreadsList.get(pos);
							// 重新调度原访问该设备的线程，得到一个转换后的任务
							ScheduledFuture<AccessDevicesThread> task = 
									(ScheduledFuture<AccessDevicesThread>) 
									CCS.pool_access.scheduleWithFixedDelay(
											access_t, 
											0,
											access_t.dev.interval*1000, 
											TimeUnit.MILLISECONDS);
							// 当调度频率不一样时，同一个任务对应的ScheduledFuture>
							// 也不同，因此更新CCS.taskList
							CCS.taskList.set(pos, task);
							System.out.println("remove task success");
						}
						else
							System.out.println("failed to remove task");						
						
						// exit inner for-loop
						break;
					}
				}
			}
		}
	}
	
	synchronized public void run() {
		
		Timestamp initialTimestamp_device = lastTimestamp_device;

		try {
			// table is modified since initialTimestamp
			boolean isDeviceTABModified = 
					isTableModifiedSinceTime("devicetab", initialTimestamp_device);
			
			if (isDeviceTABModified) {
				System.out.println("DeviceTab is modified ... update");
				
				if(CCS.devices.isEmpty()) {
					// 第一次加载设备信息时，还需要创建任务
					CCS.refreshDevices();
					CCS.createChildThreads(CCS.devices);
					
					// 调度任务
					for (int i=0; i<CCS.childThreadsList.size(); i++) {
						AccessDevicesThread access_t = CCS.childThreadsList.get(i);
						ScheduledFuture<AccessDevicesThread> task = 
								(ScheduledFuture<AccessDevicesThread>) 
								CCS.pool_access.scheduleWithFixedDelay(
										access_t, 
										0,
										access_t.dev.interval*1000, 
										TimeUnit.MILLISECONDS);
						CCS.taskList.add(task);
					}
				}
				else {
					ArrayList<Device> devices = getDevices();	
					
					// has new added devices
					processAddedDevices(devices);
					// has deleted devices
					processDeletedDevices(devices);
					// update interval
					processUpdateInterval(devices);
				}
			}
			else
				System.out.println("...");
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
