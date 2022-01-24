# cockroachdb

Accessing Data from Cockroachdba

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



## Starting a secure cluster running on mac mini 2011