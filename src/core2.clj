(ns core2
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as jsql]
            [graph]))

;; supposes docker image is running on
(def ds
  (jdbc/get-datasource
   {:dbtype "postgresql"
    :host "localhost"
    :port 5432
    :password "test"
    :user "test"}))

(def ds (jdbc/get-datasource
         {:dbtype "postgresql"
          :dbname "test"
          :host "localhost"
          :port 5432
          :password "test"
          :user "test"}))

(def graph-statement
  "
INSERT INTO g(id, f, t)
VALUES
(0, 1, 2), (1, 1, 3), (2, 1, 4), (3, 2, 4), (4, 2, 5),
(5, 3, 4), (6, 3, 6), (7, 3, 7), (7, 4, 5), (8, 4, 7),
(9, 4, 8), (10, 5, 8), (11, 6, 7), (12, 7, 8);
")

(comment
  (time (jdbc/execute! ds [graph-statement])))

(def select-test "SELECT g.id, g.f, g.t FROM g;")

(def query
  "
SELECT
    g1.f AS a, g1.t AS b, g2.t AS c
FROM
    g AS g1, g AS g2, g AS g3
WHERE
    g1.t = g2.f AND g2.t = g3.t AND g1.f = g3.f;")



(comment
  (jdbc/execute! ds [select-test])
  (jdbc/execute! ds [query])
  )

(comment
  (require 'sc.api
           '[next.jdbc.sql.builder :as builder])

  (def rand-g (graph/random-graph 1000 0.3))
  (count rand-g)
  (first rand-g)

  (def startl "set transaction read write; begin;")
  (def endl "commit;")

  (time
   (with-open [conn (jdbc/get-connection ds)
               ps (jdbc/prepare conn ["insert into g (id,f,t) values (?,?,?)"]
                                {:return-keys false})]
     (jdbc/execute! conn [startl])
     (jdbc/execute-batch! ps (map into (map vector (iterate inc 0)) rand-g)
                          {:return-generated-keys false})
     (jdbc/execute! conn [endl])))

  (time
   (jdbc/with-transaction [tx ds]
     (with-open [ps (jdbc/prepare tx ["insert into g (id,f,t) values (?,?,?)"]
                                  {:return-keys false})]
       (jdbc/execute-batch! ps (map into (map vector (iterate inc 0)) rand-g)
                            {:return-generated-keys false}))))


  (time (count (jdbc/execute! ds [query])))

  (first (jdbc/execute! ds [query]))

  (def ds (jdbc/get-datasource
           {:dbtype "postgresql"
            :dbname "test"
            :host "localhost"
            :port 5432
            :password "test"
            :user "test"}))

  (time
   (with-open [conn (jdbc/get-connection ds)
               ps (jdbc/prepare conn ["insert into g (id,f,t) values (?,?,?)"]
                                {:return-keys false})]
     (jdbc/execute-batch! ps (->> (map vector
                                       (iterate inc 0)
                                       (repeatedly #(rand-int 1000))
                                       (repeatedly #(rand-int 1000)))
                                  (take 100))
                          {:return-generated-keys false}))))
