# data-access

To try out normal and reactive data access

## Starting yugabyte as docker image
Source https://docs.yugabyte.com/latest/quick-start/install/docker/
> docker pull yugabytedb/yugabyte
> docker run -d --name yugabyte  -p7000:7000 -p9000:9000 -p5433:5433 -p9042:9042 yugabytedb/yugabyte:latest bin/yugabyted start --daemon=false
> 
With data persistence option
> docker run -d --name yugabyte  -p7000:7000 -p9000:9000 -p5433:5433 -p9042:9042 -v /mnt/d/yugabyte/data:/home/yugabyte/var/ yugabytedb/yugabyte:latest bin/yugabyted start --daemon=false 


Connect via client
> docker exec -it yugabyte /home/yugabyte/bin/ysqlsh --echo-queries`    