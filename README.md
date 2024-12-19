# Beatsaver

[![Build Status](https://jenkins.kirkstall.top-cat.me/buildStatus/icon?job=Main)](https://jenkins.kirkstall.top-cat.me/view/Beatsaver/job/Main/)

The main codebase for the beatsaver website.

Contains both backend code for uploading and managing maps, the beatsaver API and a reactJS frontend all written in Kotlin with shared code.

## Local setup

#### Prerequisites
- [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) (Community Edition is fine)
- [Docker Desktop](https://www.docker.com/products/docker-desktop)

#### Lets go
- Run `docker-compose up -d` to start the local database
- Open the project in IntelliJ IDEA
- Run the `run` gradle task
- Navigate to http://localhost:8080
- Login with one of the test users
  - admin:admin
  - test:test
  - collab1:collab1
  - collab2:collab2

#### Extra environment variables
- `ZIP_DIR` Directory zips will get served from
- `COVER_DIR` Directory cover images will get served from
- `AUDIO_DIR` Directory preview audio will get served from
- `AVATAR_DIR` Directory avatars will get served from
- `PLAYLIST_COVER_DIR` Directory playlist covers will get served from
- `UPLOAD_DIR` Directory files get uploaded to before being processed

Zips, covers and audio files must be placed in a subfolder that is named with their first character

e.g. `cb9f1581ff6c09130c991db8823c5953c660688f.zip` must be in `$ZIP_DIR/c/cb9f1581ff6c09130c991db8823c5953c660688f.zip`

#### Solr search
If you choose to run the search server via docker compose you must set `SOLR_ENABLED=true`
Once running you can access the admin ui via http://localhost:8983 there is no authentication by default.

If you already have data in postgres this won't automatically be reflected in the search index. To run a full import go to:
- For maps - http://localhost:8983/solr/beatsaver/dataimport?command=full-import&clean=true
- For playlists - http://localhost:8983/solr/playlists/dataimport?command=full-import&clean=true
- For users - http://localhost:8983/solr/users/dataimport?command=full-import&clean=true

The `clean=true` at the end means all documents will be purged before the import. Without it maps/playlists would be added and updated but not removed if they were missing. It's also possible to swap `full-import` for `delta-import` to do a partial update which  will delete objects.

## Code Style

This project uses ktlint which provides fairly basic style rules.

You can run the `ktlintFormat` gradle task to automatically apply most of them or the `ktlintCheck` task to get a list of linting errors.