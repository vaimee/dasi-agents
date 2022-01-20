FROM openjdk:13.0.2-jdk

WORKDIR /usr/src/dasi

COPY ./target/FakeControlUnit-0-SNAPSHOT.jar ./ControlUnitSimulator/FakeControlUnit.jar
COPY ./configs ./ControlUnitSimulator/configs

COPY ./target/MetaAdder-0-SNAPSHOT.jar ./MetaAdder/MetaAdder.jar
COPY ./configs ./MetaAdder/configs

COPY ./target/HistoryKeeper-0-SNAPSHOT.jar ./HistoryKeeper/HistoryKeeper.jar
COPY ./configs ./HistoryKeeper/configs

COPY run.sh ./

CMD bash ./run.sh