cd "../"
#rm -r "build/recipes"
cd "data"
sudo rm -r "server1/version-2"
sudo rm -r "server2/version-2"
sudo rm -r "server3/version-2"
cd "../"

rm -r "build"
ant
ant eclipse

cd "./src/recipes"
ant
cd "../../bin"

