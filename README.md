# Dasi agents
Contains three agents that will be described in the following

## Meta adder
This agent allow the triple coming from SCORPIO to be correctly seen by the Solid Community Server by adding metadata. When new data is added, the agent receive a notification thanks to the subscription mechanism provided by SEPA, this allow the task to be very lightweight and have very good performance.

## History Keeper
This agent add the live observation to an history graph, it receive a notification when new data is added thanks to the subscription mechanism provided by SEPA.

## Fake Control Unit
This agent simulate data coming from a transformer, it produces temperature data from four sensors following sinusoidal functions. The produced temperatures data are sent to SCORPIO.

