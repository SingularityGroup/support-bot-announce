(ns org.singularity-group.bot-announce.config
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def config
  (merge-with
   merge
   (edn/read-string
    (slurp (io/resource "tokens.edn")))
   (edn/read-string
    (slurp (io/resource "config.edn")))))


;; (defn prod? [] (:prod config))

;; (defn prod-key [] (if (prod?) :prod :test))

;; (def m
;;   (merge-with
;;    merge
;;    (edn/read-string
;;     (slurp (io/resource "tokens.edn")))
;;    (edn/read-string
;;     (slurp (io/resource "config.edn")))))


;; (defn raise-key-recursively
;;   "Raise `key` to values nested in `m`.
;;   So that
;;   {:foo {:key val}}
;;   becomes {:foo val}"
;;   [m key]
;;   (when m
;;     (or
;;      (key m)
;;      (recur m key))))

;; (raise-key-recursively
;;  m
;;  :test)

;; (select-keys
;;  m
;;  [:discord]

;;  )
