# cockroachdb

Accessing Data from Cockroachdb
```shell
## Starting cockroachdb cluster in docker insecure mode
# Source https://www.cockroachlabs.com/docs/v21.2/start-a-local-cluster-in-docker-linux
# Create a network
docker network create -d bridge roachnet

# start first node
docker run -d --name=roach1 --hostname=roach1 --net=roachnet -p 26257:26257 -p 8085:8080  -v "${PWD}/cockroach-data/roach1:/cockroach/cockroach-data"  cockroachdb/cockroach:v21.2.4 start --insecure --join=roach1,roach2,roach3

# start second node
docker run -d --name=roach2 --hostname=roach2 --net=roachnet -v "${PWD}/cockroach-data/roach2:/cockroach/cockroach-data" cockroachdb/cockroach:v21.2.4 start --insecure --join=roach1,roach2,roach3

# start third node
docker run -d --name=roach3 --hostname=roach3 --net=roachnet -v "${PWD}/cockroach-data/roach3:/cockroach/cockroach-data" cockroachdb/cockroach:v21.2.4 start --insecure --join=roach1,roach2,roach3

# One time initialization
docker exec -it roach1 ./cockroach init --insecure

# Check if the server started up successfully
docker exec -it roach1 grep 'node starting' cockroach-data/logs/cockroach.log -A 11

# connect to node
docker exec -it roach1 ./cockroach sql --insecure


#Simulating load
docker exec -it roach1 ./cockroach workload init movr 'postgresql://root@roach1:26257?sslmode=disable'

docker exec -it roach1 ./cockroach workload run movr --duration=5m 'postgresql://root@roach1:26257?sslmode=disable'
```


## Starting a secure cluster running on mac mini 2011
```shell
cockroach cert create-ca --certs-dir=certs --ca-key=my-safe-directory/ca.key
cockroach cert create-node localhost $(hostname) --certs-dir=certs --ca-key=my-safe-directory/ca.key
cockroach cert create-client rixon --certs-dir=certs --ca-key=my-safe-directory/ca.key
cockroach start --certs-dir=certs --store=node1 --listen-addr=localhost:26257 --http-addr=localhost:8080 --join=localhost:26257,localhost:26258,localhost:26259 --background
cockroach start --certs-dir=certs --store=node2 --listen-addr=localhost:26258 --http-addr=localhost:8081 --join=localhost:26257,localhost:26258,localhost:26259 --background
cockroach start --certs-dir=certs --store=node3 --listen-addr=localhost:26259 --http-addr=localhost:8082 --join=localhost:26257,localhost:26258,localhost:26259 --background
cockroach init --certs-dir=certs --host=localhost:26257
cockroach cert create-client root --certs-dir=certs --ca-key=my-safe-directory/ca.key
cockroach init --certs-dir=certs --host=localhost:26257
grep 'node starting' node1/logs/cockroach.log -A 11
grep 'node starting' node2/logs/cockroach.log -A 11
grep 'node starting' node3/logs/cockroach.log -A 11
cockroach sql --certs-dir=certs --host=localhost:26257
ps -aef | grep cockroach
cockroach
cockroach node list
cockroach node ls
cockroach node ls --certs-dir=certs
cockroach node status --certs-dir=certs
cockroach
cockroach quit --certs-dir=certs --host=localhost:26257
cockroach quit --certs-dir=certs --host=localhost:26258
cockroach quit --certs-dir=certs --host=localhost:26259
cockroach node drain --certs-dir=certs
cockroach node ls --certs-dir=certs
ps -aef | grep cockroach
cockroach node drain --certs-dir=certs --host=localhost:26259
ps -aef | grep cockroach
ps -aef | grep cockroach
cd cockroach-data/
cockroach --version
```
