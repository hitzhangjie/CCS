import oracle.sql.ConverterArchive;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.*;

class SnmpRequest {
	String					ipAddress	= null;
	int						port		= -1;
	int						snmpVersion	= -1;
	String					community	= null;

	TransportMapping		transport	= null;
	CommunityTarget			comTarget	= null;
	Snmp					snmp		= null;
	static Integer32	snmpRequestID	= new Integer32(0);
	
	public SnmpRequest(Device dev) throws IOException {
		
		this.ipAddress = dev.ip;
		this.port = dev.snmpPort;
		switch (dev.snmpVersion) {
		case 1:
			this.snmpVersion = SnmpConstants.version1;
			break;
		case 2:
			this.snmpVersion = SnmpConstants.version2c;
			break;
		case 3:
			this.snmpVersion = SnmpConstants.version3;
			break;
		default:
			this.snmpVersion = SnmpConstants.version2c;
			break;
		}
		this.community = dev.snmpCommunity;

		// create transport mapping
		transport = new DefaultUdpTransportMapping();
		if(transport!=null)
			transport.listen();
		
		// create target address object
		comTarget = new CommunityTarget();
		if(comTarget!=null) {
			comTarget.setCommunity(new OctetString(community));
			comTarget.setVersion(this.snmpVersion);
			comTarget.setAddress(new UdpAddress(this.ipAddress + "/" + this.port));
			comTarget.setRetries(0);
			comTarget.setTimeout(5000);
		}

		snmp = new Snmp(transport);
	}
	
	// 检查当前SnmpRequest对象是否可用
	public boolean isValid() {
		if(transport!=null && transport.isListening()
				&& comTarget!=null
				&& snmp!=null) {
			return true;
		}
		else
			return false;
	}
	
	// 如果被管设备没有响应，那么返回值为null
	// 如果被管设备对指定的oid没有实现或者没有响应，那么返回值为noSuchObject
	public String sendGetRequest(String oidValue) throws IOException {
		PDU pdu = new PDU();
		pdu.add(new VariableBinding(new OID(oidValue)));
		
		pdu.setType(PDU.GET);
		pdu.setRequestID(snmpRequestID);
		snmpRequestID.setValue(snmpRequestID.toInt()+1);
		
		ResponseEvent response = snmp.get(pdu, comTarget);
		
		String responseValue = null;
		if (response != null) {
			PDU responsePDU = response.getResponse();

			if (responsePDU != null) {
				int status = responsePDU.getErrorStatus();
				int index = responsePDU.getErrorIndex();
				String text = responsePDU.getErrorStatusText();

				if (status == PDU.noError) {
					responseValue = responsePDU.
							getVariableBindings().elementAt(0).getVariable().toString();
				}
				else {
					//System.out.println("Error: GET Request failed");
					//System.out.println("Error Status = " + status);
					//System.out.println("Error Index = " + index);
					//System.out.println("Error Status Text = " + text);
				}
			}
			else {
				//System.out.println("Error: Response PDU is null");
			}
		}
		else {
			//System.out.println("Error: Agent timeout");
		}

		return responseValue;	
	}
	
	// 若被管设备没有响应，那么返回值为null;
	// 若被管设备对指定的oid没有实现或者没有响应，那么对应的变量值为noSuchObject;
	public Vector<VariableBinding> sendGetRequest(String oids[]) throws IOException {

		Vector<VariableBinding> vector = null;
		
		int length = oids.length;
		if (length > 0) {
			PDU pdu = new PDU();

			for (int i = 0; i < length; i++) {
				pdu.add(new VariableBinding(new OID(oids[i])));
			}

			pdu.setType(PDU.GET);
			pdu.setRequestID(snmpRequestID);
			snmpRequestID.setValue(snmpRequestID.toInt()+1);

			ResponseEvent response = snmp.get(pdu, comTarget);

			if (response != null) {
				PDU responsePDU = response.getResponse();

				if (responsePDU != null) {
					int status = responsePDU.getErrorStatus();
					int index = responsePDU.getErrorIndex();
					String text = responsePDU.getErrorStatusText();

					if (status == PDU.noError)
						vector = (Vector<VariableBinding>) responsePDU.getVariableBindings();
					else {
						//System.out.println("Error: Request Failed");
						//System.out.println("Error Status = "+status);
						//System.out.println("Error Index = "+index);
						//System.out.println("Error Status Text = "+text);
					}
				}
				else {
					//System.out.println("Error: Response PDU is null");
				}
			}
			else {
				//System.out.println("Error: Agent Timeout... ");
			}
		}

		return vector;
	}
	
