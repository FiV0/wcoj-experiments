(ns graph
  (:require [clojure.math.combinatorics :as combo]))

(defn complete-graph [n]
  (for [i (range n) j (range (inc i) n)]
    [i j]))

(defn complete-bipartite [n]
  (let [n1 (quot n 2)]
    (for [i (range n1) j (range n1 n)]
      [i j])))

(defn star-graph [n]
  (for [i (range 1 n)]
    [0 i]))

(defn star-with-ring [n]
  (concat
   (for [i (range 1 n)]
     [0 i])
   (for [i (range 1 (dec n))]
     [i (inc i)])))

(defn random-graph [n p]
  (filter (fn [_] (<= (rand) p)) (complete-graph n)))

(defn complete-independents [n k]
  (let [k1 (quot n k)
        k2 (inc k1)
        n2 (* k2 (rem n k))
        vertices (concat
                  (partition k2 (take n2 (range n)))
                  (partition k1 (drop n2 (range n))))]
    (->> (combo/combinations vertices 2)
         (mapcat #(apply combo/cartesian-product %))
         (map vec))))

(defn random-independents [n k p]
  (filter (fn [_] (<= (rand) p)) (complete-independents n k)))

(comment
  (complete-graph 5)
  (complete-bipartite 5))
