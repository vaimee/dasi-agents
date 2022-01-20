cd /usr/src/dasi/ControlUnitSimulator

java -jar FakeControlUnit.jar > ../control.log &

cd ../MetaAdder
java -jar MetaAdder.jar > ../adder.log &

cd ../HistoryKeeper
java -jar HistoryKeeper.jar > ../history.log &

cd ..
tail -f control.log adder.log history.log