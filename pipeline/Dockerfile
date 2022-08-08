FROM openjdk:8-buster

# Install Build Essentials
RUN apt-get update
RUN apt-get install build-essential -y

RUN apt-get install -y ant

#Install go and github-release app
#Tempory comment out
RUN apt-get install -y golang
RUN go get github.com/github-release/github-release

CMD ["/bin/bash"]
