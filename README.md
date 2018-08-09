# safra-termination-detection-fault-tolerant

## Dependencies
 * JDK 1.7
 * ant

 * IPL 2.3.2: https://github.com/JungleComputing/ipl/releases/tag/v2.3.2
    or as zip file in this project
 
 * log4j 1.2: https://logging.apache.org/log4j/1.2/
     also used by IPL

## Installation
* Install IPL as explained here: https://github.com/JungleComputing/ipl
  * Especially, add IPL_HOME to your environment.
* Enter the implementation folder `cd ibisImplementation`
* Run `ant` to compile

## Running

If you run it on DAS-4:

Start the IPL server on the headnode with:
`$IPL_HOME/scripts/ipl-server`
 
`runChandyMisra.sh 
  <numberOfNodes: int> 
  <instancePerNode: int> 
  <repetitions: int>
  <outputFolderName: string>
  <nodeFailurePercentage: float>
  <SafraVersion: 'ft'|'fs'>
  <serverPort: int> <arguments to prun: ...>` 

It writes the .log files for every node, Chandy Misra Result to the supplied 
output folder. It writes it's standard out to `./out.log` which is copied
to the supplied output folder.
The standard port used for the IPL server is 8888 but every port works

For runs on other machines:
Start the IPL server on any machine: 
`$IPL_HOME/scripts/ipl-server`

And use to run a single instance:
`$IPL_HOME/scripts/ipl-run \
    -Xmx2g \
    -Dibis.server.address= <serverAddress> \
    -Dibis.pool.name=chandyMisra \
    -Dibis.pool.size= <networkSize> \
    ibis.ipl.apps.safraExperiment.IbisNode <outputFolder: string> <crashPercentage: float> <safraVersion: 'ft'|'fs'> `

You will want to to write a script to run multiple instances at the same time to wrap around this.
The scripts to run in on DAS-4 provided a good starting point and should be easy to adapts.

TODOs
=====
