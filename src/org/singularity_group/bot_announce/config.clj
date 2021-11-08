(ns org.singularity-group.bot-announce.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as walk]))

;; (def version :prod)
(def version :dev)

(defn
  pull-up
  [k m]
  (walk/postwalk
   (fn [x] (or (version x) x))
   m))

(def config
  (pull-up
   version
   (merge-with
    merge
    (edn/read-string
     (slurp (io/resource "tokens.edn"))))))
