# Zorba
## How to reload the lemmas in the ***Zorba*** app?


## Source table
The source table is in the MS Access Database application ***Grieks***  
The source table is named ***Woorden***  
It contains the lemmas we want as well as some song lyrics that we normally don't want in our app.  

## Download location
The database that provides the records for the json data that gets imported into the Zorba app, is a MySQL database on the *DriemondGlas.nl* server.
This MySQL database is named *deb11873_library*, the table is named ***woorden100***. 

## Database Synchronisation
Not the original MS Access table *Woorden* is synchronised but a clone named *woorden1001*.  
The link between the Access table and MySQL table only works if the IP address (by NordVPN) of the Windows PC is listed in the Access Host List of the MySQL database.  
When you can open the MS Access linked table ***woorden1001*** without errors, then the link is functional.  

## Add current active IP address to Host List
* Check the IP address of the Windows PC by browsing to www.whatismyipaddress.com (example Netherlands #435: 185.132.178.56)
* Go to Antagonist's  [Direct Admin panel](https://s187.webhostingserver.nl:2222/) 
* Go to home ➜ MySQL Management
* Select Database *deb11873_library*
* Check *Access Hosts IP* for correct address and if not listed add address returned from whatismyipaddress.com
* Confirm by opening linked table *woorden1001* in the MS Access application

## Update to most recent changes in MS Access Grieks database application
* Delete all records from table *woorden1001*. This may take a while because the data is actually deleted on the remote MySQL database). Check the progress bar in the MS Access App.  
* Check the result (all records deleted) in the remote database: Via the Antagonist's Direct Admin panel, login to phpMyAdmin, user deb11873_dmglas (passwd in password app).  
* Run append query in MS Access *qryAppend1001*. This also takes a while for the records to be moved to remote database.   
* Check the result (new records visible) in the remote database.
 
## Download new table in the Zorba app on your phone
* Main Activity ➜ menu ➜ Importeer Database  
* Confirm  
* Done  
