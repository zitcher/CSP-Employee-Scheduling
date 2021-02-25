#!/bin/bash

########################################
############# CSCI 2951-O ##############
########################################

# Update this file with instructions on how to compile your code
javac -classpath /Applications/CPLEX_Studio201/cpoptimizer/lib/ILOG.CP.jar ./src/solver/cp/*.java

E_BADARGS=65
if [ $# -ne 1 ]
then
	echo "Usage: `basename $0` <input>"
	exit $E_BADARGS
fi
	
input=$1

# export the ilog license to run the solver
export ILOG_LICENSE_FILE=/gpfs/main/sys/shared/psfu/local/projects/cplex/ilm/current/linux/access.site.ilm

# export the solver libraries into the path
export LD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:/Applications/CPLEX_Studio201/cpoptimizer/bin/x86-64_osx:/Applications/CPLEX_Studio201/cplex/bin/x86-64_osx

# add the solver jar to the classpath and run
java -cp /Applications/CPLEX_Studio201/cpoptimizer/lib/ILOG.CP.jar:src solver.cp.Main $input