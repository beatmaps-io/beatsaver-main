#!/bin/bash

cd /var/solr/data

for core in "beatsaver" "playlists" "users"; do
  for fileName in "solrconfig.xml" "managed-schema.xml" "solr-data-config.xml"; do
    ln -sf /mnt/solr/$core/$fileName $core/conf/$fileName
  done
done
