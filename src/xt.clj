(ns xt
  (:require [clojure.java.io :as io]
            [xtdb.api :as xt]
            [graph]))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/tx-log")
      :xtdb/document-store (kv-store "data/doc-store")
      :xtdb/index-store (kv-store "data/index-store")})))

(def xtdb-node (start-xtdb!))

(defn graph->docs [graph]
  (map (fn [[i j]] {:xt/id (str i "-" j) :g/from i :g/to j}) graph))

(defn wrap-in-puts [docs]
  (map #(vector ::xt/put %) docs))

(comment
  (graph->docs (graph/complete-graph 3)))

(def mem-node (xt/start-node {}))
(comment
  (xt/submit-tx mem-node (->> (graph/complete-graph 200) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-bipartite 200) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-graph 1000) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-with-ring 1000) graph->docs wrap-in-puts))

  (require '[postgres])

  (xt/submit-tx mem-node (->> postgres/rand-g graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-independents 300 10) graph->docs wrap-in-puts))


  )

'{:find [?a ?b ?c]
  :where [[?a :g/to ?b]
          [?a :g/to ?c]
          [?b :g/to ?c]]}

'{:find [?a ?b ?c]
  :where [[?e :g/to ?a]
          [?e2 :g/from ?a]
          [?e2 :g/to ?b]
          [?e3 :g/to ?b]
          [?e :g/from ?c]
          [?e3 :g/from ?c]]}

(def triangle-q '{:find [?a ?b ?c]
                  :where [[?e :g/to ?a]
                          [?e2 :g/from ?a]
                          [?e2 :g/to ?b]
                          [?e3 :g/to ?b]
                          [?e :g/from ?c]
                          [?e3 :g/from ?c]]})

(def triangle-q2 '{:find [?a ?b ?c]
                   :where [[?e :g/from ?c]
                           [?e :g/to ?a]
                           [?e2 :g/from ?a]
                           [?e2 :g/to ?b]
                           [?e3 :g/to ?b]
                           [?e3 :g/from ?c]]})


(comment
  (time (xt/q (xt/db mem-node) (assoc triangle-q :timeout 120000)))
  (time (xt/q (xt/db mem-node) (assoc triangle-q :timeout 60000)))
  (time (xt/q (xt/db mem-node) (assoc triangle-q2 :timeout 60000)))
  )
