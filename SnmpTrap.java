/*
 * class: SnmpTrap
 * Author:ZhangJie
 * 
 * Function:
 * 1) listen on incoming snmpv1 V1TRAP, snmpv2c TRAP
 * 2) extract cpu load and memory load 
 * 3) many other things
 * 
 * Future Work:
 * 1) how do managed devices set the TRAP\INFORM content ?
 * 	  a processor table hrProcessorTable ?
 *    a storage table hrStorageTable ?
 *    or others ?
 * 
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;

import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import com.agentpp.common.smi.editor.Token.Statement;

public class SnmpTrap implements CommandResponder {
	public static long count = 0;
	
	public SnmpTrap() {
	}

	/**
	 * This method will listen for traps and response pdu's from SNMP agent.
	 */
	public synchronized void listen(
			TransportIpAddress address) throws IOException {
		
		AbstractTransportMapping transport;
		if (address instanceof TcpAddress) {
			transport = new DefaultTcpTransportMapping((TcpAddress) address);
		}
		else {
			transport = new DefaultUdpTransportMapping((UdpAddress) address);
		}

		// create a thread pool of fixed number of threads for tasks,
		// thread will block when all threads are busy and more task is added
		ThreadPool threadPool = ThreadPool.create("DispatcherPool", 16);
		// dispatch incoming message to thread in thread pool
		MessageDispatcher mtDispatcher = 
				new MultiThreadedMessageDispatcher(
						threadPool, new MessageDispatcherImpl());

		// add message processing models
		mtDispatcher.addMessageProcessingModel(new MPv1());
		mtDispatcher.addMessageProcessingModel(new MPv2c());
		// why not add MPv3, i add it manually ?
		mtDispatcher.addMessageProcessingModel(new MPv3());

		// add all security protocols
		SecurityProtocols.getInstance().addDefaultProtocols();
		SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());

		//Create Target
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString("public"));

		Snmp snmp = new Snmp(mtDispatcher, transport);
		snmp.addCommandResponder(this);

		transport.listen();
		System.out.println("CCS snmptrapd listening on " + address);

		try {
			this.wait();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * This method will be called whenever a pdu is received on the given port 
	 * specified in the listen() method.
	 */
	public synchronized void processPdu(
			CommandResponderEvent cmdRespEvent) {
		
		System.out.println("Received PDU NO."+count);
		count++;
		PDU pdu = cmdRespEvent.getPDU();
		if (pdu != null) {

			System.out.println(PDU.TRAP+" "
					+PDU.V1TRAP+" "
					+PDU.REPORT+" "
					+PDU.RESPONSE+" "
					+PDU.INFORM);
			System.out.println("Trap Type = " + pdu.getType());
			System.out.println("Variable Bindings = " + pdu.getVariableBindings());
			
			Vector vector = pdu.getVariableBindings();
			for(int i=0; i<vector.size(); i++) {
				
				VariableBinding vb = (VariableBinding)vector.elementAt(i);
				String oid = vb.getOid().toString();
				String value = vb.getVariable().toString();
				
				String trapType = pdu.getTypeString(pdu.getType());
				
				// trap client address mapping
				String clientAddress = cmdRespEvent.getPeerAddress().toString();
				// trapd address mapping
				String tmp2 = cmdRespEvent.
						getTransportMapping().getListenAddress().toString();
				
				System.out.println(trapType+" : "
						+clientAddress+","
						+tmp2+","
						+oid+"-->"+value);
				
				
				try {
					FileWriter of = new FileWriter("./trapMsg",true);
					of.write(trapType
							+" : from "
							+clientAddress
							+", "+oid+"--->"+value+"\n");
					of.close();
				}
				catch (IOException e) {
					
					e.printStackTrace();
				}
				
				// cpu load oid
				// extract the value from pdu
				
				// mem load oid
				// extract the value from pdu
				
			}
		}
	}
}
