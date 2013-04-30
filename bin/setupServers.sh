cd server1
sudo rm zookeeper.out
sudo bash zkServer.sh start zoo1.cfg
cd ../server2
sudo rm zookeeper.out
sudo bash zkServer.sh start zoo2.cfg
cd ../server3
sudo rm zookeeper.out
sudo bash zkServer.sh start zoo3.cfg
cd ../
