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

## Overview
This chapter describes the different modules in this project, it's organized
as the folder structure of this project. First, of all the top folders:

dependencies: downloaded version of the IPL version used
report: latex sources of the technical report
ibisImplementation: Ibis based implementation of Chandy Misra and both Safra versions
  of this experiment. Generates logged events of the execution and metrics as output
analysis: Python tool to analyse the results the output of ibisImplementation

## ibisImplementation

* ChandyMisra: Implementation of a fault tolerant Chandy Misra version.
* communication: wrapper around IPL, sends, receives and dispatches messages
   to the rest of the system
* crashSimulation: contains the CrashSimulator, as well as, the simulated crash detector
* experiment: Tools to verify each run by its produced logs, calculates Safra's statistics and builds the output folder
* ibisSignalling: implementation of a thread to pull Ibis signals from the Ibis server
* network: builds the network topologies for the experiment, can calculate expected sink tree results
* Safra: fault tolerant and fault sensitive Safra implementation
* Utils: A timer, a wrapper around Random to generate the same random numbers on all instances in an experiment and 
    multiple easy barrier implementations to keep all instances in sync.
    
Two different main classes exists, `IbisNode` runs a single run of the experiment.
For this it builds up a system that uses IPL to communicate, generates a randomized network topology, determines
which nodes to crash and runs Safra on top of ChandyMisra and in the end verifies that termination was detected correctly
and generates statistics for this run.
`AnalysisOnly` runs the analysis to generate statistics about a run offline independent from the run itself.
It cannot verify the ChandyMisra result because the network is not saved with the run but it can
analysis the event log for correct termination detection. This main class is used to verify the logs against
the second definition of termination.


## Output format

Before one reads this section, one should read the report as it contains important information on how to interpret
the experiments. The gist is, that the normal definition of termination that all nodes are passive and no messages
are in the channels, does not fully cover the reality in presence of faults. Therefore, the experiment has been
analysis by an extended definition of termination which covers most of these cases.

The experiment consists out of different configuration, each configuration is
defined by its network size, node fault percentage and the used Safra version.
For each of these configurations multiple repetitions exists. Each repetition
presents a single run of the Chandy Misra with Safra as termination detection 
algorithm.

All output of the experiment lays within a folder I call `experimentFolder`.
In that folder one or multiple folders for each configurations exist.
They are named by `<network-size>-<fault-percentage|'fs'>-<...>.run`
the first two parts of the name describe the configuration represented, 
the three points can be any name, they end with the suffix `.run`.
These folders contain repetitions - folders which are named by increasing numbers
from 1. These folders contain the actual results of all runs.

The results from a single run include four kind of files:
 * `<node-number>.chandyMisra` contains the output of the Chandy Misra algorithm: <nodeNumber> <distance> <parent>
 * `<node-number>.log` a timestamped log of all relevant events on a single node e.g. sending of a token or calling announce
 * `.error` a line separated collection of all problems identified in this run a list of possible errors and there interpretation is given below
 * `.warn` a line separated collection of less severed problems with a run. Meant to be reviewed in the case of errors.
 * `safraStatistics.csv` The measured metrics in csv format.
 
Possible errors:
 * Chandy Misra miscalculated the sink tree
   * Reason 1: Bug in Chandy Misra
   * Termination has been detected to early and Chandy Misra could not finish in 
     this case also the error "Announce was called before actual termination" is present
 * "Announce was called before actual termination" announce has been called too early by Safra according
   to the definition of termination used. 
     * This is either a bug in Safra or a node crash has been detected just before announce was called and kicked other nodes
       back into action after announce has been called (see report for more information on this topic). 
       Further information is supplied in the error file and will include all parent crash events.
 * "Termination wasn't detected in 1:30 minutes." 
   * Most likely a bug in Safra because no run took so long.
   
Each run also contains a folder called `original_termination_definition` which
contains the results of analysing the event logs by the original, none extended  definition
of termination. If these folder contain an error file with the message that "Announce was called before actual termination" this
would be a definite bug in Safra's algorithm.
        