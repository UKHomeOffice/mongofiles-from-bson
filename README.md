
# mongofiles-bsonreader

Mongo database has supported storing files inside it's databases using a feature called GridFs. Users can use a mongofiles command to upload/download files between their computer and their Mongo server.

This application allows you to extract gridfs files directly a bson.gz Mongo database backup, instead of having ton restore a database backup to a live server before you recover it. This application uses Mongo's official drivers to read the bson structures and restore files correctly.

The scenario which showcases where this app is useful:

```bash
# start a database server

# upload a file to it, using mongofiles

# backup the database using mongodump

# you now have historic files within the two files fs.files.bson.gz and fs.chunks.bson.gz.
```

In the Home Office we do not wish our production data to leave the production environment, however also do not wish to restore historic files into the live database where they could conflict with production data without tested results, and also without configuring a different database within production. This application allows us to search and extract files from the backups directly.

```bash
java -jar mongofiles-bsonreader.jar -c chunks.bson.gz -i fs.files.bson.gz --match "2022Report.*.csv" list
```

In the example above, we simply deploy the jar file to the target environment and can run it anywhere a JVM is available.

* Pass the `-c` (or `--chunks`) argument to specify where the chunks collection is.
* Pass the `-i` (or `--index`) argument to specify where the index collection is. This is collection that keeps files.
* You can optionally provide a `-m` (or `--match`) argument to search for a specific file or group of files. This is a regular expression match so, `--match "Reg*"` will match Regggg but not Rega. Use `.*` where required.
* The final argument needs to be either the `list` command which lists files. (Use this first to santiy check things)
* To extract a file from the backup use the `extract` command.

```bash
java -jar mongofiles-bsonreader.jar -c chunks.bson.gz -i fs.files.bson.gz -w /home/phill extract
```

* The `-w` (or `--write-dir`) argument specifies the directoty where the file should be written to. It will keep it's original name. If a value isn't provided, the value defaults to /tmp
* If you run extract without the match argument it will match + extract all the files.

# Design

This applicaiton is written in Scala and uses the fs2 streaming library. It's streaming architecture, that includes gzip decompression, filtering and file reassembling by design has been tested on backups larger than 10GB with minimal ram requirements.

# Deployment

Java's slogan is "write-once, run anywhere". You can take the jar file and run it on any Java > JDK 17.
