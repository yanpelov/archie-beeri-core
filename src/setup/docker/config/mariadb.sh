#!/bin/sh
runuser -u mysql -- /usr/libexec/mariadb-prepare-db-dir
runuser -u mysql -- /usr/bin/mysqld_safe &
sleep 10
mysqladmin -u root create archie_beeri
mysql -u root -e "GRANT ALL ON archie_beeri.* TO 'archie'@'localhost' IDENTIFIED BY '1234'";
cat ./config/mariadb.tables.sql | mysql -u root archie_beeri