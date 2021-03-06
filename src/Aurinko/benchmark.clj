(ns Aurinko.benchmark
  (:use [clojure.java.io :only [file]])
  (:require (Aurinko [hash :as hash] [col :as col] [fs :as fs] [query :as query])))

(defn -main [& args]
  (Thread/sleep 10000)
  (.mkdir (file "col0"))
  (.mkdir (file "col1"))
  (let [col0 (col/open "col0")
        col1 (col/open "col1")]
    (prn "exercising buffer..")
    (doseq [v (range 5000)] (col/insert col0 {:tag v}))
    (doseq [v (col/all col0)] (col/update col0 (assoc v :tag2 v)))
    (doseq [v (col/all col0)] (col/delete col0 v))
    
    (let [h (hash/new "hash" 12 100)]
      (prn "Hash - put 100k entries")
      (time (doseq [v (range 100000)] (hash/kv h v v)))
      (prn "Hash - get 100k entries")
      (time (doseq [v (range 100000)] (hash/k h v 1 (fn [_] true))))
      (prn "Hash - invalidate 100k entries")
      (time (doseq [v (range 100000)] (hash/x h v 1 (fn [_] true))))
      (.delete (file "hash")))
    (prn)
  
    (prn "Collection - insert 10k documents (3 indexes)")
    (col/index-path col1 [:thing1])
    (col/index-path col1 [:thing2])
    (col/index-path col1 [:a1 :a2 :a3])
    (time (doseq [v (range 10000)]
            (col/insert col1 {:a1 {:a2 {:a3 (rand-int 10000)}}
                              :thing1 (str (rand-int 10000))
                              :thing2 (str (rand-int 10000))
                              :map {:complex {:data (rand-int 10000)}}
                              :action ["insert" "benchmark"]
                              :purpose "benchmark"})))
    (prn "Collection - update 10k documents (3 indexes)")
    (time (doseq [v (col/all col1)] (col/update col1 (assoc v :action "u"))))
    (prn "Collection - update 10k documents (3 indexes, grow each document)")
    (time
      (doseq [v (col/all col1)]
        (col/update col1 (assoc v :extra "123456789012345678901234567890123456789012345678901234567890"))))
    (prn "Collection - read all 10k documents")
    (time (col/all col1))
    (prn "Collection - index 10k documents")
    (time (col/index-path col1 [:map :complex :data]))
    (prn "Query - index lookup 10k items")
    (time (doseq [v (range 10000)]
            (let [val (rand-int 10000)]
              (query/q col1 [:col [:map :complex :data] val -1 :eq]))))
    (prn "Query - scan collection (no index)")
    (time (query/q col1 [:col [:action] "insert" -1 :eq]))
    (prn "Query - complex (no index)")
    (time (query/q col1 [:col [:purpose] "benchmark" -1 :eq
                         [:map :complex :not :exist] :not-have
                         :col [:map :complex :data]  5000 -1 :ge
                         [:a1 :a2 :a3] 500 -1 :lt
                         :diff]))
    (time (query/q col1 [:col [:action] "insert" -1 :eq
                         :col [:map :complex :data]  5000 -1 :gt
                         [:map :complex :data] :has
                         [:a1 :a2 :a3] 500 -1 :lt
                         :union]))
    (time (query/q col1 [:col [:action] "benchmark" -1 :eq
                         :col [:map :complex :data]  5000 -1 :gt
                         [:a1 :a2 :a3] 500 -1 :lt
                         :intersect
                         [:a1 :a2 :a3] :asc]))
    (prn "Collection - delete 10k documents")
    (time (doseq [v (col/all col1)] (col/delete col1 v))))
  (fs/rmrf (file "col0"))
  (fs/rmrf (file "col1")))
