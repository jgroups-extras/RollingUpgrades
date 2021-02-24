#!/bin/bash

## Generates a server (and optionally) a client certificate, plus a public/private key pair. When running the server
# and clients in secure mode (enrypted gRpc connections):
# 1. Make sure HOST is set to the hostname on which UpgradeServer is running
# 2. Run UpgradeServer with -cert server.cert -key server.key
# 3. Set server_cert in the UPGRADE protocol to "server.cert"

HOST=localhost ## `hostname`
printf "Generating server public/private keypair and certificate with hostname set to %s\n" $HOST

## Generate server key and cert (common name should be the hostname of the UpgradeServer):
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout ./server.key -out ./server.cert \
-subj "/C=CH/ST=Thurgau/L=Kreuzlingen/O=Red Hat/CN=localhost"

## Generate client cert:
# openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout ./client.key -out ./client.cert \
#   -subj "/C=CH/ST=Thurgau/L=Kreuzlingen/O=Red Hat/CN=localhost"