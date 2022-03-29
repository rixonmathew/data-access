# data-access

To try out normal and reactive data access

## Starting h2 as docker images
Source https://hub.docker.com/r/oscarfonts/h2/
> docker pull oscarfonts/h2
> docker run -d -p 11521:1521 -p 11581:81 -v /mnt/d//h2/docker-data:/opt/h2-data -e H2_OPTIONS=-ifNotExists --name=h2-docker oscarfonts/h2
>
