(ns postgres
  (:require [next.jdbc :as jdbc]))

(def ds (jdbc/get-datasource
         {:dbtype "postgresql"
          :host "localhost"
          :port 5432
          :password "postgres"
          :user "postgres"}))

(def create-db "CREATE DATABASE wcoj")

(jdbc/execute! ds [create-db])

(def conn (jdbc/get-datasource
           {:dbtype "postgresql"
            :dbname "wcoj"
            :host "localhost"
            :port 5432
            :password "postgres"
            :user "postgres"}))

(def graph-statement
  "
CREATE TABLE g(f INT, t INT);
INSERT INTO g
VALUES
(1, 2), (1, 3), (1, 4), (2, 4), (2, 5),
(3, 4), (3, 6), (3, 7), (4, 5), (4, 7),
(4, 8), (5, 8), (6, 7), (7, 8);
")

(jdbc/execute! conn [graph-statement])

(def query
  "
SELECT
    g1.f AS a, g1.t AS b, g2.t AS c
FROM
    g AS g1, g AS g2, g AS g3
WHERE
    g1.t = g2.f AND g2.t = g3.t AND g1.f = g3.f;")

(jdbc/execute! conn [query])
