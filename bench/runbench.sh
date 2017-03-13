#!/bin/bash

[ "$nthreads." == "." ] && export nthreads="2"
[ "$strategies." == "." ] && export strategies="fixed sync reentrant custom stamped llock fll tfll"

declare -A strategy_object

strategy_object[fixed]="FixedSize"
strategy_object[sync]="Synchronized"
strategy_object[reentrant]="ReentrantLock"
strategy_object[custom]="CustomLock"
strategy_object[stamped]="StampedLock"
strategy_object[llock]="LayoutLock"
strategy_object[fll]="LightweightLayoutLock"
strategy_object[tfll]="TransitioningFastLayoutLock"

JT="../tool/jt.rb"

while [[ $# -gt 0 ]]
do
key=$1

case $key in
   -h|--help)
   echo "The following options are supported"
   echo " -h | --help           This help message"
   echo " -T \"<space seprated list of thread numbers>\""
   echo "                       Specify the numbers of threads to run with."
   echo "                       Default: -T \"$nthreads\""
   echo " -S \"<space separated list of synchronization strategies>\""
   echo "                       Available strategies: fixed sync reentrant custom stamped llock fll tfll"
   echo "                       Default: -S \"$strategies\""
   echo " -B <benchmark_tempalte_file>"
   ;;
   -T)
      export nthreads=$2
      shift # past argument
   ;;
   -S)
      export strategies=$2
      shift # past argument
   ;;
   -B)
      export benchmark=$2
      shift # past argument
   ;;
   *)
     echo "Unknon option $key. Please re-run -with -h or --help to see the list of available options."
     exit 1;
  ;;
esac
shift #past argument or value
done

[ "$benchmark." == "." ] && echo "Please specifiy a benchmark script" && exit

# prepare the benchmark script
for strategy in $strategies; do
#   cat $benchmark | sed "s/@STRATEGY@/${strategy_object[$strategy]}/g" | sed "s/@NAME@/$strategy/g" > script.rb
#  run the script on the thread groups
   for threads in $nthreads; do
       echo "Run with strategy=${strategy_object[$strategy]}, threads=$threads"
       echo "$JT ruby --graal -J-Dgraal.TruffleSplittingMaxCalleeSize=0 -J-Dgraal.TruffleOSR=false $benchmark ${strategy_object[$strategy]} $threads"
       $JT ruby --graal -J-Dgraal.TruffleSplittingMaxCalleeSize=0 -J-Dgraal.TruffleOSR=false --trace $benchmark ${strategy_object[$strategy]} $threads 2>&1
   done
done
  
