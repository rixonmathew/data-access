#To startup Cassandra locally 
## Windows - docker local
Source https://cassandra.apache.org/quickstart/
> docker pull cassandra:latest
> 
> docker network create cassandra
> 
> docker run --rm -d --name cassandra -p 9142:9042 --hostname cassandra --network cassandra cassandra

cd to resources folder where data.cql is kept
> docker run --rm --network cassandra -v "%CD%\data.cql:/scripts/data.cql" -e CQLSH_HOST=cassandra -e CQLSH_PORT=9042 nuvo/docker-cqlsh

Interactive CQLSH 
> docker run --rm -it --network cassandra nuvo/docker-cqlsh cqlsh cassandra 9042 --cqlversion='3.4.4'


## MacOS - cassandra via homebrew install (docker setup shown above will also work on M1 macs where arm image is not yet available)

> docker pull cassandra:latest
>
> docker network create cassandra
>
> docker run --rm -d --name cassandra -p 9142:9042 --hostname cassandra --network cassandra cassandra

cd to resources folder where data.cql is kept
> docker run --rm --network cassandra -v "$(pwd)/data.cql:/scripts/data.cql" -e CQLSH_HOST=cassandra -e CQLSH_PORT=9042 nuvo/docker-cqlsh

Interactive CQLSH
> docker run --rm -it --network cassandra nuvo/docker-cqlsh cqlsh cassandra 9042 --cqlversion='3.4.4'
