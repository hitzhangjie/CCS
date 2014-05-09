import java.util.ArrayList;
import java.util.LinkedHashMap;

class Device {
	// unique
	String deviceTag = null;
	// device type, '01' or '1':host, '02' or '2':storage, '03' or '3':network
	String deviceType = null;	
	// ip address
	String ip = null;
	// snmp relevant info
	int snmpVersion = -1;
	int snmpPort = -1;
	String snmpCommunity = null;
	// seconds
	int interval = -1;
	
	public Device(
			String deviceTag, 
			String deviceType,
			String ip, 
			int snmpVersion,
			int snmpPort,
			String snmpCommunity,
			int interval) {
		this.deviceTag = deviceTag;
		this.deviceType = deviceType;
		this.ip = ip;
		this.snmpVersion = snmpVersion;
		this.snmpPort = snmpPort;
		this.snmpCommunity = snmpCommunity;
		this.interval = interval;
	}
	
	public String toString() {
		return deviceTag+":"+ip+":"+interval;
	}
}
