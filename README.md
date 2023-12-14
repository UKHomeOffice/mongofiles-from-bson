
# mongofiles-from-bson

Mongo database has supported storing files inside it's databases using a feature called GridFs. Users can use a mongofiles command to upload/download files between their computer and their Mongo server.

This application allows you to extract gridfs files directly a bson.gz Mongo database backup, instead of having ton restore a database backup to a live server before you recover it. This application uses Mongo's official drivers to read the bson structures and restore files correctly.

The scenario which showcases where this app is useful:

```bash
# start a database server
docker run --rm --name=mongo mongo:7.0 mongod --bind_ip=0.0.0.0

# upload a file to it, using mongofiles
docker exec -it mongo /bin/bash

echo "hello, World" > /tmp/testfile.txt
mongofiles -d testdb put /tmp/testfile.txt

mongofiles -d testdb list

# backup the database using mongodump
mongodump --gzip -d mydb

             (output from script)
             2023-12-13T15:09:12.866+0000    writing mydb.fs.chunks to dump/mydb/fs.chunks.bson.gz
             2023-12-13T15:09:12.870+0000    done dumping mydb.fs.chunks (1 document)
             2023-12-13T15:09:12.871+0000    writing mydb.fs.files to dump/mydb/fs.files.bson.gz
             2023-12-13T15:09:12.872+0000    done dumping mydb.fs.files (1 document)

# you now have historic files within the two files fs.files.bson.gz and fs.chunks.bson.gz.
# take these to an environment without Mongo database, or where there is little desire to run mongorestore against it.
# extract the files without mongo

docker cp mongo:/dump /tmp

# use our program to extract the files
# (see the release tab)

java -jar mongofiles-from-bson-v1.jar -c /tmp/dump/mydb/fs.chunks.bson.gz -f /tmp/dump/mydb/fs.files.bson.gz

            (example output)
            MATCHED: /tmp/testfile.txt (chunks: 1)

```

In the Home Office we do not wish our production data to leave the production environment, however also do not wish to restore historic files into the live database where they could conflict with production data without tested results, and also without configuring a different database within production. This application allows us to search and extract files from the backups directly.

```bash
java -jar mongofiles-from-bson-v1.jar -c chunks.bson.gz -f fs.files.bson.gz --match "2022Report.*.csv"
```

In the example above, we simply deploy the jar file to the target environment and can run it anywhere a JVM is available.

* Pass the `-c` (or `--chunks`) argument to specify where the chunks collection is.
* Pass the `-f` (or `--files`) argument to specify where the files collection is. This is effectively the index for the chunks
* You can optionally provide a `-m` (or `--match`) argument to search for a specific file or group of files. This is a regular expression match so, `--match "Reg*"` will match Regggg but not Rega. Use `.*` where required.

```bash
java -jar mongofiles-from-bson-v1.jar -c chunks.bson.gz -f fs.files.bson.gz -w /home/phill -x
```

* The `-x` (or `--extract`) flag tells the app to extract any files from the dump, effectively recovering them from the bson image.
* The `-w` (or `--write-dir`) argument specifies the directory where the matching files should be written. Files keep their original name. If a value isn't provided, the app defaults to /tmp

# Design

This applicaiton is written in Scala and uses the fs2 streaming library. It's streaming architecture, that includes gzip decompression, filtering and file reassembling by design has been tested on backups larger than 10GB with minimal ram requirements.

# Deployment

Java's slogan is "write-once, run anywhere". You can take the jar file and run it on any Java > JDK 17.
