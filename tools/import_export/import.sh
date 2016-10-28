#!/bin/bash
# Adjusted from http://stackoverflow.com/a/13550669/783758

if [ ! $2 ]; then
  echo " Example of use: $0 database_name dump_dir"
  echo " dump_dir should contain <collection>.json files for each collection."
  exit 1
fi

host="$3"
if [ ! $3 ]; then
  host="localhost"
fi

port="$4"
if [ ! $4 ]; then
  port="27017"
fi

db=$1
dump_dir=$2

for dump_file in `ls $dump_dir`
do
  collection=${dump_file%.json}
  mongo "$db" --host "$host" --port "$port" --eval "db.${collection}.drop()"
  mongoimport --db "$db" --host "$host" --port "$port" --collection "$collection" --file "$dump_dir/$dump_file"
done
