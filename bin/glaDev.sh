cd "../"
#rm -r "build/recipes"
cd "data/server"
sudo rm -r "version-2"
cd "../../"

rm -r "build"
ant
ant eclipse

cd "./src/recipes"
ant
cd "../../bin"