	// 如果被管设备没有响应，那么返回值为null;
	public String sendGetNextRequest(String oidValue) throws IOException {
		PDU pdu = new PDU();
		pdu.add(new VariableBinding(new OID(oidValue)));
		
		pdu.setType(PDU.GET);
		pdu.setRequestID(snmpRequestID);
		snmpRequestID.setValue(snmpRequestID.toInt()+1);

		ResponseEvent response = snmp.getNext(pdu, comTarget);
		
		String responseValue = null;
		if (response != null) {
			PDU responsePDU = response.getResponse();

			if (responsePDU != null) {
				int status = responsePDU.getErrorStatus();
				int index = responsePDU.getErrorIndex();
				String text = responsePDU.getErrorStatusText();

				if (status == PDU.noError) {
					//responseValue = responsePDU.getVariableBindings().toString();
					responseValue = 
					((VariableBinding)responsePDU.getVariableBindings().elementAt(0))
					.getVariable().toString();
				}
				else {
					//System.out.println("Error: Request Failed");
					//System.out.println("Error Status = " + status);
					//System.out.println("Error Index = " + index);
					//System.out.println("Error Status Text = " + text);
				}
			}
			else {
				//System.out.println("Error: GetNextResponse PDU is null");
			}
		}
		else {
			//System.out.println("Error: Agent Timeout");
		}

		return responseValue;
	}	
	
