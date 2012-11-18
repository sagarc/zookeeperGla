#!/bin/sh
sudo rm zookeeper.out

cd "../"
cd "data"
sudo rm -r "server1/version-2"
sudo rm -r "server2/version-2"
sudo rm -r "server3/version-2"
cd ../
cd bin
ls
sudo ./zkServer.sh restart zoo1.cfg
sudo ./zkServer.sh restart zoo2.cfg
sudo ./zkServer.sh restart zoo3.cfg


