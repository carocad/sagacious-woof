(ns hypobus.simulation.data-handler
  (:require [hypobus.basics.geometry :as geo]
            [hypobus.util :as tool]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(defn- parse-number
  "coerces a string containing either an integer or a floating point number"
  [^String str-number]
  (if (.contains str-number ".")
    (float (bigdec str-number))
    (int (bigint str-number))))

(def ^:cons ^:private csv->hash-map (partial zipmap [:timestamp :line-id :direction
     :journey-pattern-id :time-frame :vehicle-journey-id :operator
     :congestion :lon :lat :delay :block-id :vehicle-id :stop-id :at-stop]))

(defn journey-id
  [{journey-pattern-id :journey-pattern-id}]
  (cond
    (zero? (count journey-pattern-id)) "EMPTY-ID"
    (< (count journey-pattern-id) 4) journey-pattern-id
    :else (let [line-pattern (seq (subs journey-pattern-id 0 4))
                journey-seq  (drop-while #(= \0 %) line-pattern)]
            (apply str journey-seq))))

(defn fetch-data
  ([filename]
   (fetch-data filename nil))
  ([filename xform]
  (with-open [in-file (io/reader filename)]
    (let [raw-data       (csv/read-csv in-file :separator \,)
          vec->hashmap   (map csv->hash-map)
          ;taker          (take 1000)
          remove-null    (remove #(= (:journey-pattern-id %) "null"))
          str->num       (map #(tool/update-vals % [:lat :lon] parse-number))
          data-reader    (comp vec->hashmap remove-null str->num)
          processesor    (if xform (comp data-reader xform)
                           data-reader)]
      (into [] processesor raw-data)))))

(defn fetch-line [filename line-id] (fetch-data filename (filter #(= (:line-id %) line-id))))
(defn fetch-journeys [filename journeys] (fetch-data filename (filter #((set journeys) (:journey-pattern-id %)))))
(defn fetch-all [filename] (fetch-data filename (map #(select-keys % [:lat :lon :journey-pattern-id :vehicle-journey-id]))))
(defn fetch-parsed-id [filename line-id] (fetch-data filename (filter #(=  (journey-id %) line-id))))

(defn organize-journey
  [data]
  (let [gap-remover    (mapcat #(geo/split-at-gaps geo/haversine %))
        trans-curves   (map #(geo/tidy geo/haversine %))
        remove-fault   (remove #(< (count %) 5))
        prepare-data   (comp gap-remover trans-curves remove-fault)
        raw-trajectories (vals (group-by :vehicle-journey-id data))]
        (into [] prepare-data raw-trajectories)))