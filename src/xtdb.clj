(ns xtdb
  (:require [clojure.java.io :as io]
            [graph]
            [xtdb.api :as xt]))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/tx-log")
      :xtdb/document-store (kv-store "data/doc-store")
      :xtdb/index-store (kv-store "data/index-store")})))

(comment
  (def xtdb-node (start-xtdb!)))

(defn graph->docs [edges]
  (->> edges
       (reduce (fn [graph [i j]] (-> graph (update i (fnil conj #{}) j) (update j (fnil identity #{})))) {})
       (map (fn [[i neighbours]] {:xt/id i :g/to neighbours}))))

(comment
  (graph->docs (graph/complete-graph 5)))

(defn wrap-in-puts [docs]
  (map #(vector ::xt/put %) docs))

(def mem-node (xt/start-node {}))
(comment
  (xt/submit-tx mem-node (->> (graph/complete-graph 200) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-bipartite 200) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-graph 1000) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-with-ring 1000) graph->docs wrap-in-puts))

  (require '[postgres])

  (xt/submit-tx mem-node (->> postgres/rand-g graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-independents 300 10) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> postgres/rand-inds graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> postgres/rand-inds2 graph->docs wrap-in-puts))

  (.close mem-node)
  )

(def triangleq '{:find [?a ?b ?c]
                 :where [[?a :g/to ?b]
                         [?a :g/to ?c]
                         [?b :g/to ?c]]})

(defn- no-repetition [vars]
  (loop [res [] vars vars]
    (if (seq vars)
      (recur (into res (map #(vector (list '!= (first vars) %)) (nthrest vars 2))) (rest vars))
      res)))

(comment
  (no-repetition (range 4)))

(defn k-path-query [k]
  (let [vars (vec (repeatedly (inc k) #(symbol (str "?" (gensym)))))]
    (-> {:find vars}
        (assoc :where (mapv #(vector %1 :g/to %2) vars (rest vars)))
        (update :where (comp vec concat) (no-repetition vars)))))

(comment
  (k-path-query 4))

(defn create-and-query [graph query]
  (with-open [node (xt/start-node {})]
    (xt/submit-tx node (->> graph graph->docs wrap-in-puts))
    (xt/sync node)
    (time (xt/q (xt/db node) query))))

(comment
  (time (xt/q (xt/db mem-node) (assoc triangleq :timeout 120000)))
  (time (xt/q (xt/db mem-node) (assoc (k-path-query 4) :timeout 120000)))

  (create-and-query (graph/complete-independents 100 7) (k-path-query 3))

  )

;;///////////////////////////////////////////////////////////////////////////////
;;===============================================================================
;;                                 old approach
;;===============================================================================
;;\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

;; some queries below took quite a while to execute, maybe worth investigating

(defn graph->docs2 [graph]
  (map (fn [[i j]] {:xt/id (str i "-" j) :g/from i :g/to j}) graph))

(comment
  (xt/submit-tx mem-node (->> (graph/complete-graph 200) graph->docs2 wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-bipartite 200) graph->docs2 wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-graph 1000) graph->docs2 wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-with-ring 1000) graph->docs2 wrap-in-puts))

  (require '[postgres])

  (xt/submit-tx mem-node (->> postgres/rand-g graph->docs2 wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-independents 300 10) graph->docs2 wrap-in-puts))

  )

;; takes a long time for certain graphs
(def triangle-q '{:find [?a ?b ?c]
                  :where [[?e :g/to ?a]
                          [?e2 :g/from ?a]
                          [?e2 :g/to ?b]
                          [?e3 :g/to ?b]
                          [?e :g/from ?c]
                          [?e3 :g/from ?c]]})

;; faster
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
