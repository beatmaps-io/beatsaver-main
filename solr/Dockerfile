FROM solr:9

# Libs
RUN mkdir -p /var/solr/lib && \
    wget https://github.com/SearchScale/dataimporthandler/releases/download/v9.4.0/data-import-handler-9.4.0.jar -O /var/solr/lib/data-import-handler-9.4.0.jar && \
    wget https://jdbc.postgresql.org/download/postgresql-42.7.4.jar -O /var/solr/lib/postgresql-42.7.4.jar

# Core
RUN mkdir -p /var/solr/data && \
    /opt/solr/bin/solr start && \
    /opt/solr/bin/solr create_core -c beatsaver && \
    /opt/solr/bin/solr create_core -c playlists && \
    /opt/solr/bin/solr create_core -c users && \
    /opt/solr/bin/solr stop

# Symlink to where we will mount files
COPY symlink.sh .
RUN ./symlink.sh
