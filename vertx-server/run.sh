#!/bin/bash
cd "$(dirname "$0")"
echo "Starting IPAM Vert.x Server..."
mvn exec:java
