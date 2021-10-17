(ns org.singularity-group.bot-announce.util
  (:require [clojure.string :as str]))

(defn truncate [s n]
  (subs s 0 (min n (count s))))

(defmacro if-let*
  ([bindings then]
   `(if-let* ~bindings ~then nil))
  ([bindings then else]
   (if (seq bindings)
     `(if-let [~(first bindings) ~(second bindings)]
        (if-let* ~(drop 2 bindings) ~then ~else)
        ~else)
     then)))

(defmacro when-let*
  [bindings then]
  `(if-let* ~bindings ~then nil))

(defn sorted-map-by-order [rows]
  (into (sorted-map-by
         (fn [key1 key2]
           (compare
            (get-in rows [key1 :order])
            (get-in rows [key2 :order]))))
        rows))

(defn count-words [s]
  (count (str/split s #" ")))
