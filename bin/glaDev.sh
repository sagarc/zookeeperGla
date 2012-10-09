rm -r "../build/recipes"
cd "../src/recipes"
ant
cd "../../bin"
bash zkGla.sh $@
