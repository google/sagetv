# netsh script for adding sagetv ports
firewall add portopening protocol=UDP port=31100 name="SageTV MiniDiscovery" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=UDP port=8270 name="SageTV ClientDiscovery" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=tcp port=42024 name="SageTV Client" mode=enable scope=CUSTOM addresses=LocalSubnet 
firewall add portopening protocol=UDP port=16881 name="SageTV MVP" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=UDP port=16869 name="SageTV Tftp" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=UDP port=16867 name="SageTV Bootp" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=tcp port=31099 name="SageTV MiniClient" mode=enable scope=ALL
firewall add portopening protocol=tcp port=6969 name="SageTV EncodingServer" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=UDP port=8271 name="SageTV EncodingDiscovery" mode=enable scope=CUSTOM addresses=LocalSubnet
firewall add portopening protocol=tcp port=7818 name="SageTV MediaServer" mode=enable scope=CUSTOM addresses=LocalSubnet 
bye