	// 如果被管设备没有响应，返回值为null;
	// 如果对指定的oid没有响应，那么对应的变量为null;
	public LinkedHashMap<String, String> sendGetBulkRequest(String oidValue) {
		PDU pdu = new PDU();
		pdu.add(new VariableBinding(new OID(oidValue)));
		pdu.setType(PDU.GETBULK);
		pdu.setRequestID(snmpRequestID);
		snmpRequestID.setValue(snmpRequestID.toInt()+1);
		pdu.setRequestID(snmpRequestID);
		
		/*
		SNMPGETBULK, with MAXREPETITONS and NONREPEATERS in GETBULK 
		PDU, there's a var list for NONREPEATERS vars, only one GETNEXT request 
		is done for rest vars, for each of them, process GETNEXT request 
		MAXREPTITIONS times.

		in our getbulk request, only one oid is specified, so NONREPEATER is set to 
		ZERO we want to get data as much as possible, so set MAXREPETITONS to 
		1000.
		
		this parameter should be adjustable.
		*/
		pdu.setMaxRepetitions(100);
		pdu.setNonRepeaters(0);

		/*
		using HashMap, the sequence you put and you traverse may be different, 
		using LinkedHashMap instead of HashMap.
		*/
		LinkedHashMap<String, String> map = null;

		// Snmp call getBulk
		ResponseEvent response = null;
		try {
			response = snmp.getBulk(pdu, comTarget);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		
		if (response != null) {
			PDU responsePDU = response.getResponse();

			if (responsePDU != null) {
				map = new LinkedHashMap<String, String>();
			
				int status = responsePDU.getErrorStatus();
				int index = responsePDU.getErrorIndex();
				String text = responsePDU.getErrorStatusText();

				if (status == PDU.noError) {
					Vector vc = responsePDU.getVariableBindings();
					for (int i = 0; i < vc.size(); i++) {
						VariableBinding vb = (VariableBinding)vc.elementAt(i);
						// "." is removed from the beginning, pay attention
						String oid = vb.getOid().toString();
						String value = vb.getVariable().toString();

						map.put(oid, value);
					}
				}
				else {
					//System.out.println("Error: Request Failed");
					//System.out.println("Error Status = " + status);
					//System.out.println("Error Index = " + index);
					//System.out.println("Error Status Text = " + text);
				}
			}
			else {
				//System.out.println("OID: "+oidValue+" Error: Response PDU is null");
			}
		}
		else {
			//System.out.println("Error: Agent Timeout");
		}
		return map;
	}	
	
	public String getRowIdentifier(String subOid, String superOid) {
		
		// 不要对传入参数做修改
		String subOidValue = new String(subOid);
		String superOidValue = new String(superOid);
		
		String rowIdentifier = null;
		
		if(!subOidValue.startsWith("."))
			subOidValue = "."+subOidValue;
		
		// remove superOid, remove . and entry 1 and .
		// for example, super is .1.3.6.1, sub is 1.3.6.1
		rowIdentifier = subOidValue.replaceFirst(superOidValue+".1.", "");
		// remove col NO.
		int dotPos = rowIdentifier.indexOf(".");
		rowIdentifier = rowIdentifier.substring(dotPos+1);
		
		return rowIdentifier;
	}
	
	// 如果被管设备没有响应，返回值为null;
	public ArrayList<LinkedHashMap<String, String>>
	sendGetTableRequest(String oidValue) {
		
		// <行标识符,<该行中字段的oid，字段的值>>
		LinkedHashMap<String, LinkedHashMap<String, String>> allRows = 
				new LinkedHashMap<String, LinkedHashMap<String,String>>();
		// 将上面结构体中的行标识符去掉之后的结果
		ArrayList<LinkedHashMap<String,String>> table = null;
		// 暂存getbulk返回的数据
		LinkedHashMap<String,String> newTableDataMap = null;
		
		boolean hasNext = true;
		
		String curOidValue = oidValue;
		String nextOidValue = oidValue;
		
		while(hasNext) {
			
			curOidValue = nextOidValue;
			newTableDataMap = sendGetBulkRequest(curOidValue);
			
			// 第一次getbulk
			if(table==null) {
				if(newTableDataMap==null)		// 未收到响应
					return null;
				else											// 收到了响应
					table = new ArrayList<LinkedHashMap<String, String>>();
			}
			
			// getbulk请求收到了响应，将结果写入table
			if(table!=null && newTableDataMap!=null) {
				/*
				 	put table elements in newTableDataMap into table, remained 
				 	elements that not belong table will be dropped.
				 */
				
				for(Map.Entry<String, String> entry : newTableDataMap.entrySet()) {
					
					String oid = entry.getKey();
					String value = entry.getValue();
					
					// 检查当前的oid有没有超出对应的表格的范围
					if(("."+oid).startsWith(oidValue) 					
							&& ("."+oid).contains(oidValue+".")) {
						
						// snmp agent返回的由oidValue标志的表格，这个表格中的数据时这
						// 样组织的，oidValue+".1."，这时通过阅读MIB，或者查看MIB浏览
						// 器，发现到达了表格下的entry，之后在oid+".1."上继续追加的部分
						// 我们就需要认真对待了，+"colNumber.rowIdentifier"，rowIdentifier
						// 可以用来判断返回的数据中那些属于表格中的同一行，而colNumber
						// 则可用来判断返回的数据中那些属于同一列.
						// 综合这两项构建出完整的表格.
						
						// 首先取出行的编号
						String rowIdentifier = getRowIdentifier(oid, oidValue);
						
						// 从表格中取出对应的行
						LinkedHashMap<String, String> row = allRows.get(rowIdentifier);
						
						// 该行已经存在，继续在里面存放即可
						if(row!=null) {
							row.put(oid, value);
						}
						// 该行不存在，需要先创建行
						else {
							LinkedHashMap<String, String> newRow =
									new LinkedHashMap<String, String>();
							
							newRow.put(oid, value);
							// 保存数据到返回值
							table.add(newRow);
							// 为了便于检索，也将数据存放到这里面
							allRows.put(rowIdentifier, newRow);
						}
						
						// ready for next GETBULK loop
						nextOidValue = "."+oid;
						
					}
					else {
					 	// current oid is exceed the range of table data, so 
						// dropped it and exit the getBulk loop.
						hasNext = false;
						break;
					}
				}
			}
			else {
				// getbulk操作没有正常遍历完所有的table数据，这中间可能出现了
				// 网络超时的情况，这种情况下，为了尽可能多的获取数据，理应继续
				// 访问，所以我们设置了setRetries(3)，如果尝试3次之后仍然失败，
				// 就不需要继续getbulk操作了，继续getbulk不过是继续尝试第4次，
				// 这种情况下，我们丢掉表格后续内容，这可能引发一些问题，例如
				// 造成ifTable、ifXTable、ipAddTable、ipRouteTable不一致，进而
				// 出现出现某些异常，一个已知问题是如果ifXTable不完整，使用ifTable
				// 中的数据，可能会出现溢出
				hasNext = false;
			}
			
			// ready for next GETBULK loop
			newTableDataMap = null;
		}
		
		return table;
	}
	
	// 如果对多个表的请求均为null，则返回值为null;
	// 对多个表发出请求，可能其中某几个的响应为null,按顺序检查对应的响应是否为null;
	public Vector<ArrayList<LinkedHashMap<String, String>>> 
	sendGetTableRequest(String oids[]) {

		Vector<ArrayList<LinkedHashMap<String, String>>> allTables = 
				new Vector<ArrayList<LinkedHashMap<String,String>>>();
		
		for(int i=0; i<oids.length; i++) {
			ArrayList<LinkedHashMap<String,String>> table = 
					new ArrayList<LinkedHashMap<String,String>>();
			
			String oidValue = oids[i];
			
			// 对于我们的代理这一块，做了如下修改，主要是考虑到这几张表之间的关系，
			// 如果是需要读取ifTable、ifXTable、ipAddTable、ipRouteTable，读取之前
			// 尽量先检查一下ifTable是否已经成功获取到，如果ifTable没有获取到，后续
			// 三张表的获取是没有用的.
			// 如果要忽略表之间的欢喜的话，请将if部分注释掉，直接使用else块内的语句
			if( 	(oidValue.equals(".1.3.6.1.2.1.31.1.1") ||
					 oidValue.equals(".1.3.6.1.2.1.4.20") ||
					 oidValue.equals(".1.3.6.1.2.1.4.21")) &&
					 allTables.get(0)==null
					) {
				allTables.add(null);
			}
			else {
				table = sendGetTableRequest(oidValue);
				allTables.add(table);
			}
		}
		
		// check whether all sendTableRequest's responses are null
		int pos = 0;
		for(; pos<allTables.size(); pos++) {
			if(allTables.get(pos)!=null)
				break;
		}
		if(pos==allTables.size())
			return null;
		
		return allTables;
	}
	
	
	// 转换16进制字符串到ASCII字符串
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

	// 显示绑定变量的内容
	public void displayVariableBinding(VariableBinding vb) {
		String oid = vb.getOid().toString();
		String value = vb.getVariable().toString();
		// if value is noSuchObject, means this oid is not implemented by agent
		System.out.println(String.format("%-30s --> %30s",oid,value));
	}
	
	// 显示表的内容
	public void displayTable(ArrayList<LinkedHashMap<String, String>> table) {
		// display all table rows
		int index = 0;
		for (LinkedHashMap<String, String> row : table) {
			index ++;
			if (row != null) {
				String onerow = index+": ";
				for (Map.Entry<String, String> col : row.entrySet()) {
					
					//System.out.println(String.format("%-30s --> %30s",
						//	col.getKey(),col.getValue()));
						
					onerow+=String.format(" %40s ", convertFromHexToAscii(col.getValue()));
				}
				System.out.println(onerow);
			}
			else
				System.out.println(index+":               this row is empty");
		}
	}
	public void displayTableContainOid(ArrayList<LinkedHashMap<String, String>> table) {
		// display all table rows
		int index = 0;
		for (LinkedHashMap<String, String> row : table) {
			index ++;
			if (row != null) {
				String onerow = index+": ";
				for (Map.Entry<String, String> col : row.entrySet()) {
					
					System.out.println(String.format("%-30s --> %30s",
							col.getKey(),col.getValue()));
						
					//onerow+=String.format(" %40s ", convertFromHexToAscii(col.getValue()));
				}
				//System.out.println(onerow);
			}
			else
				System.out.println(index+":               this row is empty");
		}
	}
	
	// 显示vector内容
	public void displayVector (Vector vc, int type) {
		 // type = 1, process response of sendGetRequest
		 // type = 2, process response of sendGetTableRequest
		if(type!=1 && type!=2) {
			System.out.println("error type");
			return;
		}
		
		if(vc==null)
			return;
			
		for(int i=0; i<vc.size(); i++) {
			if(type==1) {
				System.out.println("response from snmpget "+i+":");
				displayVariableBinding((VariableBinding)vc.elementAt(i));
			}
			if(type==2) {
				System.out.println("response from snmpgettable "+i+":");
				displayTable(
						(ArrayList<LinkedHashMap<String,String>>)vc.elementAt(i));
			}
		}
	}
	
	// 关闭
	public void close() throws IOException {
		snmp.close();
		transport.close();
	}
	
	
}
