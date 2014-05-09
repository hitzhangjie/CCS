CCS
===

CCS is snmp-based condition capture system to monitor the information devices, including hosts, network devices, storage devices. It is not just an snmp monitor, it can serves as an proxy. Providing there're many groups of developers working 
for a big company, and they're developing snmp-based systems, the devices' manager will have to configure the target 
devices because of the snmpv1/v2c/v3 access control. So, we can see, this configuration process will put heavy load on devices's managers. What's worse, too many snmp requests and responses will put heavy load on devices, too.

The goal of CCS is to build a powerful and high effective system. It defines the interface to interfact with thirdpary
developers to receive their request and then, CCS works as a proxy to send snmp request to the devices and get response,
this process can be periodical, CCS may send the data back to thirdparty developers via MQ, such as Apache ActiveMQ.

i have implement the basic part for concurrently send snmp request and parse the response, and i have tested the programme
for accessing about 10000 devices, including 1000 hosts, 2500 routers and 6000 switchers. It will spend about 30 or 40 minutes to finish one poll. The performance issue is very obvious. 

