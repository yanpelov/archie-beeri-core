#!/bin/sh

# Import back to Solr
curl 'http://localhost:8983/solr/archie_beeri/update/json/docs' -H 'Content-type:application/json' -d @import.json
