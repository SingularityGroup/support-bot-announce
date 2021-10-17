(ns org.singularity-group.bot-announce.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def config
  (edn/read-string
   (slurp (io/resource "tokens.edn"))))
