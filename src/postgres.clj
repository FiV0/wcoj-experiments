(ns postgres
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jsql]
            [honey.sql :as sql]
            [honey.sql.helpers :as help]
            [graph]))

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

(def delete-table-stmt "DROP TABLE g;")

(jdbc/execute! conn [delete-table-stmt])

(def create-table-stmt "CREATE TABLE g(f INT, t INT);")

(jdbc/execute! conn [create-table-stmt])

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

(defn insert-graph [conn graph]
  (jsql/insert-multi! conn :g [:f :t] graph {:batch true}))

(defn insert-graph! [conn graph]
  (jdbc/execute! conn [delete-table-stmt])
  (jdbc/execute! conn [create-table-stmt])
  (jsql/insert-multi! conn :g [:f :t] graph {:batch true}))

(comment
  (insert-graph conn (graph/complete-graph 200))
  (insert-graph conn (graph/complete-bipartite 200))
  (insert-graph conn (graph/star-graph 200))
  (insert-graph! conn (graph/star-graph 1000))
  (insert-graph! conn (graph/star-with-ring 1000))

  (def rand-g (graph/random-graph 1000 0.3))
  (insert-graph! conn rand-g)


  (insert-graph! conn (graph/complete-independents 300 10))

  (def rand-inds (graph/random-independents 300 10 0.3))
  (def rand-inds2 (graph/random-independents 200 7 0.3))
  (insert-graph! conn rand-inds2)

  )


(def triangle-query
  '{:select [[g1.f a] [g1.t b] [g2.t c]]
    :from [[g g1] [g g2] [g g3]]
    :where [:and [:= g1.t g2.f] [:= g2.t g3.t] [:= g1.f g3.f]]})

(def query
  "
SELECT
    g1.f AS a, g1.t AS b, g2.t AS c
FROM
    g AS g1, g AS g2, g AS g3
WHERE
    g1.t = g2.f AND g2.t = g3.t AND g1.f = g3.f;")

(time (jdbc/execute! conn [query]))
(time (jdbc/execute! conn (sql/format triangle-query)))

(defn- no-repetition [vars]
  (loop [res [] vars vars]
    (if (seq vars)
      (recur (into res (map #(vector (first vars) %) (nthrest vars 2))) (rest vars))
      res)))

(defn k-path-query [k]
  (let [vars (map #(symbol (str "v" %)) (range (inc k)))]
    (-> {:from (mapv #(vector 'g (symbol (str "g" %))) (range k))
         :where (into [:and]
                      (concat
                       (map #(vector := (symbol (str "g" % ".t")) (symbol (str "g" (inc %) ".f"))) (range (dec k)))
                       (map (fn [[i j]] (vector :!= (symbol (str "g" i ".t")) (symbol (str "g" j ".t"))))
                            (no-repetition (range k)))))
         :select (-> (vec (map-indexed #(vector (symbol (str "g" %1 ".f")) %2) (butlast vars)))
                     (conj (vector (symbol (str "g" (dec k) ".t")) (last vars))))})))

(comment
  (k-path-query 4))

(time (jdbc/execute! conn (sql/format (k-path-query 4))))
