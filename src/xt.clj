(ns xt
  (:require [clojure.java.io :as io]
            [clojure.math.combinatorics :as combo]
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

(def xtdb-node (start-xtdb!))

(defn graph->docs [edges]
  (->> edges
       (reduce (fn [graph [i j]] (-> graph (update i (fnil conj #{}) j) (update j (fnil identity #{})))) {})
       (map (fn [[i neighbours]] {:xt/id i :g/to neighbours}))))

(comment (graph->docs (graph/complete-graph 5) ))

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
  (xt/submit-tx mem-node (->> postgres/rand-inds graph->docs wrap-in-puts))


  )

(def triangleq '{:find [?a ?b ?c]
                 :where [[?a :g/to ?b]
                         [?a :g/to ?c]
                         [?b :g/to ?c]]})

(defn k-path-query [k]
  (let [vars (vec (repeatedly (inc k) #(symbol (str "?" (gensym)))))]
    (-> {:find vars}
        (assoc :where (mapv #(vector %1 :g/to %2) vars (rest vars)))
        (update :where (comp vec concat) (mapv #(vector (list '!= %1 %2)) vars (rest vars))))))

(comment
  (k-path-query 4)
  )

(comment
  (time (xt/q (xt/db mem-node) (assoc triangleq :timeout 120000)))
  (time (xt/q (xt/db mem-node) (assoc (k-path-query 3) :timeout 120000)))

  (count *1)
  )



(comment
  (def data [{:xt/id 1 :g/to #{2 3}} {:xt/id 2 :g/to #{3}} {:xt/id 3 :g/to #{}}])

  (xt/submit-tx mem-node (wrap-in-puts data) )

  (xt/submit-tx mem-node [[::xt/put {:xt/id 2 :g/to #{3}}]])

  (xt/q (xt/db mem-node) triangle-other)

  (xt/q (xt/db mem-node) '{:find [?a ?b]
                           :where [[?a :g/to ?b]
                                   [?a :g/to ?c]
                                   #_[?b :g/to ?c]]})




  )

(defn graph->docs [graph]
  (map (fn [[i j]] {:xt/id (str i "-" j) :g/from i :g/to j}) graph))


(comment
  (xt/submit-tx mem-node (->> (graph/complete-graph 200) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-bipartite 200) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-graph 1000) graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/star-with-ring 1000) graph->docs wrap-in-puts))

  (require '[postgres])

  (xt/submit-tx mem-node (->> postgres/rand-g graph->docs wrap-in-puts))
  (xt/submit-tx mem-node (->> (graph/complete-independents 300 10) graph->docs wrap-in-puts))


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
