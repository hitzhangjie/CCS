import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;
import org.snmp4j.smi.VariableBinding;

/**
 * 访问设备线程类
 * @author ZhangJie
 * 
 */
class AccessDevicesThread implements Runnable {
	
	/**
	 * 待访问的设备
	 */
	public Device dev = null;
	
	/**
	 *  通过SnmpRequest对象访问设备
	 */
	SnmpRequest snmpRequest = null;
	
	/**
	 *  当前次snmp请求的发起\结束时间
	 */
	long accessTimePre = 0;
	long accessTimeCur = 0;
	long timePassed = 0;
	
	
	/**
	 *  构造函数 
	 * @param dev 待访问设备
	 */
	public AccessDevicesThread(Device dev) {
		this.dev = dev;
		try {
			this.snmpRequest = new SnmpRequest(dev);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 *  线程处理函数入口
	 */
	public void run() {
		
		// 检查一下snmpRequest对象是否可用
		if(!snmpRequest.isValid()) {
			System.out.println("当前设备出现SNMP配置问题，无法正常访问");
			return;
		}
		
		// 根据设备类型插入合适的表
		if (dev.deviceType.equals("1") || dev.deviceType.equals("01")) {
			accessHostDevice();
		}
		else if (dev.deviceType.equals("2") || dev.deviceType.equals("02"))
			;
		else if (dev.deviceType.equals("3") || dev.deviceType.equals("03")) {
			accessNetworkDevice();
		}
		else
			;
	}
	
	/**
	 *  对主机设备进行访问
	 */
	public void accessHostDevice() {
		try {
			System.out.println("thread " + Thread.currentThread().toString() + " access 主机设备 " + dev + " begin");

			// 针对获取到的oid列表，发起snmp请求,获取响应
			getHostSnmpResponse();
			
			// 获取到的cpu列表、存储列表、端口列表、设备列表
			 ArrayList<LinkedHashMap<String, String>> interfaceList = null;
			 ArrayList<LinkedHashMap<String, String>> storageList = null;
			 ArrayList<LinkedHashMap<String, String>> deviceList = null;
			 ArrayList<LinkedHashMap<String, String>> cpuList = null;
			 ArrayList<LinkedHashMap<String, String>> x_interfaceList = null;
			 ArrayList<LinkedHashMap<String, String>> ipList = null;
			 ArrayList<LinkedHashMap<String, String>> routeList = null;
			
			 if(tableVec!=null) {
				 interfaceList = tableVec.elementAt(0);
				 storageList = tableVec.elementAt(1);
				 deviceList = tableVec.elementAt(2);
				 cpuList = tableVec.elementAt(3);
				 x_interfaceList = tableVec.elementAt(4);
				 ipList = tableVec.elementAt(5);
				 routeList = tableVec.elementAt(6);
			 }
			 
			 // 存储指标对应的取值<指标名称，<端口描述，值>>
			 LinkedHashMap<String, LinkedHashMap<String, String>> ifIndexValue = null;
			 LinkedHashMap<String, LinkedHashMap<String, String>> linkIndexValue = null;
			 LinkedHashMap<String, String> perfIndexValue = null;
			 LinkedHashMap<String, String> resIndexValue = null;
			 
			 curInterfaceData = new ArrayList<ArrayList<String>>();
				 
			 //数据解析，并将结果存储到curInterfaceData
			 parseIfTableData(interfaceList, curInterfaceData);
			 parseIfXTableData(x_interfaceList, curInterfaceData);
			 parseIpAddTable(ipList, curInterfaceData);
			 parseIpRouteData(routeList, curInterfaceData);
			 
			 //删除没有ip地址的端口
			 curInterfaceData = rmInterfacesNoAddr(curInterfaceData);
			 
			 // 解析网络端口相关指标
			 ifIndexValue = parseSnmpResponse_InterfaceIndex();
			 // 解析线路相关指标
			 linkIndexValue = parseSnmpResponse_LinkIndex();
			 // 解析性能相关指标
			 perfIndexValue = parseSnmpResponse_PerfIndex(cpuList, deviceList, storageList);
			 // 解析资源相关指标
			 resIndexValue = parseSnmpResponse_ResIndex();
			 
			 //synchronized(this) {
				 // 插入网络端口记录
				 insertIndexRecord_IfOrLink(ifIndexValue,1);
				 // 插入线路记录
				 insertIndexRecord_IfOrLink(linkIndexValue,2); 
				 // 插入性能记录
				 insertIndexRecord_PerfOrRes(perfIndexValue, 3);
				 // 插入资源记录
				 insertIndexRecord_PerfOrRes(resIndexValue, 4);
			 //}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			this.getVec = null;
			this.tableVec = null;
			
			this.preInterfaceData = this.curInterfaceData;
			this.curInterfaceData = null;
			
			this.accessTimePre = this.accessTimeCur;
			this.accessTimeCur = 0;
			
			System.out.println("thread " + Thread.currentThread().toString() + " access 主机设备 " + dev + " done");		
		}
	}
	
	/**
	 *  对网络设备进行访问
	 */
	public void accessNetworkDevice() {
		try {
			System.out.println("thread " + Thread.currentThread().toString() + " access 网络设备 " + dev + " begin");

			// 针对获取到的oid列表，发起snmp请求,获取响应
			getNetworkSnmpResponse();
			
			// 获取到的端口列表
			 ArrayList<LinkedHashMap<String, String>> interfaceList = null;
			 ArrayList<LinkedHashMap<String, String>> x_interfaceList = null;		 
			 // ip地址表
			 ArrayList<LinkedHashMap<String, String>> ipList = null;
			 // 路由表
			 ArrayList<LinkedHashMap<String, String>> routeList = null;

			 if(tableVec!=null) {
				 interfaceList = tableVec.elementAt(0);
				 x_interfaceList = tableVec.elementAt(1);
				 ipList = tableVec.elementAt(2);
				 routeList = tableVec.elementAt(3);
			 }
			 	
			 // 存储指标对应的取值<指标名称，<端口描述，值>>
			 LinkedHashMap<String, LinkedHashMap<String, String>> ifIndexValue = null;
			 LinkedHashMap<String, LinkedHashMap<String, String>> linkIndexValue = null;
			 LinkedHashMap<String, String> perfIndexValue = null;
			 LinkedHashMap<String, String> resIndexValue = null;

			 curInterfaceData = new ArrayList<ArrayList<String>>();
			 
			 //数据解析，并将结果存储到curInterfaceData
			 parseIfTableData(interfaceList, curInterfaceData);
			 parseIfXTableData(x_interfaceList, curInterfaceData);
			 parseIpAddTable(ipList, curInterfaceData);
			 parseIpRouteData(routeList, curInterfaceData);
			 
			 curInterfaceData = rmInterfacesNoAddr(curInterfaceData);

			 //解析端口相关指标
			 ifIndexValue = parseSnmpResponse_InterfaceIndex();
			 // 解析线路相关指标
			 linkIndexValue = parseSnmpResponse_LinkIndex();
			 // 解析性能相关指标
			 perfIndexValue = parseSnmpResponse_PerfIndex(null, null, null);
			 // 解析资源相关指标
			 resIndexValue = parseSnmpResponse_ResIndex();
			 
			 // 插入最新记录
			 //synchronized (this) {
				 // 插入网络端口指标记录
				 insertIndexRecord_IfOrLink(ifIndexValue, 1);
				 // 插入线路指标记录
				 insertIndexRecord_IfOrLink(linkIndexValue, 2);
				 // 插入性能指标记录
				 insertIndexRecord_PerfOrRes(perfIndexValue, 3);
				 // 插入资源指标记录
				 insertIndexRecord_PerfOrRes(resIndexValue, 4);
			 //}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			this.getVec = null;
			this.tableVec = null;
			this.preInterfaceData = this.curInterfaceData;
			this.curInterfaceData = null;
			this.accessTimePre = this.accessTimeCur;
			this.accessTimeCur = 0;
			
			System.out.println("thread " + Thread.currentThread().toString() + " access 网络设备 " + dev + " done");		
		}
	}
	
	/********************************数据采集相关实现**********************************/
	
	/**
	 * 保存上次\当前次采集时的端口数据，端口数据是ifTable、ifXTable、ipAddrTable
	 * 以及ipRouteTable的结合
	 */
	ArrayList<ArrayList<String>> preInterfaceData = null;
	ArrayList<ArrayList<String>> curInterfaceData = null;

	/**
	 * ifTable中各个指标的名称，curInterfaceData中存储如下未注释的数据
	 */
	String [] ifTableIndex = {
		"ifIndex",					//Integer
		"ifDescr",					//OctetStr
		"ifType",					//Integer(Enum)
		"ifMtu",						//Integer
		"ifSpeed",					//Gauge32,OF
		"ifPhysAddress",			//OctetsStr
		"ifAdminStatus",			//Integer(Enum)
		"ifOperStatus",			//Integer(Enum)
		"ifLastChange",			//Ticks
		"ifInOctets",				//Counter(Integer32),OF
		"ifInUcastPkts",			//Counter(Integer32),OF
		"ifInNUcastPkts",		//Counter(Integer32),
		"ifInDiscards",			//Counter(Integer32),NOF
		"ifInErrors",				//Counter(Integer32),NOF
		"ifInUnknownProtos",	//Counter(Integer32),NOF
		"ifOutOctets",				//Counter(Integer32),OF
		"ifOutUcastPkts",		//Counter(Integer32),
		"ifOutNUcastPkts",		//Counter(Integer32),
		"ifOutDiscards",			//Counter(Integer32),NOF
		"ifOutErrors",				//Counter(Integer32),NOF
		"ifOutQLen",				//Gauge
		"ifSpecific"					//Object
	};	// 22 index
	
	/**
	 * ifXTable中各个指标的名称，curInterfaceData中保存如下未注释数据
	 */
	String [] ifXTableIndex = {
		"ifName",
		"ifInMulticastPkts",
		"ifInBroadcastPkts",
		"ifOutMulticastPkts",
		"ifOutBroadcastPkts",
		"ifHCInOctets",
		"ifHCInUcastPkts",
		"ifHCInMulticastPkts",
		"ifHCInBroadcastPkts",
		"ifHCOutOctets",
		"ifHCOutUcastPkts",
		"ifHCOutMulticastPkts",
		"ifHCOutBroadcastPkts",
		"ifLinkUpDownTrapEnable",
		"ifHighSpeed",
		"ifPromiscuousMode",
		"ifConnectorPresent",
		"ifAlias",
		"ifCounterDiscontinuityTime"
	};	// 19 index
	
	/**
	 * ipAddrTable中各个指标的名称，curInterfaceData中保存如下未注释数据
	 */
	String [] ipAddrTableIndex = {
		"ipAdEntAddr	",
		"ipAdEntIfIndex",
		"ipAdEntNetMask",
		"ipAdEntBcastAddr",
		"ipAdEntReasmMaxSize"
	};	// 5 index
	
	/**
	 * ipRouetTable中各个指标的名称，curInterfaceData保存如下未注释数据
	 */
	String [] ipRouteTableIndex = {
		//"ipRouteDest",
		//"ipRouteIfIndex",
		//"ipRouteMetric1",
		//"ipRouteMetric2",
		//"ipRouteMetric3",
		//"ipRouteMetric4",
		"ipRouteNextHop",
		//"ipRouteType",
		//"ipRouteProto",
		//"ipRouteAge",
		//"ipRouteMask",
		//"ipRouteMetric5",
		//"ipRouteInfo"
	};	// 1 index
	
	
	/**************************解析表格数据到curInterfaceData***********************/
	
	/**
	 * 解析ifTable表格数据，将结果存储到curInterfaceData
	 * @param ifTable
	 * @param curInterfaceData
	 */
	public void parseIfTableData(
			ArrayList<LinkedHashMap<String, String>> ifTable,
			ArrayList<ArrayList<String>> curInterfaceData
			) {
		
		if(curInterfaceData==null)
			return;
			
		//ifTable为null，或者为空，无需处理
		if(ifTable==null || ifTable.isEmpty())
			return;

		//开始解析
		for(LinkedHashMap<String, String> one_interface : ifTable) {
			ArrayList<String> ifDetails = new ArrayList<String>();
			//准备对当前网络端口的详细信息进行解析
			for(Map.Entry<String, String> one_index : one_interface.entrySet()) {
				String oid = one_index.getKey();
				String value = one_index.getValue();
				
				//ifDescr可能是16进制
				if(oid.contains("1.3.6.1.2.1.2.2.1.2"))
					value = convertFromHexToAscii(value);

				//有的指标字段是用16进制表示的，转ascii存储
				ifDetails.add(value);
			}
			//取回的表格型数据可能不完整，为了避免不完整的表格数据对后续处理造成破
			//坏，这里将不完整的行数据进行填充，是数据的数量与ifTable中每行指标的数
			//量相同
			int len = ifDetails.size();
			while(len++<ifTableIndex.length)
				ifDetails.add(null);
			
			curInterfaceData.add(ifDetails);
		}
	}

	/**
	 * 解析ifXTable数据，将结果追加到curInterfaceData
	 * @param ifXTable snmp返回的ifXTable表格
	 * @param curInterfaceData
	 */
	public void parseIfXTableData(
			ArrayList<LinkedHashMap<String, String>> ifXTable,
			ArrayList<ArrayList<String>> curInterfaceData
			) {
		
		/*
		为了防止ifTable中数据溢出，需要解析ifXTable中数据，并在必要时用 ifXTable中
		的64位精度值，代替ifTable中的32位精度值;
		由于对于不同厂商的设备对snmp agent的实现有所不同，我们无法对不同厂商的设
		备使用同一个阈值，来界定何时使用ifTable中的数据，何时使用ifXTable中的数据，
		因此我们采用这样一种策略，只要ifXTable中存在有效数据，那么我们就采用
		ifXTable中的数据，反之使用ifTable中的数据;
		即便如此，由于ifTable与ifXTable中数据是通过行编号来对应，ifXTable中没有再设
		置索引ifIndex，因此当ifXTable出现数据不完整的时候，使用ifTable中的数据，仍
		然存在溢出的风险
		*/

		//curInterfaceData中无数据，无需处理
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return;
		
		//由于ifXTable中的某些指标字段可能是not availabe，这里的not available并不是
		//snmp agent返回的值，而是表示对应的指标没有被snmp返回，为了统一操作，
		//我们先对数据进行填充，然后对返回的有效数据进行更新
		for(ArrayList<String> one_interface : curInterfaceData) {
			int ifxtable_len = ifXTableIndex.length;
			int len = 0;
			while(len++<ifxtable_len)
				one_interface.add(null);
		}
		
		//ifXTable无数据，跳过后续处理
		if(ifXTable==null || ifXTable.isEmpty())
			return;
		
		//创建一个辅助索引，记录网络端口在ifXTable中的索引
		int ifIndex = 0;
		
		//准备解析
		for(LinkedHashMap<String, String> one_interface : ifXTable) {
			//ifTable中，ifIndex索引从1开始计数，为了与其保持一致，将变量+1
			ifIndex ++;
	
			ArrayList<String> if_matched = null;
			if(ifIndex>=0 && ifIndex<=curInterfaceData.size())
				if_matched = curInterfaceData.get(ifIndex-1);
			else
				continue;
			
			for(Map.Entry<String, String> one_index : one_interface.entrySet()) {
				
				String oid = one_index.getKey();
				String value = one_index.getValue();
			
				String str1 = oid.substring(0, oid.lastIndexOf("."));
				String str2 = str1.substring(str1.lastIndexOf(".")+1, str1.length());
				int indexNum = Integer.parseInt(str2);
				
				if_matched.set(ifTableIndex.length+indexNum-1, value);
			}
		}
	}

	/**
	 * 解析ipAddrTable中的数据，将结果追加到curInterfaceData
	 * @param ipAddrTable snmp返回的网络端口的地址表
	 * @param curInterfaceData
	 */
	public void parseIpAddTable(
			ArrayList<LinkedHashMap<String, String>> ipAddrTable,
			ArrayList<ArrayList<String>> curInterfaceData
			) {
		
		//curInterfaceData没有数据，无需后续处理，但需填充数据
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return;
		
		//ipAddrTable无数据，无需后续处理，但需填充数据
		if(ipAddrTable==null || ipAddrTable.isEmpty()) {
			
			//填充不完整的表格数据
			for(ArrayList<String> one_interface : curInterfaceData) {
				int exact_len =
						 ifTableIndex.length+ifXTableIndex.length+ipAddrTableIndex.length;
				int cur_len = one_interface.size();
				while(cur_len++<exact_len)
					one_interface.add(null);
			}
			return;
		}
		
		//准备解析
		for(LinkedHashMap<String, String> one_addr : ipAddrTable) {
			
			ArrayList<String> if_matched = null;
			
			String ipAdEntAddr = null;
			String ipAdEntifIndex = null;
			String ipAdEntNetMask = null;
			String ipAdEntBcastAddr = null;
			String ipAdEntReasmMaxSize = null;
			
			for(Map.Entry<String, String> one_index : one_addr.entrySet()) {
				String oid = one_index.getKey();
				String value = one_index.getValue();
				
				if(oid.contains("1.3.6.1.2.1.4.20.1.1")) {
					// ip address
					ipAdEntAddr = value;
				}
				else if(oid.contains("1.3.6.1.2.1.4.20.1.2")) {
					//ipAdEntIfIndex, equals to ifTable.ifIndex
					ipAdEntifIndex = value;
					int ifIndex = Integer.parseInt(value);
					if(ifIndex>=0 && ifIndex<=curInterfaceData.size()) {
						if_matched = curInterfaceData.get(ifIndex-1);
					}
				}
				else if(oid.contains("1.3.6.1.2.1.4.20.1.3")) {
					ipAdEntNetMask = value;
				}
				else if(oid.contains("1.3.6.1.2.1.4.20.1.4")) {
					ipAdEntBcastAddr = value;
				}
				else if(oid.contains("1.3.6.1.2.1.4.20.1.5")) {
					ipAdEntReasmMaxSize = value;
					
					if(if_matched!=null) {
						if_matched.add(ipAdEntAddr);
						if_matched.add(ipAdEntifIndex);
						if_matched.add(ipAdEntNetMask);
						if_matched.add(ipAdEntBcastAddr);
						if_matched.add(ipAdEntReasmMaxSize);
					}
				}
				else {
					//不应该到达这里
					;
				}
				
			}
		}
		//填充不完整的表格数据，由于在寻找匹配的if_matched的时候可能失败，所以这里
		//迭代curInterfaceData，填充不完整的表格数据
		for(ArrayList<String> one_interface : curInterfaceData) {
			int exact_len =
					 ifTableIndex.length+ifXTableIndex.length+ipAddrTableIndex.length;
			int cur_len = one_interface.size();
			while(cur_len++<exact_len)
				one_interface.add(null);
		}
	}
	
	/**
	 * 解析ipRouteTable数据，将处理结果追加到curInterfaceData
	 * @param ipRouteTable snmp返回的路由表
	 * @param curInterfaceData
	 */
	//解析ipRouteTable数据
	//注意，只为网络端口添加下一跳的信息，其他路由相关信息一概不加
	public void parseIpRouteData(
			ArrayList<LinkedHashMap<String, String>> ipRouteTable,
			ArrayList<ArrayList<String>> curInterfaceData
			) {
		
		//curInterfaceData无数据，无需处理
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return;
		
		//路由表无数据，无需后续处理，但需填充数据
		if(ipRouteTable==null || ipRouteTable.isEmpty()) {
			
			//填充不完整的行数据
			for(ArrayList<String> one_interface : curInterfaceData) {
				int exact_len = ifTableIndex.length+ifXTableIndex.length
						+ipAddrTableIndex.length+ipRouteTableIndex.length;
				int cur_len = one_interface.size();
				while(cur_len++<exact_len)
					one_interface.add(null);
			}
			return;
		}
		
		//准备解析
		for(LinkedHashMap<String, String> one_route : ipRouteTable) {
			
			ArrayList<String> if_matched = null;
			String ipRoutefIndex = null;
			for(Map.Entry<String, String> one_detail : one_route.entrySet()) {
				String oid = one_detail.getKey();
				String value = one_detail.getValue();
				
				if(oid.contains("1.3.6.1.2.1.4.21.1.2")) {
					//ipRouteIfIndex
					ipRoutefIndex = value;
				}
				else if(oid.contains("1.3.6.1.2.1.4.21.1.7")) {
					//ipRouteNextHop
					
					//一个网路端口，在路由表中可能存在多条路由信息，但是下一跳一定是
					//唯一的，因此在存储下一跳信息之前，应检查是否已经添加过当前网络
					//端口的下一跳信息了
					
					//首先获取匹配的网络端口
					int ifIndex = Integer.parseInt(ipRoutefIndex	);
					if(ifIndex>=0 && ifIndex<=curInterfaceData.size())
						if_matched = curInterfaceData.get(ifIndex-1);
					//检查网络端口信息列表中是否已经存储了下一跳的信息
					if(if_matched!=null  &&  if_matched.size()
					>ifTableIndex.length+ifXTableIndex.length+ipAddrTableIndex.length) {
						//已经保存了下一跳的信息
						continue;
					}
					else if(if_matched!=null){
						//还未保存下一跳的信息
						if_matched.add(value);
					}
				}
			}
		}
		//填充不完整的行数据
		for(ArrayList<String> one_interface : curInterfaceData) {
			int exact_len = ifTableIndex.length+ifXTableIndex.length
					+ipAddrTableIndex.length+ipRouteTableIndex.length;
			int cur_len = one_interface.size();
			while(cur_len++<exact_len)
				one_interface.add(null);
		}
	}
	
	/**
	 * 从curInterfaceData中删除没有ip地址的端口
	 * @param curInterfaceData
	 * @return 返回将curInterfaceData中没有ip地址的端口删除后的剩余数据
	 */
	public ArrayList<ArrayList<String>> rmInterfacesNoAddr(
			ArrayList<ArrayList<String>> curInterfaceData
			) { 
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
	
		ArrayList<ArrayList<String>> resultData = 
				new ArrayList<ArrayList<String>>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			String ipAddress = getIfIpAddr(one_interface);
			if(ipAddress!=null)
				resultData.add(one_interface);
			else
				resultData.add(null);
		}
		
		return resultData;
	}

	/************************解析端口指标时常用工具函数定义***************************/
	
	/**
	 * 查找元素在数组中的索引值（查找时忽略大小写）
	 * @param array 待查找的数组
	 * @param element 待查找的元素
	 * @return 返回元素在数组中第一次出现的索引，找不到返回-1
	 */
	public int indexOf(String [] array, String element) {
		
		for(int i=0; i<array.length; i++) {
			if(array[i].trim().equalsIgnoreCase(element))
				return i;
		}
		return -1;
	}

	/**
	 * 获取指定网络端口的描述信息 
	 * @param target_if 待处理的网络端口
	 * @return 找到则返回描述信息，找不到返回null
	 */
	public String getIfDescr(ArrayList<String> target_if) {
		
		return target_if.get(indexOf(ifTableIndex, "ifDescr"));
	}
	
	/**
	 * 获取指定网络端口的索引值
	 * @param target_if 待处理的网络端口
	 * @return 返回网络端口在ifTable中的索引值，找不到返回-1
	 */
	public int getIfIndex(ArrayList<String> target_if) {
		
		String ifIndex = target_if.get(indexOf(ifTableIndex, "ifIndex"));
		if(ifIndex==null)
			return -1;
		else
			return Integer.parseInt(ifIndex);
	}
	
	/**
	 * 获取指定网络端口的带宽
	 * @param target_if 待处理的网络端口
	 * @return 返回网络端口的当前网络带宽（bps），找不到返回-1
	 */
	public long getIfBandwidth(ArrayList<String> target_if) {
		
		//获取带宽的话，从ifTable或者ifXTable都是可以的，如果从ifXTable中可以得到，
		//就用ifHighSpeed代替ifTable中的ifSpeed，防止溢出；注意ifHighSpeed的单位
		//是Mbps
		String s_ifHcSpeed = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHighSpeed"));
		String s_ifSpeed = target_if.get(indexOf(ifTableIndex, "ifSpeed")); 
		
		if(s_ifHcSpeed!=null)
			return Long.parseLong(s_ifHcSpeed);
		else if(s_ifSpeed!=null)
			return Long.parseLong(s_ifSpeed);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口接收字节的数量
	 * @param target_if 待处理的网络端口
	 * @return 返回网络端口的接收字节的总数量，找不到返回-1
	 */
	public long getIfInOctets(ArrayList<String> target_if) {
		
		String s_ifHCInOctets = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCInOctets"));
		String s_ifInOctets = target_if.get(indexOf(ifTableIndex, "ifInOctets"));
		if(s_ifHCInOctets!=null)
			return Long.parseLong(s_ifHCInOctets);
		else if(s_ifInOctets!=null)
			return Long.parseLong(s_ifInOctets);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口发送字节的数量
	 * @param target_if
	 * @return 返回网络端口发送字节的数量，找不到返回-1
	 */
	public long getIfOutOctets(ArrayList<String> target_if) {
		
		String s_ifHCOutOctets = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCOutOctets"));
		String s_ifOutOctets = target_if.get(indexOf(ifTableIndex, "ifOutOctets"));
		if(s_ifHCOutOctets!=null)
			return Long.parseLong(s_ifHCOutOctets);
		else if(s_ifOutOctets!=null)
			return Long.parseLong(s_ifOutOctets);
		else
			return -1;
	}
		
	/**
	 * 获取指定网络端口接收包的数量
	 * @param target_if
	 * @return 返回网络端口接收包的数量，找不到返回-1
	 */
	public long getIfInPkts(ArrayList<String> target_if) {
		
		//接收到的包总量为收到的单播包+非单播包的数量
		long sum = 0;
	
		//首先检查ifXTable中是否有对应数据
		String s_hcInUcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCInUcastPkts"));
		String s_hcInMulticastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCInMulticastPkts"));
		String s_hcInBroadcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCInBroadcastPkts"));
		//ifTable中肯定有数据，但可能溢出
		String s_inUcastPkts = target_if.get(indexOf(ifTableIndex, "ifInUcastPkts"));
		String s_inNUcastPkts = target_if.get(indexOf(ifTableIndex, "ifInNUcastPkts"));
		
		if(s_hcInUcastPkts!=null)
			sum += Long.parseLong(s_hcInUcastPkts);
		else if(s_inUcastPkts!=null)
			sum += Long.parseLong(s_inUcastPkts);
		else
			return -1;
			
		if(s_hcInMulticastPkts!=null && s_hcInBroadcastPkts!=null)
			sum += Long.parseLong(
					s_hcInBroadcastPkts)+Long.parseLong(s_hcInMulticastPkts);
		else if(s_inNUcastPkts!=null)
			sum += Long.parseLong(s_inNUcastPkts);
		else
			return -1;
		
		return sum;
	}
		
	/**
	 * 获取指定网络端口发送包的数量
	 * @param target_if
	 * @return 返回指定网络端口发送包的数量，找不到返回-1
	 */
	public long getIfOutPkts(ArrayList<String> target_if) {
		
		//发送出去的包总量为发送的单播包+非单播包的数量
		long sum = 0;
		
		//首先检查ifXTable中是否有对应数据
		String s_hcOutUcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCOutUcastPkts"));
		String s_hcOutMulticastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCOutMulticastPkts"));
		String s_hcOutBroadcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCOutBroadcastPkts"));
		//ifTable中肯定有数据，但可能溢出
		String s_outUcastPkts = target_if.get(indexOf(ifTableIndex, "ifOutUcastPkts"));
		String s_outNUcastPkts = target_if.get(indexOf(ifTableIndex, "ifOutNUcastPkts"));
		
		if(s_hcOutUcastPkts!=null)
			sum += Long.parseLong(s_hcOutUcastPkts);
		else if(s_outUcastPkts!=null)
			sum += Long.parseLong(s_outUcastPkts);
		else
			return -1;
			
		if(s_hcOutMulticastPkts!=null && s_hcOutBroadcastPkts!=null) 
			sum += Long.parseLong(
					s_hcOutBroadcastPkts)+Long.parseLong(s_hcOutMulticastPkts);
		else if(s_outNUcastPkts!=null)
			sum += Long.parseLong(s_outNUcastPkts);
		else
			return -1;
		
		return sum;
	}
	
	/**
	 * 获取指定网络端口接收丢包的数量
	 * @param target_if
	 * @return 返回网络端口接收丢包的数量，找不到返回-1
	 */
	public long getIfInDiscards(ArrayList<String> target_if) {
		
		String s_inDiscardsPkts = target_if.get(indexOf(ifTableIndex, "ifInDiscards"));
		if(s_inDiscardsPkts!=null)
			return Long.parseLong(s_inDiscardsPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口发送丢包的数量
	 * @param target_if
	 * @return 返回网络端口发送丢包的数量，找不到返回-1
	 */
	public long getIfOutDiscards(ArrayList<String> target_if) {
		
		String s_outDiscardsPkts = target_if.get(indexOf(ifTableIndex, "ifOutDiscards"));
		if(s_outDiscardsPkts!=null)
			return Long.parseLong(s_outDiscardsPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口接收错包的数量
	 * @param target_if
	 * @return 返回网络端口接收错包的数量，找不到返回-1
	 */
	public long getIfInErrors(ArrayList<String> target_if) {
	
		String s_inErrorPkts = target_if.get(indexOf(ifTableIndex, "ifInErrors"));
		if(s_inErrorPkts!=null)
			return Long.parseLong(s_inErrorPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口发送错包的数量
	 * @param target_if
	 * @return 返回网络端口发送错包的数量，找不到返回-1
	 */
	public long getIfOutErrors(ArrayList<String> target_if) {
		
		String s_outErrorPkts = target_if.get(indexOf(ifTableIndex, "ifOutErrors"));
		if(s_outErrorPkts!=null)
			return Long.parseLong(s_outErrorPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口接收单播包的数量
	 * @param target_if
	 * @return 返回网络端口接收单播包的数量，找不到返回-1
	 */
	public long getIfInUcastPkts(ArrayList<String> target_if) {
	
		String s_hcInUcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCInUcastPkts"));
		String s_inUcastPkts = target_if.get(indexOf(ifTableIndex, "ifInUcastPkts"));
		if(s_hcInUcastPkts!=null)
			return Long.parseLong(s_hcInUcastPkts);
		else if(s_inUcastPkts!=null)
			return Long.parseLong(s_inUcastPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口发送单播包的数量
	 * @param target_if
	 * @return 返回网络端口发送单播包的数量，找不到返回-1
	 */
	public long getIfOutUcastPkts(ArrayList<String> target_if) {
		
		String s_hcOutUcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCOutUcastPkts"));
		String s_outUcastPkts = target_if.get(indexOf(ifTableIndex, "ifOutUcastPkts"));
		if(s_hcOutUcastPkts!=null)
			return Long.parseLong(s_hcOutUcastPkts);
		else if(s_outUcastPkts!=null)
			return Long.parseLong(s_outUcastPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口接收广播包的数量
	 * @param target_if
	 * @return 返回网络端口接收广播包的数量，找不到返回-1
	 */
	public long getIfInBroadcastPkts(ArrayList<String> target_if) {
		
		String s_hcInBroadcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCInBroadcastPkts"))	;
		String s_inBroadcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifInBroadcastPkts"))	;
		if(s_hcInBroadcastPkts!=null)
			return Long.parseLong(s_hcInBroadcastPkts);
		else if(s_inBroadcastPkts!=null)
			return Long.parseLong(s_inBroadcastPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口发送广播包的数量
	 * @param target_if
	 * @return 返回网络端口发送广播包的数量，找不到返回-1
	 */
	public long getIfOutBroadcastPkts(ArrayList<String> target_if) {
		
		String s_hcOutBroadcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifHCOutBroadcastPkts"));
		String s_outBroadcastPkts = target_if.get(
				ifTableIndex.length+indexOf(ifXTableIndex, "ifOutBroadcastPkts"));
		if(s_hcOutBroadcastPkts!=null)
			return Long.parseLong(s_hcOutBroadcastPkts);
		else if(s_outBroadcastPkts!=null)
			return Long.parseLong(s_outBroadcastPkts);
		else
			return -1;
	}
	
	/**
	 * 获取指定网络端口的工作状态	
	 * @param target_if
	 * @return 返回网络端口的工作状态，找不到返回'端口状态未知'
	 */
	public String getIfStatus(ArrayList<String> target_if) {
		
		String s_ifAdminStatus = target_if.get(indexOf(ifTableIndex, "ifAdminStatus"));
		String s_ifOperStatus = target_if.get(indexOf(ifTableIndex, "ifOperStatus"));
		if(s_ifAdminStatus==null || s_ifOperStatus==null)
			return "端口状态未知";
		
		int ifAdminStatus = Integer.parseInt(s_ifAdminStatus);
		int ifOperStatus = Integer.parseInt(s_ifOperStatus);
		
		String ifStatus = null;
		if(ifAdminStatus==1 && ifOperStatus==1)
			ifStatus = "端口工作正常";
		else if(ifAdminStatus==1 && ifOperStatus==2)
			ifStatus = "端口打开失败";
		else if(ifAdminStatus==2 && ifOperStatus==2)
			ifStatus = "端口关闭";
		else if(ifAdminStatus==3 && ifOperStatus==3)
			ifStatus = "端口测试";
		else
			ifStatus = "端口状态未知";
	
		return ifStatus;
	}
	
	/**
	 * 获取指定网络端口的ip地址
	 * @param target_if
	 * @return 返回网络端口对应的ip地址，找不到返回null
	 */
	public String getIfIpAddr(ArrayList<String> target_if) {
		
		String ipAddress = target_if.get(
				ifTableIndex.length+ifXTableIndex.length
				+indexOf(ipAddrTableIndex, "ipAdEntAddr"));
		return ipAddress;
	}
	
	/**
	 * 获取指定网络端口的下一跳的IP地址
	 * @param target_if
	 * @return 返回网络端口下一跳的ip地址，找不到返回null
	 */
	public String getIfNextHopIp(ArrayList<String> target_if) {
		
		return target_if.get(target_if.size()-1);
	}
	
	/*******************************发送SNMP请求************************************/
	
	/**
	 *  存储Snmp响应，分get响应和getTable响应两种，对get或者table请求分类后，一
	 *  同发送，提高访问效率类型
	 */
	Vector<VariableBinding> getVec = null;
	Vector<ArrayList<LinkedHashMap<String, String>>> tableVec	= null;

	/**
	 *  发送snmp请求，并获取snmp响应
	 */
	public void getHostSnmpResponse() {

		// get指标
		String getOids[] = { ".1.3.6.1.2.1.1.1.0", // sysDescr, 0
		".1.3.6.1.2.1.1.2.0", // sysObjectID, 1
		".1.3.6.1.2.1.1.3.0", // sysUpTime, 2
		".1.3.6.1.2.1.1.4.0", //	sysContact, 3
		".1.3.6.1.2.1.1.5.0	", //	sysName, 4
		".1.3.6.1.2.1.1.6.0", //	sysLocation, 5
		".1.3.6.1.2.1.1.7.0" //	sysServices, 6
		};
		// table指标
		String tableOids[] = { ".1.3.6.1.2.1.2.2", //	ifTable
		".1.3.6.1.2.1.25.2.3", //	hrStorageTable
		".1.3.6.1.2.1.25.3.2", //	hrDeviceTable
		".1.3.6.1.2.1.25.3.3", //	hrProcessorTable
		".1.3.6.1.2.1.31.1.1",	 //	ifXTable
		".1.3.6.1.2.1.4.20",	 // ifAddrTable
		".1.3.6.1.2.1.4.21"	// ifRouteTable
		};

		try {
			// 发送请求，获取响应
			getVec = snmpRequest.sendGetRequest(getOids);
			tableVec = snmpRequest.sendGetTableRequest(tableOids);
			
			accessTimeCur = System.currentTimeMillis();
			if(accessTimePre!=0)
				timePassed = accessTimeCur-accessTimePre;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 发送snmp请求，并获取snmp响应
	public void getNetworkSnmpResponse() {
		// get指标
		String getOids[] = { ".1.3.6.1.2.1.1.1.0", // sysDescr, 0
		".1.3.6.1.2.1.1.2.0", // sysObjectID, 1
		".1.3.6.1.2.1.1.3.0", // sysUpTime, 2
		".1.3.6.1.2.1.1.4.0", //	sysContact, 3
		".1.3.6.1.2.1.1.5.0	", //	sysName, 4
		".1.3.6.1.2.1.1.6.0", //	sysLocation, 5
		".1.3.6.1.2.1.1.7.0" //	sysServices, 6
		};
		// table指标
		String tableOids[] = { ".1.3.6.1.2.1.2.2", //	ifTable
			".1.3.6.1.2.1.31.1.1", 	// ifXTable
			".1.3.6.1.2.1.4.20",		// ifAddrTable
			".1.3.6.1.2.1.4.21"		// ifRouteTable
		};

		try {
			// 发送请求，获取响应
			getVec = snmpRequest.sendGetRequest(getOids);
			tableVec = snmpRequest.sendGetTableRequest(tableOids);
			
			accessTimeCur = System.currentTimeMillis();
			if(accessTimePre!=0)
				timePassed = accessTimeCur-accessTimePre;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/********************************数据库操作相关***********************************/
	
	/**
	 *  获取要插入的表的名称
	 * @param indexType 指明指标的类型，详情请看函数实现
	 * @return 返回要数据库表的名称
	 */
	public String getIndexTableName (int indexType) {
		String indexTableName = null;
		// 主机相关指标
		if(dev.deviceType.equals("1") || dev.deviceType.equals("01")) {
			if(indexType==1)			// 端口指标
				indexTableName = "hostIfIndexValueTab";
			else if(indexType==2)	// 线路指标
				indexTableName  = "hostLinkIndexValueTab";
			else if(indexType==3)	// 性能会标
				indexTableName = "hostPerfIndexValueTab";					
			else if(indexType==4)	// 资源指标
				indexTableName = "hostResIndexValueTab";
		}
		// 网络设备相关指标
		else if(dev.deviceType.equals("3") || dev.deviceType.equals("03")) {
			if(indexType==1)			// 端口指标
				indexTableName = "networkIfIndexValueTab";
			else if(indexType==2)	// 线路指标
				indexTableName  = "networkLinkIndexValueTab";
			else if(indexType==3)	// 性能指标
				indexTableName = "networkPerfIndexValueTab";	
			else if(indexType==4)	// 资源指标
				indexTableName = "networkResIndexValueTab";
		}		
		
		return indexTableName;
	}
	
	/**
	 *  获取所有的网络端口或线路的指标名称（也即表字段名称）
	 * @param indexValue
	 * @return 返回所有的网路端口或者线路的指标名称
	 */
	public ArrayList<String> getIndexName_IfOrLink (
			LinkedHashMap<String, LinkedHashMap<String, String>> indexValue) {
		
		ArrayList<String> indexName = new ArrayList<String>();
		
		for(Map.Entry<String, LinkedHashMap<String, String>> index 
				: indexValue.entrySet()) {
			
			String f_name = index.getKey();
			indexName.add(f_name);
		}
		
		return indexName;
	}
	
	/**
	 *  获取所有的性能指标名称或资源指标名称
	 * @param indexValue
	 * @return 返回所有的性能指标名称或者资源指标名称
	 */
	public ArrayList<String> getIndexName_PerfOrRes (
			LinkedHashMap<String, String> indexValue) {
		
		ArrayList<String> indexName = new ArrayList<String>();
		
		for(Map.Entry<String, String> index : indexValue.entrySet()) {
			String f_name = index.getKey();
			indexName.add(f_name);
		}
		
		return indexName;
	}
	
	/**
	 *  获取所有的网络端口或者线路的名称
	 * @param indexValue
	 * @return 返回所有的网络端口或者线路的名称
	 */
	public ArrayList<String> getInterfaceOrLinkName (
			LinkedHashMap<String, LinkedHashMap<String, String>> indexValue) {
		
		ArrayList<String> interfaceName = new ArrayList<String>();
		
		for(Map.Entry<String, LinkedHashMap<String, String>> index 
				: indexValue.entrySet()) {
			
			if(interfaceName.isEmpty()) {
				LinkedHashMap<String, String> f_value = index.getValue();
				if(f_value!=null) {
					for(Map.Entry<String, String> each : f_value.entrySet()) {
						String ifDescr = each.getKey();
						interfaceName.add(ifDescr);
					}
					break;
				}
			}
		}
		
		return interfaceName;
	}
	
	/**
	 *  查看item是否在list中出现
	 * @param list
	 * @param item
	 * @return 如果item出现在list中返回true，反之返回false
	 */
	public boolean isExist(ArrayList<String> list, String item) {
		boolean exist = false;
		
		for(String one_item : list) {
			if(one_item.equalsIgnoreCase(item)) {
				exist = true;
				break;
			}
		}
		
		return exist;
	}
	
	/**
	 *  生成针对每一个网络端口或者线路的插入记录的SQL
	 * @param interfaceName
	 * @param indexName
	 * @param indexValue
	 * @param indexType
	 * @param indexTableName
	 * @return 返回插入语句对应的SQL
	 */
	public ArrayList<String> getSQLsForInterfaceOrLink(
			ArrayList<String> interfaceName,
			ArrayList<String> indexName,
			LinkedHashMap<String, LinkedHashMap<String, String>> indexValue,
			int indexType,
			String indexTableName) {
		
		// 先检查一下当前设备的哪些网络端口在表中已经有记录了
		ArrayList<String> ifDescrList = new ArrayList<String>();
		Statement stmt_check = null;
		ResultSet rs_check = null;
		
		String descrName = null;
		if(indexType==1)
			descrName = "ifDescr";
		if(indexType==2)
			descrName = "linkDescr";
		
		try {
			String sql_check = null;
			sql_check = "select distinct "+descrName+" from "+indexTableName
					+" where devicetag='"+dev.deviceTag+"'";
			
			stmt_check = CCS.conn.createStatement();
			stmt_check.execute(sql_check);
			rs_check = stmt_check.getResultSet();
			
			while(rs_check.next()) {
				String ifDescr = rs_check.getString(descrName);
				ifDescrList.add(ifDescr);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			CCS.closeResultSet(rs_check);
			CCS.closeStatement(stmt_check);
		}
		
		ArrayList<String> sqls = new ArrayList<String>();
		
		// ifDescr, ---> all index
		for (int i=0; i<interfaceName.size(); i++) {
			// 取得网络端口名称或者线路名称
			String ifDescr = interfaceName.get(i);
			
			// 字段名称和对应的值
			// 插入记录时用
			String sql_ins_fieldNames = "";
			String sql_ins_fieldValues = "";
			// 更新记录时用
			String sql_upd_fields = "";
			
			// insert
			if(!isExist(ifDescrList, ifDescr)) {
				// 获取所有的网络指标的值或者线路指标的值
				for(int j=0; j<indexName.size(); j++) {
					String f_name = indexName.get(j);
					String f_value = null;
					if(indexValue!=null) {
						LinkedHashMap<String, String> f_value_list = 
								indexValue.get(f_name);
						if(f_value_list!=null)
							f_value = f_value_list.get(ifDescr);
					}
					
					sql_ins_fieldNames+=f_name+",";
					// 检查f_value的取值
					if(f_value!=null && !f_value.equalsIgnoreCase("null"))
						sql_ins_fieldValues+="'"+f_value+"',";
					else
						sql_ins_fieldValues+="null,";
				}
				// 拼接插入语句
				String sql_insert ="";
				if(indexType==1)
					sql_insert = "insert into "+indexTableName
						+"(id,devicetag,captime,ifDescr,";
				else if(indexType==2)
					sql_insert = "insert into "+indexTableName
						+"(id,devicetag,captime,linkDescr,";
				
				sql_insert += sql_ins_fieldNames+" capinterval)" +" values(s_"
						+indexTableName+".nextval,'"+dev.deviceTag+"',"+accessTimeCur+",'"
						+ifDescr+"',"+sql_ins_fieldValues+dev.interval+")";
				
				//System.out.println(sql_insert);
				
				sqls.add(sql_insert);
			}
			// update
			else {
				// 获取所有的网络指标的值或者线路指标的值
				for(int j=0; j<indexName.size(); j++) {
					String f_name = indexName.get(j);
					//System.out.println(f_name);
					
					String f_value = null;
					if(indexValue!=null) {
						LinkedHashMap<String, String> f_value_list =
								indexValue.get(f_name);
						if(f_value_list!=null) {
							f_value = f_value_list.get(ifDescr);
						}
					}
					
					sql_upd_fields +=f_name+"=";
					// 检查f_value的取值
					if(f_value!=null && !f_value.equalsIgnoreCase("null"))
						sql_upd_fields +="'"+f_value+"',";
					else
						sql_upd_fields +="null,";
				}
				
				// 拼接更新语句
				String sql_update ="update "+indexTableName+" set "+sql_upd_fields;
				sql_update+="capinterval="+dev.interval +" where devicetag='"
						+dev.deviceTag+"' and "+descrName+"='"+ifDescr+"'";
				
				sqls.add(sql_update);
			}
		}
		//for(String sql : sqls) {
			//System.out.println(sql);
		//}
		
		return sqls;
	}
	
	/**
	 *  生成针对性能或资源指标的插入记录的SQL
	 * @param indexValue
	 * @param indexTableName
	 * @return 返回插入语句对应的SQL
	 */
	public String getSQLsForPerfOrRes(
		LinkedHashMap<String, String> indexValue,
		String indexTableName) {
		
		String insertOrUpdateSQL = null;
		
		// 检查当前设备是否已经在表中
		String sql_check = "select distinct devicetag from "+indexTableName;
		Statement stmt_check = null;
		ResultSet rs_check = null;
		
		ArrayList<String> deviceList = new ArrayList<String>();
		try {
			stmt_check = CCS.conn.createStatement();
			stmt_check.execute(sql_check);
			rs_check = stmt_check.getResultSet();
			while(rs_check.next()) {
				String devicetag = rs_check.getString("devicetag");
				deviceList.add(devicetag);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			CCS.closeResultSet(rs_check);
			CCS.closeStatement(stmt_check);
		}
		
		// 拼接插入语句
		if(!isExist(deviceList, dev.deviceTag)) {
			
			insertOrUpdateSQL = "insert into "+indexTableName
					+"(id,devicetag,captime,";
			
			String sql_fields = "";
			String sql_values = "";
			
			for(Map.Entry<String, String> index : indexValue.entrySet()) {
				String tmp = index.getKey();
				sql_fields += tmp+",";
				
				tmp = index.getValue();
				if(tmp==null || tmp.equalsIgnoreCase("null"))
					sql_values += "null,";
				else
					sql_values += "'"+tmp+"',";
			}
			
			insertOrUpdateSQL += sql_fields+"capinterval)" +" values(s_"
					+indexTableName+".nextval,'"+dev.deviceTag+"',"+System.currentTimeMillis()+","
					+sql_values+dev.interval+")";
		}
		// 拼接更新语句
		else {
			String updateFields = "";
			
			for(Map.Entry<String, String> index : indexValue.entrySet()) {
				String tmp = index.getKey();
				updateFields += tmp+"=";
				tmp= index.getValue();
				if(tmp==null || tmp.equalsIgnoreCase("null"))
					updateFields += "null,";
				else
					updateFields += "'"+tmp+"',";
				
				insertOrUpdateSQL = "update "+indexTableName+" set "+updateFields
						+"capinterval="+dev.interval;
			}
		}
			
		return insertOrUpdateSQL;
	}
		
	/**
	 * 插入网络端口指标记录，插入线路指标记录
	 * @param indexValue 待插入的指标名称以及取值都保存在这个参数中
	 * @param indexType indexType=1表示插入网络端口指标，=2表示插入线路指标
	 */
	public void insertIndexRecord_IfOrLink(

			LinkedHashMap<String, LinkedHashMap<String, String>> indexValue,
			int indexType) {
		
		if(indexValue.isEmpty())
			return;
		
		String indexTableName = null;
		indexTableName = getIndexTableName(indexType);

		// 保存所有的指标名称
		ArrayList<String> indexName = null;
		indexName = getIndexName_IfOrLink(indexValue);
		
		// 保存所有的网络端口名称，或者线路名称
		ArrayList<String> interfaceName = null;
		interfaceName = getInterfaceOrLinkName(indexValue);
		
		ArrayList<String> sqls = getSQLsForInterfaceOrLink(
							interfaceName, indexName, indexValue, indexType, indexTableName);
		
		Statement stmt_insert = null;
		try {
			stmt_insert = CCS.conn.createStatement();
			// add batch
			for(int i=0; i<sqls.size(); i++) {
				
				String sql_insert = sqls.get(i);
				
				if(sql_insert!=null && !sql_insert.isEmpty())
					stmt_insert.addBatch(sql_insert);
			}
			// execute batch
			stmt_insert.executeBatch();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			CCS.closeStatement(stmt_insert);
		}
	}
	
	/**
	 * 插入性能或资源的指标记录
	 * @param indexValue 带插入的性能指标名称以及取值都存放在这个参数中
	 * @param indexType indexType=3表示插入性能指标，=4表示插入资源指标
	 */
	public void insertIndexRecord_PerfOrRes(
			LinkedHashMap<String, String> indexValue,
			int indexType) {
		
		if(indexValue.isEmpty())
			return;
		
		String indexTableName = null;
		indexTableName = getIndexTableName(indexType);

		// 
		String sql_insert = getSQLsForPerfOrRes(indexValue, indexTableName);
		
		Statement stmt_insert = null;
		try {
			stmt_insert = CCS.conn.createStatement();
			//System.out.println(sql_insert);
			stmt_insert.execute(sql_insert);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			CCS.closeStatement(stmt_insert);
		}
	}
	
	/*************************************解析指标值*************************************/
	
	/**
	 *  解析Snmp获取的网络端口相关的原始数据，提取相关指标值
	 * @return 返回网络端口相关的指标值
	 */
	public LinkedHashMap<String, LinkedHashMap<String, String>> 
	parseSnmpResponse_InterfaceIndex() {
		
		// <K,V>, K is indexName, V is values
		LinkedHashMap<String, LinkedHashMap<String, String>> indexValue = 
				new LinkedHashMap<String, LinkedHashMap<String, String>>();
		
		 // 网络端口相关的指标
		 indexValue.put("IfBwUsedRate", getIfBwUsedRate());
		 indexValue.put("IfInBwUsedRate", getIfInBwUsedRate());
		 indexValue.put("IfOutBwUsedRate", getIfOutBwUsedRate());
	 
		 indexValue.put("IfTransmissionRate", getIfTransmissionRate());
		 indexValue.put("IfInTransmissionRate", getIfInTransmissionRate());
		 indexValue.put("IfOutTransmissionRate", getIfOutTransmissionRate());
	 
		 indexValue.put("IfInDropPacketRate", getIfInDropPacketRate());
		 indexValue.put("IfOutDropPacketRate", getIfOutDropPacketRate());
		 indexValue.put("IfDropPacketRate", getIfDropPacketRate());
		 indexValue.put("IfInDropPacketNUM", getIfInDropPacketNUM());
		 indexValue.put("IfOutDropPacketNUM", getIfOutDropPacketNUM());
		 indexValue.put("IfDropPacketNUM", getIfDropPacketNUM());
	 
		 indexValue.put("IfInUnicastPacketFlow", getIfInUnicastPacketFlow());
		 indexValue.put("IfOutUnicastPacketFlow", getIfOutUnicastPacketFlow());
		 indexValue.put("IfUnicastPacketFlow", getIfUnicastFlow());
		 indexValue.put("IfInUnicastPacketNUM", getIfInUnicastPacketNUM());
		 indexValue.put("IfOutUnicastPacketNUM", getIfOutUnicastPacketNUM());
		 indexValue.put("IfUnicastPacketNUM", getIfUnicastNUM());
	 
		 indexValue.put("IfInBroadcastPacketFlow", getIfInBroadcastFlow());
		 indexValue.put("IfOutBroadcastPacketFlow", getIfOutBroadcastFlow());
		 indexValue.put("IfBroadcastPacketFlow", getIfBroadcastFlow());
		 indexValue.put("IfInBroadcastPacketNUM", getIfInBroadcastPacketNUM());
		 indexValue.put("IfOutBroadcastPacketNUM", getIfOutBroadcastNUM());
		 indexValue.put("IfBroadcastPacketNUM", getIfBroadcastNUM());
	 
		 indexValue.put("IfInPacketNUM", getIfInPacketNUM());
		 indexValue.put("IfOutPacketNUM", getIfOutPacketNUM());
		 indexValue.put("IfPacketNUM", getIfPacketNUM());
		 indexValue.put("IfInFlow", getIfInFlow());
		 indexValue.put("IfOutFlow", getIfOutFlow());
		 indexValue.put("IfFlow", getIfFlow());
	 
		 indexValue.put("IfInErrorPacketRate", getIfInErrorPacketRate());
		 indexValue.put("IfOutErrorPacketRate", getIfOutErrorPacketRate());
		 indexValue.put("IfErrorPacketRate", getIfErrorPacketRate());
		 indexValue.put("IfInErrorPacketNUM", getIfInErrorPacketNUM());
		 indexValue.put("IfOutErrorPacketNUM", getIfOutErrorPacketNUM());
		 indexValue.put("IfErrorPacketNUM", getIfErrorPacketNUM());
	 
		 indexValue.put("IfStatus", getIfStatus());
		 indexValue.put("IPAddress", getIfIpAddress());
		
		 return indexValue;
	}

	/**
	 *  解析Snmp获取的链路相关的原始数据，提取相关指标值
	 * @return 返回线路相关的所有的指标值
	 */
	public LinkedHashMap<String, LinkedHashMap<String, String>> 
	parseSnmpResponse_LinkIndex () {
		
		LinkedHashMap<String, LinkedHashMap<String, String>> indexValue = 
				new LinkedHashMap<String, LinkedHashMap<String, String>>();
		
		 // 端口相关的指标
		 indexValue.put("LinkBwUsedRate", getIfBwUsedRate());
		 indexValue.put("LinkInBwUsedRate", getIfInBwUsedRate());
		 indexValue.put("LinkOutBwUsedRate", getIfOutBwUsedRate());
	 
		 indexValue.put("LinkTransmissionRate", getIfTransmissionRate());
		 indexValue.put("LinkInTransmissionRate", getIfInTransmissionRate());
		 indexValue.put("LinkOutTransmissionRate", getIfOutTransmissionRate());
	 
		 indexValue.put("LinkInDropPacketRate", getIfInDropPacketRate());
		 indexValue.put("LinkOutDropPacketRate", getIfOutDropPacketRate());
		 indexValue.put("LinkDropPacketRate", getIfDropPacketRate());
		 indexValue.put("LinkInDropPacketNUM", getIfInDropPacketNUM());
		 indexValue.put("LinkOutDropPacketNUM", getIfOutDropPacketNUM());
		 indexValue.put("LinkDropPacketNUM", getIfDropPacketNUM());
	 
		 indexValue.put("LinkInUnicastPacketFlow", getIfInUnicastPacketFlow());
		 indexValue.put("LinkOutUnicastPacketFlow", getIfOutUnicastPacketFlow());
		 indexValue.put("LinkUnicastPacketFlow", getIfUnicastFlow());
		 indexValue.put("LinkInUnicastPacketNUM", getIfInUnicastPacketNUM());
		 indexValue.put("LinkOutUnicastPacketNUM", getIfOutUnicastPacketNUM());
		 indexValue.put("LinkUnicastPacketNUM", getIfUnicastNUM());
	 
		 indexValue.put("LinkInBroadcastPacketFlow", getIfInBroadcastFlow());
		 indexValue.put("LinkOutBroadcastPacketFlow", getIfOutBroadcastFlow());
		 indexValue.put("LinkBroadcastPacketFlow", getIfBroadcastFlow());
		 indexValue.put("LinkInBroadcastPacketNUM", getIfInBroadcastPacketNUM());
		 indexValue.put("LinkOutBroadcastPacketNUM", getIfOutBroadcastNUM());
		 indexValue.put("LinkBroadcastPacketNUM", getIfBroadcastNUM());
	 
		 indexValue.put("LinkInPacketNUM", getIfInPacketNUM());
		 indexValue.put("LinkOutPacketNUM", getIfOutPacketNUM());
		 indexValue.put("LinkPacketNUM", getIfPacketNUM());
		 indexValue.put("LinkInFlow", getIfInFlow());
		 indexValue.put("LinkOutFlow", getIfOutFlow());
		 indexValue.put("LinkFlow", getIfFlow());
	 
		 indexValue.put("LinkInErrorPacketRate", getIfInErrorPacketRate());
		 indexValue.put("LinkOutErrorPacketRate", getIfOutErrorPacketRate());
		 indexValue.put("LinkErrorPacketRate", getIfErrorPacketRate());
		 indexValue.put("LinkInErrorPacketNUM", getIfInErrorPacketNUM());
		 indexValue.put("LinkOutErrorPacketNUM", getIfOutErrorPacketNUM());
		 indexValue.put("LinkErrorPacketNUM", getIfErrorPacketNUM());
	 
		 indexValue.put("LinkStatus", getIfStatus());
		 
		 indexValue.put("IPAddress", getIfIpAddress());
		 indexValue.put("NextHopIPAddress", getLinkNextHopAddress());
		
		return indexValue;
	}
	
	/**
	 *  解析Snmp获取的性能相关的原始数据，提取相关指标值
	 * @param cpuList
	 * @param deviceList
	 * @param storageList
	 * @return 返回性能相关的指标值
	 */
	LinkedHashMap<String, String>
	parseSnmpResponse_PerfIndex (
			ArrayList<LinkedHashMap<String, String>> cpuList,
			ArrayList<LinkedHashMap<String, String>> deviceList,
			ArrayList<LinkedHashMap<String, String>> storageList) {
		
		LinkedHashMap<String, String> perfIndexValue = 
				new LinkedHashMap<String, String	>();
		
		if(dev.deviceType.equals("1") || dev.deviceType.equals("01")) {
			// 获取cpu平均负载
			perfIndexValue.put("cpuLoad5Min", getCPUAvgLoad(cpuList));
			
			// 获取内存平均负载
			perfIndexValue.put("memLoad5Min", getRAMAvgLoad(storageList));
			
			// 获取系统上线时间
			perfIndexValue.put("sysUpTime", getSysUpTime());
			
			// 获取磁盘分区大小以及使用情况
			perfIndexValue.put("diskUsage", getPartitionReserved(storageList));
			
			// 获取主要部件故障次数
			perfIndexValue.put("partsFaultTimes", getTotalMainPartsFaultTimes(deviceList)+"");
		}
		else if(dev.deviceType.equals("3") || dev.deviceType.equals("03")) {	
			// 获取系统上线时间
			perfIndexValue.put("sysUpTime", getSysUpTime());
		}
		
		return perfIndexValue;
	}
	
	/**
	 *  解析Snmp获取的资源相关的原始数据，提取相关指标值
	 * @return 返回资源相关的指标值
	 */
	LinkedHashMap<String, String>
	parseSnmpResponse_ResIndex () {
		LinkedHashMap<String, String> resIndexValue = 
				new LinkedHashMap<String, String>();
		
		resIndexValue.put("sysDescr", getSysDescr());
		resIndexValue.put("sysContact", getSysContact());
		resIndexValue.put("sysName", getSysName());
		resIndexValue.put("sysLocation", getSysLocation());
		
		return resIndexValue;
	}
	
	/**
	 *  指标可以分为4类：网络端口指标、线路指标、性能指标、资源指标
	 */
	
	/*************************************性能指标***************************************/

	/**
	 * 获取系统上线时间
	 * @return 返回系统上线时间
	 */
	public String getSysUpTime() {
		if(getVec!=null) {
			VariableBinding vb = getVec.elementAt(2);
			if (vb != null && !vb.getVariable().toString().equalsIgnoreCase("no such object"))
				return vb.getVariable().toString();
			else
				return null;
		}
		else
			return null;
	}

	/**
	 *  获取cpu平均负载
	 * @param cpuList
	 * @return 返回cpu平均负载
	 */
	public String getCPUAvgLoad(
			ArrayList<LinkedHashMap<String, String>> cpuList) {
		
		int cpuCoreCount = 0;
		double cpuCoreLoad = 0;
		double cpuAvgLoad = 0;
		
		if(cpuList!=null) {
			for(LinkedHashMap<String, String> core : cpuList) {
				if(core!=null) {
					for(Map.Entry<String, String> col : core.entrySet()) {
						if(col.getKey().contains("1.3.6.1.2.1.25.3.3.1.2.")) {
							cpuCoreCount ++;
							// snmp agent在响应的时候返回的是每个核心的负载百分比
							cpuCoreLoad += Double.parseDouble(col.getValue());
						}
					}
				}
			}
			// 转换成百分比
			if(cpuCoreCount!=0)
				cpuAvgLoad = (double)cpuCoreLoad/(double)cpuCoreCount;
		}
		
		return String.format("%.3f",cpuAvgLoad);
	}
	
	/**
	 *  获取ram平均负载
	 * @param storageList
	 * @return 返回ram平均负载
	 */
	public String getRAMAvgLoad(
			ArrayList<LinkedHashMap<String, String>> storageList) {
		
		double ramAvgLoad = 0;
		
		boolean hasRAMFound = false;
		if(storageList!=null) {
			for(LinkedHashMap<String,String> partition : storageList) {
				if(partition!=null) {
					if(hasRAMFound)
						break;
					
					long size = 0;
					long used = 0;
						
					for(Map.Entry<String,String> col : partition.entrySet()) {
						if(col.getKey().contains("1.3.6.1.2.1.25.2.3.1.3.")) {
							// 分区描述信息
							if(col.getValue().equals("Physical Memory")) {
								hasRAMFound = true;
							}
							else
								break;
						}
						else if(col.getKey().contains("1.3.6.1.2.1.25.2.3.1.5.")) {
							size = Long.parseLong(col.getValue());
						}
						else if(col.getKey().contains("1.3.6.1.2.1.25.2.3.1.6.")) {
							used = Long.parseLong(col.getValue());
							if(size!=0)
								// 转换成百分比
								ramAvgLoad = ((double)used/(double)size)*100;
							
							break;
						}
					}
				}
			}
		}
		return String.format("%.3f",ramAvgLoad);
	}

	/**
	 *  获取指定分区的剩余空间大小
	 * @param partitionName
	 * @param storageList
	 * @return 返回指定分区的剩余空间大小
	 */
	public double getPartitionReserved(
			String partitionName, 
			ArrayList<LinkedHashMap<String, 
			String>> storageList) {
		// 
		boolean hasCalcPartitionReserved = false;

		// 剩余空间大小
		double partitionReserved = -1;

		if (storageList != null) {
			for (LinkedHashMap<String, String> row : storageList) {
				if (row != null) {
					if (hasCalcPartitionReserved)
						break;

					long allocationUnits = 0;
					long size = 0;
					long used = 0;

					for (Map.Entry<String, String> col : row.entrySet()) {

						// 分区描述信息
						if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.3.")) {
							String descr = col.getValue();
							if (!descr.toLowerCase().startsWith(partitionName.toLowerCase())) {
								break;
							}
						}
						// 分配单元，单位字节
						else if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.4.")) {
							allocationUnits = Long.parseLong(col.getValue());
						}
						// 分区总容量
						else if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.5.")) {
							size = Long.parseLong(col.getValue());
						}
						// 已经使用的分区容量
						else if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.6.")) {
							used = Long.parseLong(col.getValue());
							partitionReserved = (double) (allocationUnits * (size - used)) / 1024 / 1024 / 1024;
							hasCalcPartitionReserved = true;

							break;
						}
					}
				}
			}
		}
		return partitionReserved;
	}

	/**
	 *  获取各个分区的剩余空间大小
	 * @param storageList
	 * @return 返回各个分区的剩余空间大小[分区1，空间][分区2，空间]
	 */
	public String getPartitionReserved(
			ArrayList<LinkedHashMap<String, String>> storageList) {
		
		if(storageList==null)
			return null;

		String sysPartitionReserved = "";

		for (LinkedHashMap<String, String> row : storageList) {
			if (row != null) {

				String descr = null;
				long allocationUnits = 0;
				long size = 0;
				long used = 0;
				String currentSysPartitionReserved = null;

				for (Map.Entry<String, String> col : row.entrySet()) {

					// 分区描述信息
					if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.3.")) {
						descr = col.getValue().toUpperCase();
					}
					// 分配单元，单位字节
					else if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.4.")) {
						allocationUnits = Long.parseLong(col.getValue());
					}
					// 分区总容量
					else if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.5.")) {
						size = Long.parseLong(col.getValue());
					}
					// 已经使用的分区容量
					else if (col.getKey().contains("1.3.6.1.2.1.25.2.3.1.6.")) {
						used = Long.parseLong(col.getValue());

						double partitionSize = (double)(allocationUnits*size)/1024/1024/1024;
						double partitionReserved = 0;
						partitionReserved = (double) (allocationUnits*(size-used))/1024/1024/1024;
						currentSysPartitionReserved = "["+descr+","
								+String.format("%.3f",partitionSize)+","
								+String.format("%.3f",partitionReserved)+"]";
						sysPartitionReserved += currentSysPartitionReserved;
						//
						break;
					}
				}
			}
		}
		return sysPartitionReserved;
	}

	/**
	 *  获取主要部件故障次数
	 * @param devicesList
	 * @return 返回系统主要部件故障总次数
	 */
	public int getTotalMainPartsFaultTimes(
			ArrayList<LinkedHashMap<String, String>> devicesList) {
		
		int faultTimes = 0;
		
		if(devicesList!=null) {
			for(LinkedHashMap<String, String> row : devicesList) {
				if(row!=null) {
					
					String deviceTypeStr = null;
					String deviceDescr = null;
					int deviceError = 0;
				
					for(Map.Entry<String, String> col : row.entrySet()) {
						if(col.getKey().contains("1.3.6.1.2.1.25.3.2.1.2.")) { // 设备类型
							deviceTypeStr = col.getValue();
						}
						else if(col.getKey().contains("1.3.6.1.2.1.25.3.2.1.3.")) { // 设备描述
							deviceDescr = col.getValue();
						}
						else if(col.getKey().contains("1.3.6.1.2.1.25.3.2.1.6.")) { // 设备错误
							deviceError = Integer.parseInt(col.getValue());
							faultTimes += deviceError;
						}
					}
				}
			}
		}

		return faultTimes;
	}
	
	/**
	 *  获取各部件故障次数
	 * @param devicesList
	 * @return 返回系统每个部件的故障次数[部件1,故障次数][部件2,故障次数]
	 */
	public String getEachMainPartsFaultTimes(
			ArrayList<LinkedHashMap<String, String>> devicesList) {
		
		if(devicesList==null)
			return null;

		String sysMainPartsFaultTimes = "";

		for(LinkedHashMap<String, String> row : devicesList) {
			if(row!=null) {
				
				String deviceTypeStr = null;
				String deviceDescr = null;
				int deviceError = 0;

				String currentSysMainPartsFaultTimes = null;
			
				for(Map.Entry<String, String> col : row.entrySet()) {
					if(col.getKey().contains("1.3.6.1.2.1.25.3.2.1.2.")) { // 设备类型
						deviceTypeStr = col.getValue();
					}
					else if(col.getKey().contains("1.3.6.1.2.1.25.3.2.1.3.")) { // 设备描述
						deviceDescr = col.getValue();
					}
					else if(col.getKey().contains("1.3.6.1.2.1.25.3.2.1.6.")) { // 设备错误
						deviceError = Integer.parseInt(col.getValue());

						currentSysMainPartsFaultTimes = "["+deviceDescr+","+deviceError+"]";
						sysMainPartsFaultTimes += currentSysMainPartsFaultTimes;
					}
				}
			}
		}
		return sysMainPartsFaultTimes;
	}

	/*********************************** 资源指标***************************************/
	
	/**
	 *  获取系统描述
	 * @return
	 */
	public String getSysDescr() {
		if(getVec!=null) {
			VariableBinding vb = getVec.elementAt(0);
			if (vb != null && !vb.getVariable().toString().equalsIgnoreCase("no such object"))
				return vb.getVariable().toString();
			else
				return null;
		}
		else
			return null;
	}
	
	/**
	 *  获取系统联系人
	 * @return
	 */
	public String getSysContact() {
		if(getVec!=null) {
			VariableBinding vb = getVec.elementAt(3);
			if (vb != null && !vb.getVariable().toString().equalsIgnoreCase("no such object"))
				return vb.getVariable().toString();
			else
				return null;
		}
		else
			return null;
	}
	
	/**
	 *  获取系统名称
	 * @return
	 */
	public String getSysName() {
		if(getVec!=null) {
			VariableBinding vb = getVec.elementAt(4);
			if (vb != null && !vb.getVariable().toString().equalsIgnoreCase("no such object"))
				return vb.getVariable().toString();
			else
				return null;
		}
		else
			return null;
	}
	
	/**
	 *  获取系统位置
	 * @return
	 */
	public String getSysLocation() {
		if(getVec!=null) {
			VariableBinding vb = getVec.elementAt(5);
			if (vb != null && !vb.getVariable().toString().equalsIgnoreCase("no such object"))
				return vb.getVariable().toString();
			else
				return null;
		}
		else
			return null;
	}
	
	/**********************************网络端口指标************************************/
	
	/**
	 *  获取网络端口的ip地址
	 * @return
	 */
	public LinkedHashMap<String, String> getAllIfIpAddress() {
		
		// <ifDescr,ipAddress>
		LinkedHashMap<String, String> ifIpAddressList =
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			 
			String ifDescr = getIfDescr(one_interface);
			String ipAddress = getIfIpAddr(one_interface);
			ifIpAddressList.put(ifDescr, ipAddress);
		}
		
		return ifIpAddressList;
	}
	
	/**
	 *  获取线路的下一跳ip地址
	 * @return
	 */
	public LinkedHashMap<String, String> getLinkNextHopAddress() {
		
		return getIfNextHopIpAddress();
	}
	
	/**
	 *  转换16进制字符串到ASCII字符串
	 * @param ifDescr
	 * @return
	 */
	public String convertFromHexToAscii(String ifDescr) {
		// maybe ifDescr is displayed in hexidemal
		if(ifDescr.contains(":")) {
			String ascii_digit[] = ifDescr.split(":");
			StringBuffer stringbuf = new StringBuffer(ascii_digit.length);
			
			// 移除最后的00，所以长度减一，length-1
			for(int j=0; j<ascii_digit.length-1; j++) {
				// convert from hex to ascii
				stringbuf.append((char)(Integer.parseInt(ascii_digit[j],16)));
			}
			ifDescr = stringbuf.toString();
		}
		return ifDescr;
	}
	
	/**
	 *  1 ifBwUsedRate,  xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfBwUsedRate() {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifBwUsedRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			//没有历史数据，无法计算带宽占用率，设为0
			if(preInterfaceData==null) {
				ifBwUsedRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long bandwidth = getIfBandwidth(one_interface);
			long cur_ifInOctets = getIfInOctets(one_interface);
			long pre_ifInOctets = getIfInOctets(preInterfaceData.get(ifIndex-1));
			long cur_ifOutOctets = getIfOutOctets(one_interface);
			long pre_ifOutOctets = getIfOutOctets(preInterfaceData.get(ifIndex-1));
			
			if(bandwidth==-1 || cur_ifInOctets==-1 || cur_ifOutOctets==-1
					|| pre_ifInOctets==-1 || pre_ifOutOctets==-1	) {
				ifBwUsedRate.put(ifDescr, "0");
				continue;
			}
			
			long octetsRcvd = Math.abs(cur_ifInOctets-pre_ifInOctets);
			long octetsSent = Math.abs(cur_ifOutOctets-pre_ifOutOctets);
			
			double usedRate = 0;
			long timePassed = this.timePassed/1000;
			if(bandwidth!=0 && timePassed!=0) {
				
				// *100, convert to percentage
				// half-duplex
				//usedRate = (((double)octetsRcvd+(double)octetsSent)*8*100)
				//		/((double)bandwidth*(double)timePassed);
				// full-duplex
				usedRate = (Math.max((double)octetsRcvd,(double)octetsSent))*8*100
						/((double)bandwidth*(double)timePassed);
			}
			ifBwUsedRate.put(ifDescr, String.format("%.3f", usedRate));
		}
		return ifBwUsedRate;
	}
	
	/** 
	 * 2 ifInBwUsedRate, usedRate is xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInBwUsedRate() {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifInBwUsedRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInBwUsedRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long bandwidth = getIfBandwidth(one_interface);
			long cur_ifInOctets = getIfInOctets(one_interface);
			long pre_ifInOctets = getIfInOctets(preInterfaceData.get(ifIndex-1));
			long octetsRcvd = Math.abs(cur_ifInOctets-pre_ifInOctets);
			
			if(bandwidth==-1 || cur_ifInOctets==-1 || pre_ifInOctets==-1) {
				ifInBwUsedRate.put(ifDescr, "0");
				continue;
			}
			
			long timePassed = this.timePassed/1000;
			
			double usedRate = 0;
			
			if(bandwidth!=0 && timePassed!=0)
				usedRate = ((double)octetsRcvd*8*100)/((double)bandwidth*(double)timePassed);
			ifInBwUsedRate.put(ifDescr, String.format("%.3f", usedRate));
		}
		return ifInBwUsedRate;
	}
	
	/**
	 *  3 ifOutBwUsedRate, usedRate is xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutBwUsedRate() {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifOutBwUsedRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutBwUsedRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long bandwidth = getIfBandwidth(one_interface);
			long cur_ifOutOctets = getIfOutOctets(one_interface);
			long pre_ifOutOctets = getIfOutOctets(preInterfaceData.get(ifIndex-1));
			long octetsSent = Math.abs(cur_ifOutOctets-pre_ifOutOctets); 
			
			if(bandwidth==-1 || cur_ifOutOctets==-1 || pre_ifOutOctets==-1) {
				ifOutBwUsedRate.put(ifDescr, "0");
				continue;
			}
			
			long timePassed = this.timePassed/1000;
			
			// for out bw
			double usedRate = 0;
			if(bandwidth!=0 && timePassed!=0)
				usedRate = (((double)octetsSent)*8*100)/((double)bandwidth*(double)timePassed);
			ifOutBwUsedRate.put(ifDescr, String.format("%.3f", usedRate));
		}
		return ifOutBwUsedRate;
	}
	
	/**
	 *  4 ifTransmissionRate, transRate xxx bps
	 * @return
	 */
	public LinkedHashMap<String, String> getIfTransmissionRate () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifTransmissionRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifTransmissionRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInOctets = getIfInOctets(one_interface);
			long cur_ifOutOctets = getIfOutOctets(one_interface);
			long pre_ifInOctets = getIfInOctets(preInterfaceData.get(ifIndex-1));
			long pre_ifOutOctets = getIfOutOctets(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInOctets==-1 || cur_ifOutOctets==-1
					|| pre_ifInOctets==-1 || pre_ifOutOctets==-1) {
				ifTransmissionRate.put(ifDescr, "0");
				continue;
			}
			
			long octetsRcvd = Math.abs(cur_ifInOctets-pre_ifInOctets);
			long octetsSent = Math.abs(cur_ifOutOctets-pre_ifOutOctets);
			long timePassed = this.timePassed/1000;
			
			double transRate = 0;
			if(timePassed!=0)
				transRate = (((double)octetsRcvd+(double)octetsSent)*8)/(double)timePassed;
			ifTransmissionRate.put(ifDescr, String.format("%.3f", transRate));
		}
		return ifTransmissionRate;
	}
	
	/**
	 *  5 ifInTransmissionRate, transRate is xxx bps
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInTransmissionRate () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInTransmissionRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInTransmissionRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInOctets = getIfInOctets(one_interface);
			long pre_ifInOctets = getIfInOctets(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInOctets==-1 || pre_ifInOctets==-1) {
				ifInTransmissionRate.put(ifDescr, "0");
				continue;
			}
			
			long octetsRcvd = Math.abs(cur_ifInOctets-pre_ifInOctets);
			long timePassed = this.timePassed/1000;
			
			double transRate = 0;
			if(timePassed!=0)
				transRate = ((double)octetsRcvd)*8/(double)timePassed;
			ifInTransmissionRate.put(ifDescr, String.format("%.3f", transRate));
		}
		return ifInTransmissionRate;
	}
	
	/**
	 *  6 ifOutTransmissionRate, transRate is xxx bps
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutTransmissionRate () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifOutTransmissionRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutTransmissionRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutOctets = getIfOutOctets(one_interface);
			long pre_ifOutOctets = getIfOutOctets(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutOctets==-1 || pre_ifOutOctets==-1) {
				ifOutTransmissionRate.put(ifDescr, "0");
			}
			
			long octetsSent = Math.abs(cur_ifOutOctets-pre_ifOutOctets);
			long timePassed = this.timePassed/1000;
			
			double transRate = 0;
			if(timePassed!=0)
				transRate = ((double)octetsSent)*8/(double)timePassed;
			ifOutTransmissionRate.put(ifDescr, String.format("%.3f", transRate));
		}
		return ifOutTransmissionRate;
	}
	
	/**
	 *  7 ifInDropPacketRate, dropRate is xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInDropPacketRate () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInDropPacketRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInDropPacketRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInDiscards = getIfInDiscards(one_interface);
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInDiscards = getIfInDiscards(preInterfaceData.get(ifIndex-1));
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInDiscards==-1 || cur_ifInPkts==-1
					|| pre_ifInDiscards==-1 || pre_ifInPkts==-1) {
				ifInDropPacketRate.put(ifDescr, "0");
				continue;
			}
			
			long diffInPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			long diffInDiscards = Math.abs(cur_ifInDiscards-pre_ifInDiscards);
			
			double dropRate = 0;
			if(diffInPkts!=0)
				dropRate = (double)diffInDiscards/(double)diffInPkts;
			ifInDropPacketRate.put(ifDescr, String.format("%.3f", dropRate));
		}
		return ifInDropPacketRate;
	}
	
	/**
	 *  8 ifOutDropPacketRate, dropRate is xxx% 
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutDropPacketRate () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifOutDropPacketRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutDropPacketRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutDiscards = getIfOutDiscards(one_interface);
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutDiscards = getIfOutDiscards(preInterfaceData.get(ifIndex-1));
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutDiscards==-1 || cur_ifOutPkts==-1
					|| pre_ifOutDiscards==-1 || pre_ifOutPkts==-1) {
				ifOutDropPacketRate.put(ifDescr, "0");
				continue;
			}
			
			long diffOutPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			long diffOutDiscards = Math.abs(cur_ifOutDiscards-pre_ifOutDiscards);
			
			double dropRate = 0;
			if(diffOutPkts!=0)
				dropRate = (double)diffOutDiscards/(double)diffOutPkts;
			ifOutDropPacketRate.put(ifDescr, String.format("%.3f", dropRate));
		}
		return ifOutDropPacketRate;
	}
	
	/**
	 *  9 ifDropPacketRate, dropRate is xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfDropPacketRate () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifDropPacketRate = 
				new LinkedHashMap<String, String>();
		
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifDropPacketRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInDiscards = getIfInDiscards(one_interface);
			long cur_ifOutDiscards = getIfOutDiscards(one_interface);
			long cur_ifInPkts = getIfInPkts(one_interface);
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifInDiscards = getIfInDiscards(preInterfaceData.get(ifIndex-1));
			long pre_ifOutDiscards = getIfOutDiscards(preInterfaceData.get(ifIndex-1));
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));		
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInDiscards==-1 || cur_ifOutDiscards==-1 || cur_ifInPkts==-1 || cur_ifOutPkts==-1
					|| pre_ifInDiscards==-1 || pre_ifOutDiscards==-1 || pre_ifInPkts==-1 || pre_ifOutPkts==-1) {
				ifDropPacketRate.put(ifDescr, "0");
				continue;
			}
			
			long diffInPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			long diffOutPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			long diffInDiscards = Math.abs(cur_ifInDiscards-pre_ifInDiscards);
			long diffOutDiscards = Math.abs(cur_ifOutDiscards-pre_ifOutDiscards);
			
			double dropRate = 0;
			if(diffInPkts+diffOutPkts!=0)
				dropRate = ((double)diffInDiscards+(double)diffOutDiscards)/((double)diffInPkts+(double)diffOutPkts);
			ifDropPacketRate.put(ifDescr, String.format("%.3f", dropRate));
		}
		return ifDropPacketRate;
	}
	
	/**
	 *  10 ifInDropPacketNUM, dropNUM is pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInDropPacketNUM () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInDropPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInDropPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInDropPkts = getIfInDiscards(one_interface);
			long pre_ifInDropPkts = getIfInDiscards(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInDropPkts==-1 || pre_ifInDropPkts==-1) {
				ifInDropPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long inDropPkts = Math.abs(cur_ifInDropPkts-pre_ifInDropPkts);	
			long timePassed = this.timePassed/1000;
			double dropNUM = 0;
			if(timePassed!=0)
				dropNUM = (double)inDropPkts/(double)timePassed;
			ifInDropPacketNUM.put(ifDescr, String.format("%.3f", dropNUM));
		}
		return ifInDropPacketNUM;
	}
	
	/**
	 *  11 ifOutDropPacketNUM, dropNUM is pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutDropPacketNUM () {
	
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifOutDropPacketNUM = 
				new LinkedHashMap<String, String>();
		 
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutDropPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutDropPkts = getIfOutDiscards(one_interface);
			long pre_ifOutDropPkts = getIfOutDiscards(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutDropPkts==-1 || pre_ifOutDropPkts==-1) {
				ifOutDropPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long outDropPkts = Math.abs(cur_ifOutDropPkts-pre_ifOutDropPkts);
			long timePassed = this.timePassed/1000;
			double dropNUM = 0;
			if(timePassed!=0)
				dropNUM = (double)outDropPkts/(double)timePassed;
			ifOutDropPacketNUM.put(ifDescr, String.format("%.3f", dropNUM));
		}
		return ifOutDropPacketNUM;
	}
	
	/**
	 *  12 ifDropPacketNUM, dropNUM is pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfDropPacketNUM () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifDropPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifDropPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInDropPkts = getIfInDiscards(one_interface);
			long cur_ifOutDropPkts = getIfOutDiscards(one_interface);
			long pre_ifInDropPkts = getIfInDiscards(preInterfaceData.get(ifIndex-1));
			long pre_ifOutDropPkts = getIfOutDiscards(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInDropPkts==-1 || cur_ifOutDropPkts==-1 
					|| pre_ifInDropPkts==-1 || pre_ifOutDropPkts==1) {
				ifDropPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long inDropPkts = Math.abs(cur_ifInDropPkts-pre_ifInDropPkts);
			long outDropPkts = Math.abs(cur_ifOutDropPkts-pre_ifOutDropPkts);
			long timePassed = this.timePassed/1000;
			double dropNUM = 0;
			if(timePassed!=0)
				dropNUM = ((double)inDropPkts+(double)outDropPkts)/(double)timePassed;
			ifDropPacketNUM.put(ifDescr, String.format("%.3f", dropNUM));
		}
		return ifDropPacketNUM;
	}
	
	/**
	 *  13 ifInUnicastPacketFlow, inUnicastFlow is pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInUnicastPacketFlow () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInUnicastPacketFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInUnicastPacketFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInUcastPkts = getIfInUcastPkts(one_interface);
			long pre_ifInUcastPkts = getIfInUcastPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInUcastPkts==-1 || pre_ifInUcastPkts==-1) {
				ifInUnicastPacketFlow.put(ifDescr, "0");
				continue;
			}
			
			long inUcastPkts = Math.abs(cur_ifInUcastPkts-pre_ifInUcastPkts);
			long timePassed = this.timePassed/1000;
			double inUcastFlow = 0;
			if(timePassed!=0)
				inUcastFlow = (double)inUcastPkts/(double)timePassed;
			ifInUnicastPacketFlow.put(ifDescr, String.format("%.3f", inUcastFlow));	
		}
		return ifInUnicastPacketFlow;
	}
	
	/**
	 *  14 ifOutUnicastPacketFlow, outUnicastFlow is pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutUnicastPacketFlow () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifOutUnicastPacketFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutUnicastPacketFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutUcastPkts = getIfOutUcastPkts(one_interface);
			long pre_ifOutUcastPkts = getIfOutUcastPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutUcastPkts==-1 || pre_ifOutUcastPkts==-1) {
				ifOutUnicastPacketFlow.put(ifDescr, "0");
				continue;
			}
			
			long outUcastPkts = Math.abs(cur_ifOutUcastPkts-pre_ifOutUcastPkts);
			long timePassed = this.timePassed/1000;
			double outUcastFlow = 0;
			if(timePassed!=0)
				outUcastFlow = (double)outUcastPkts/(double)timePassed;
			ifOutUnicastPacketFlow.put(ifDescr, String.format("%.3f", outUcastFlow));	
		}
		return ifOutUnicastPacketFlow;
	}
	
	/**
	 *  15 ifUnicastPacketFlow, unicastFlow is Pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfUnicastFlow () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifUnicastPacketFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifUnicastPacketFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInUcastPkts = getIfInUcastPkts(one_interface);
			long pre_ifInUcastPkts = getIfInUcastPkts(preInterfaceData.get(ifIndex-1));
			long cur_ifOutUcastPkts = getIfOutUcastPkts(one_interface);
			long pre_ifOutUcastPkts = getIfOutUcastPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInUcastPkts==-1 || pre_ifInUcastPkts==-1 
					|| cur_ifOutUcastPkts==-1 || pre_ifOutUcastPkts==-1) {
				ifUnicastPacketFlow.put(ifDescr, "0");
				continue;
			}
			
			long inUcastPkts = Math.abs(cur_ifInUcastPkts-pre_ifInUcastPkts);
			long outUcastPkts = Math.abs(cur_ifOutUcastPkts-pre_ifOutUcastPkts);
			long timePassed = this.timePassed/1000;
			double ucastFlow = 0;
			if(timePassed!=0)
				ucastFlow = ((double)inUcastPkts+(double)outUcastPkts)/(double)timePassed;
			ifUnicastPacketFlow.put(ifDescr, String.format("%.3f", ucastFlow));	
		}
		return ifUnicastPacketFlow;
	}
	
	/**
	 *  16 ifInUnicastPacketsNUM, inUnicastNUM is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInUnicastPacketNUM () {
		
		return getIfInUnicastPacketFlow();
	}
	
	/**
	 *  17 ifOutUnicastPacketsNUM, outUnicastNUM is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutUnicastPacketNUM () {
		
		return getIfOutUnicastPacketFlow();
	}
	
	/**
	 *  18 ifUnicastPacketsNUM, unicastNUM is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfUnicastNUM () {
	
		return getIfUnicastFlow();
	}
	
	/**
	 *  19 ifInBroadcastFlow, inBroadcastFlow is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInBroadcastFlow () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifInBroadcastFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInBroadcastFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInBroadcastPkts = getIfInBroadcastPkts(one_interface);
			long pre_ifInBroadcastPkts = getIfInBroadcastPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInBroadcastPkts==-1 || pre_ifInBroadcastPkts==-1) {
				ifInBroadcastFlow.put(ifDescr, "0");
				continue;
			}
			
			long inBcastPkts = Math.abs(cur_ifInBroadcastPkts-pre_ifInBroadcastPkts);
			long timePassed = this.timePassed/1000;
			double inBcastFlow = 0;
			if(timePassed!=0)
				inBcastFlow = (double)inBcastPkts/(double)timePassed;
			ifInBroadcastFlow.put(ifDescr, String.format("%.3f", inBcastFlow));
		}
		return ifInBroadcastFlow;
	}
	
	/**
	 *  20 ifOutBroadcastFlow, outBroadcastFlow is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutBroadcastFlow () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifOutBroadcastFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutBroadcastFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutBroadcastPkts = getIfOutBroadcastPkts(one_interface);
			long pre_ifOutBroadcastPkts = getIfOutBroadcastPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutBroadcastPkts==-1 || pre_ifOutBroadcastPkts==-1) {
				ifOutBroadcastFlow.put(ifDescr, "0");
				continue;
			}
			
			long outBcastPkts = Math.abs(cur_ifOutBroadcastPkts-pre_ifOutBroadcastPkts);
			long timePassed = this.timePassed/1000;
			double outBcastFlow = 0;
			if(timePassed!=0)
				outBcastFlow = (double)outBcastPkts/(double)timePassed;
			ifOutBroadcastFlow.put(ifDescr, String.format("%.3f", outBcastFlow));
		}
		return ifOutBroadcastFlow;
	}
	
	/**
	 *  21 ifBroadcastFlow, broadcastFlow is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfBroadcastFlow () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifBroadcastFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifBroadcastFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInBroadcastPkts = getIfInBroadcastPkts(one_interface);
			long pre_ifInBroadcastPkts = getIfInBroadcastPkts(preInterfaceData.get(ifIndex-1));
			long cur_ifOutBroadcastPkts = getIfOutBroadcastPkts(one_interface);
			long pre_ifOutBroadcastPkts = getIfOutBroadcastPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInBroadcastPkts==-1 || pre_ifInBroadcastPkts==-1
					|| cur_ifOutBroadcastPkts==-1 || pre_ifOutBroadcastPkts==-1) {
				ifBroadcastFlow.put(ifDescr, "0");
				continue;
			}
			
			long inBcastPkts = Math.abs(cur_ifInBroadcastPkts-pre_ifInBroadcastPkts);
			long outBcastPkts = Math.abs(cur_ifOutBroadcastPkts-pre_ifOutBroadcastPkts);
			long timePassed = this.timePassed/1000;
			double bcastFlow = 0;
			if(timePassed!=0)
				bcastFlow = ((double)inBcastPkts+(double)outBcastPkts)/(double)timePassed;
			ifBroadcastFlow.put(ifDescr, String.format("%.3f", bcastFlow));
		}
		return ifBroadcastFlow;
	}
	
	/**
	 *  22 ifInBroadcastPacketNUM, inBroadcastNUM is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInBroadcastPacketNUM () {
		
		return getIfInBroadcastFlow();
	}
	
	/**
	 *  23 ifOutBroadcastPacketNUM, outBroadcastNUM is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutBroadcastNUM () {
		
		return getIfOutBroadcastFlow();
	}
	
	/**
	 *  24 ifBroadcastPacketNUM, broadcastNUM is xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfBroadcastNUM () {
		
		return getIfBroadcastFlow();
	}
	
	/**
	 *  25 ifInPacketNUM, inNUM is xxx pkts
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInPacketNUM () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInPkts==-1 || pre_ifInPkts==-1) {
				ifInPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long inPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			ifInPacketNUM.put(ifDescr, inPkts+"");
		}
		return ifInPacketNUM;
	}
	
	/**
	 *  26 ifOutPacketNUM, outNUM is xxx pkts
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutPacketNUM () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifOutPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutPkts==-1 || pre_ifOutPkts==-1) {
				ifOutPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long outPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			ifOutPacketNUM.put(ifDescr, outPkts+"");
		}

		return ifOutPacketNUM;
	}
	
	/**
	 *  27 ifPacketNUM, xxx pkts
	 * @return
	 */
	public LinkedHashMap<String, String> getIfPacketNUM () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInPkts==-1 || pre_ifInPkts==-1 
					|| cur_ifOutPkts==-1 || pre_ifOutPkts==-1) {
				ifPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			
			long inPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			long outPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			ifPacketNUM.put(ifDescr, (inPkts+outPkts)+"");
		}
		return ifPacketNUM;
	}
	
	/**
	 *  28 ifInFlow, xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInFlow () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInPkts==-1 || pre_ifInPkts==-1) {
				ifInFlow.put(ifDescr, "0");
				continue;
			}
			
			long inPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			long timePassed = this.timePassed/1000;
			double inFlow = 0;
			if(timePassed!=0)
				inFlow = (double)inPkts/(double)timePassed;
			ifInFlow.put(ifDescr, String.format("%.3f", inFlow));
		}
		return ifInFlow;
	}
	
	/**
	 *  29 ifOutFlow, pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutFlow () {
		
		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifOutFlow = 
				new LinkedHashMap<String, String>();
				
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutPkts==-1 || pre_ifOutPkts==-1) {
				ifOutFlow.put(ifDescr, "0");
				continue;
			}
			
			long outPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			long timePassed = this.timePassed/1000;
			double outFlow = 0;
			if(timePassed!=0)
				outFlow = (double)outPkts/(double)timePassed;
			ifOutFlow.put(ifDescr, String.format("%.3f", outFlow));
		}
		return ifOutFlow;
	}
	
	/**
	 *  30 ifFlow, pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfFlow () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifFlow = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifFlow.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInPkts==-1 || pre_ifInPkts==-1
					|| cur_ifOutPkts==-1 || pre_ifOutPkts==-1) {
				ifFlow.put(ifDescr, "0");
				continue;
			}
			
			long inPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			long outPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			long timePassed = this.timePassed/1000;
			double flow = 0;
			if(timePassed!=0)
				flow = ((double)inPkts+(double)outPkts)/(double)timePassed;
			ifFlow.put(ifDescr, String.format("%.3f", flow));
		}
		return ifFlow;
	}
	
	/**
	 *  31 ifInErrorPacketRate, xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInErrorPacketRate () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInErrorPacketRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInErrorPacketRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInErrorPkts = getIfInErrors(one_interface);
			long pre_ifInErrorPkts = getIfInErrors(preInterfaceData.get(ifIndex-1));
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInErrorPkts==-1 || pre_ifInErrorPkts==-1 
					|| cur_ifInPkts==-1 || pre_ifInPkts==-1) {
				ifInErrorPacketRate.put(ifDescr, "0");
				continue;
			}
			
			long inErrorPkts = Math.abs(cur_ifInErrorPkts-pre_ifInErrorPkts);
			long inPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			double inErrorRate = 0;
			if(inPkts!=0)
				inErrorRate = (double)inErrorPkts/(double)inPkts;
			ifInErrorPacketRate.put(ifDescr, String.format("%.3f", inErrorRate));
		}
		return ifInErrorPacketRate;
	}
	
	/**
	 *  32 ifOutErrorPacketRate, xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutErrorPacketRate () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifOutErrorPacketRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutErrorPacketRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutErrorPkts = getIfOutErrors(one_interface);
			long pre_ifOutErrorPkts = getIfOutErrors(preInterfaceData.get(ifIndex-1));
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutErrorPkts==-1 || pre_ifOutErrorPkts==-1 
					|| cur_ifOutPkts==-1 || pre_ifOutPkts==-1) {
				ifOutErrorPacketRate.put(ifDescr, "0");
				continue;
			}
			
			long outErrorPkts = Math.abs(cur_ifOutErrorPkts-pre_ifOutErrorPkts);
			long outPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			double outErrorRate = 0;
			if(outPkts!=0)
				outErrorRate = (double)outErrorPkts/(double)outPkts;
			ifOutErrorPacketRate.put(ifDescr, String.format("%.3f", outErrorRate));
		}
		return ifOutErrorPacketRate;
	}
	
	/**
	 *  33 ifErrorPacketRate, xxx%
	 * @return
	 */
	public LinkedHashMap<String, String> getIfErrorPacketRate () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifErrorPacketRate = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifErrorPacketRate.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInErrorPkts = getIfInErrors(one_interface);
			long pre_ifInErrorPkts = getIfInErrors(preInterfaceData.get(ifIndex-1));
			long cur_ifOutErrorPkts = getIfOutErrors(one_interface);
			long pre_ifOutErrorPkts = getIfOutErrors(preInterfaceData.get(ifIndex-1));
			long cur_ifInPkts = getIfInPkts(one_interface);
			long pre_ifInPkts = getIfInPkts(preInterfaceData.get(ifIndex-1));
			long cur_ifOutPkts = getIfOutPkts(one_interface);
			long pre_ifOutPkts = getIfOutPkts(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInErrorPkts==-1 || pre_ifInErrorPkts==-1
					|| cur_ifOutErrorPkts==-1 || pre_ifOutErrorPkts==-1 
					|| cur_ifInPkts==-1 || pre_ifInPkts==-1
					|| cur_ifOutPkts==-1 || pre_ifOutPkts==-1) {
				ifErrorPacketRate.put(ifDescr, "0");
				continue;
			}
			
			long inErrorPkts = Math.abs(cur_ifInErrorPkts-pre_ifInErrorPkts);
			long outErrorPkts = Math.abs(cur_ifOutErrorPkts-pre_ifOutErrorPkts);
			long inPkts = Math.abs(cur_ifInPkts-pre_ifInPkts);
			long outPkts = Math.abs(cur_ifOutPkts-pre_ifOutPkts);
			long sum = inPkts+outPkts;
			double errorRate = 0;
			if(sum!=0)
				errorRate = ((double)inErrorPkts+(double)outErrorPkts)/(double)sum;
			ifErrorPacketRate.put(ifDescr, String.format("%.3f", errorRate));
		}
		return ifErrorPacketRate;
	}
	
	/**
	 *  34 ifInErrorPacketNUM, xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfInErrorPacketNUM () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifInErrorPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifInErrorPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInErrorPkts = getIfInErrors(one_interface);
			long pre_ifInErrorPkts = getIfInErrors(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInErrorPkts==-1 || pre_ifInErrorPkts==-1) {
				ifInErrorPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long inErrorPkts = Math.abs(cur_ifInErrorPkts-pre_ifInErrorPkts);
			long timePassed = this.timePassed/1000;
			double inErrorNUM = 0;
			if(timePassed!=0)
				inErrorNUM = (double)inErrorPkts/(double)timePassed;
			ifInErrorPacketNUM.put(ifDescr, String.format("%.3f", inErrorNUM));
		}
		return ifInErrorPacketNUM;
	}
	
	/**
	 *  35 ifOutErrorPacketNUM, xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfOutErrorPacketNUM () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifOutErrorPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifOutErrorPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifOutErrorPkts = getIfOutErrors(one_interface);
			long pre_ifOutErrorPkts = getIfOutErrors(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifOutErrorPkts==-1 || pre_ifOutErrorPkts==-1) {
				ifOutErrorPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long outErrorPkts = Math.abs(cur_ifOutErrorPkts-pre_ifOutErrorPkts);
			long timePassed = this.timePassed/1000;
			double outErrorNUM = 0;
			if(timePassed!=0)
				outErrorNUM = (double)outErrorPkts/(double)timePassed;
			ifOutErrorPacketNUM.put(ifDescr, String.format("%.3f", outErrorNUM));
		}
		return ifOutErrorPacketNUM;
	}
		
	/**
	 *  36 ifErrorPacketNUM, xxx pkts/s
	 * @return
	 */
	public LinkedHashMap<String, String> getIfErrorPacketNUM () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifErrorPacketNUM = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			if(preInterfaceData==null) {
				ifErrorPacketNUM.put(ifDescr, "0");
				continue;
			}
			int ifIndex = getIfIndex(one_interface);
			long cur_ifInErrorPkts = getIfInErrors(one_interface);
			long pre_ifInErrorPkts = getIfInErrors(preInterfaceData.get(ifIndex-1));
			long cur_ifOutErrorPkts = getIfOutErrors(one_interface);
			long pre_ifOutErrorPkts = getIfOutErrors(preInterfaceData.get(ifIndex-1));
			
			if(cur_ifInErrorPkts==-1 || pre_ifInErrorPkts==-1 
					|| cur_ifOutErrorPkts==-1 || pre_ifOutErrorPkts==-1) {
				ifErrorPacketNUM.put(ifDescr, "0");
				continue;
			}
			
			long inErrorPkts = Math.abs(cur_ifInErrorPkts-pre_ifInErrorPkts);
			long outErrorPkts = Math.abs(cur_ifOutErrorPkts-pre_ifOutErrorPkts);
			long timePassed = this.timePassed/1000;
			double errorNUM = 0;
			if(timePassed!=0)
				errorNUM = ((double)inErrorPkts+(double)outErrorPkts)/(double)timePassed;
			ifErrorPacketNUM.put(ifDescr, String.format("%.3f", errorNUM));
		}
		return ifErrorPacketNUM;
	}
	
	/**
	 *  37 ifStatus
	 * @return
	 */
	public LinkedHashMap<String, String> getIfStatus () {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;
		
		LinkedHashMap<String, String> ifStatusList = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			String ifStatus = getIfStatus(one_interface);
			ifStatusList.put(ifDescr, ifStatus);
		}
		return ifStatusList;
}

	/**
	 *  38 if ip address
	 * @return
	 */
	public LinkedHashMap<String, String> getIfIpAddress() {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifIpAddrList = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			String ipAddr = getIfIpAddr(one_interface);
			ifIpAddrList.put(ifDescr, ipAddr);
		}
		return ifIpAddrList;
	}

	/**
	 *  39 if next hop ipaddress
	 * @return
	 */
	public LinkedHashMap<String, String> getIfNextHopIpAddress() {

		if(curInterfaceData==null || curInterfaceData.isEmpty())
			return null;

		LinkedHashMap<String, String> ifNextHopList = 
				new LinkedHashMap<String, String>();
		
		for(ArrayList<String> one_interface : curInterfaceData) {
			if(one_interface==null)
				continue;
			String ifDescr = getIfDescr(one_interface);
			String ifNextHop = getIfNextHopIp(one_interface);
			ifNextHopList.put(ifDescr, ifNextHop);
		}
		return ifNextHopList;
	}
}
